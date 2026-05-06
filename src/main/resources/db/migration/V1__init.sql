-- ╔══════════════════════════════════════════════════════════════════╗
-- ║  Migración inicial — schema facturador                          ║
-- ║  Aplicada automáticamente por Flyway al primer arranque         ║
-- ╚══════════════════════════════════════════════════════════════════╝

CREATE SCHEMA IF NOT EXISTS facturador;

CREATE TABLE IF NOT EXISTS facturador.contribuyentes (
    ruc                     VARCHAR(11)     PRIMARY KEY,

    -- Datos de la empresa (para generar XML del emisor)
    razon_social            VARCHAR(200)    NOT NULL,
    nombre_comercial        VARCHAR(200),
    direccion               VARCHAR(300)    NOT NULL,
    ubigeo                  VARCHAR(6),
    departamento            VARCHAR(50),
    provincia               VARCHAR(50),
    distrito                VARCHAR(50),

    -- Credenciales SOL (para facturas/boletas vía SOAP)
    usuario_sol             VARCHAR(50)     NOT NULL,
    password_sol_enc        TEXT            NOT NULL,

    -- Certificado digital .p12 (para firma XMLDSig)
    certificado_p12_enc     TEXT            NOT NULL,
    cert_password_enc       TEXT            NOT NULL,
    cert_vence              DATE            NOT NULL,

    -- Credenciales API REST SUNAT (SOLO para GRE — opcionales)
    gre_client_id           VARCHAR(100),
    gre_client_secret_enc   TEXT,

    -- Token OAuth GRE en caché
    gre_token               TEXT,
    gre_token_expira        TIMESTAMP,

    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

CREATE TABLE IF NOT EXISTS facturador.comprobantes (
    id                  BIGSERIAL       PRIMARY KEY,
    ruc_emisor          VARCHAR(11)     NOT NULL,
    tipo                VARCHAR(20)     NOT NULL,
    serie               VARCHAR(4)      NOT NULL,
    correlativo         VARCHAR(8)      NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    ticket_sunat        VARCHAR(50),
    cdr_codigo          VARCHAR(10),
    cdr_descripcion     VARCHAR(500),
    hash_xml            VARCHAR(100),
    fecha_emision       DATE            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,

    UNIQUE (ruc_emisor, serie, correlativo)
);

CREATE TABLE IF NOT EXISTS facturador.series_correlativos (
    id                  BIGSERIAL       PRIMARY KEY,
    ruc_emisor          VARCHAR(11)     NOT NULL,
    tipo                VARCHAR(20)     NOT NULL,
    serie               VARCHAR(4)      NOT NULL,
    ultimo_numero       INTEGER         NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,

    UNIQUE (ruc_emisor, tipo, serie)
);

CREATE INDEX IF NOT EXISTS idx_comp_ruc    ON facturador.comprobantes (ruc_emisor);
CREATE INDEX IF NOT EXISTS idx_comp_estado ON facturador.comprobantes (estado);
CREATE INDEX IF NOT EXISTS idx_comp_ticket ON facturador.comprobantes (ticket_sunat) WHERE ticket_sunat IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cert_vence  ON facturador.contribuyentes (cert_vence);
