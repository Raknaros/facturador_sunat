-- ╔══════════════════════════════════════════════════════════════╗
-- ║  SCRIPT MYSQL — Base de datos: db_facturacion               ║
-- ╚══════════════════════════════════════════════════════════════╝

CREATE DATABASE IF NOT EXISTS db_facturacion
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE db_facturacion;

-- ─────────────────────────────────────────────
-- Comprobantes (cabecera)
-- ─────────────────────────────────────────────
CREATE TABLE comprobantes (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_ruc         VARCHAR(11)     NOT NULL,
    tipo_comprobante    ENUM('FACTURA','BOLETA','NOTA_CREDITO','NOTA_DEBITO',
                             'GUIA_REMISION','RESUMEN_DIARIO','COMUNICACION_BAJA') NOT NULL,
    serie               VARCHAR(4)      NOT NULL,
    correlativo         VARCHAR(8)      NOT NULL,
    fecha_emision       DATE            NOT NULL,

    -- Receptor
    cliente_tipo_doc    CHAR(1),               -- 6=RUC, 1=DNI, 4=Carnet Extranjería
    cliente_nro_doc     VARCHAR(15),
    cliente_razon_social VARCHAR(200),
    cliente_direccion   VARCHAR(300),

    -- Totales
    moneda              CHAR(3)         NOT NULL DEFAULT 'PEN',
    tipo_cambio         DECIMAL(8,3),
    total_gravado       DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    total_exonerado     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    total_inafecto      DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    total_igv           DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    total_descuentos    DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    importe_total       DECIMAL(12,2)   NOT NULL,

    -- XML y SUNAT
    xml_firmado         LONGTEXT,
    hash_xml            VARCHAR(100),
    estado              ENUM('PENDIENTE','ENVIADO','ACEPTADO','RECHAZADO',
                             'BAJA_SOLICITADA','DADO_DE_BAJA','ERROR') NOT NULL DEFAULT 'PENDIENTE',
    fecha_envio_sunat   DATETIME,
    numero_ticket_sunat VARCHAR(50),
    observaciones       TEXT,

    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        ON UPDATE CURRENT_TIMESTAMP,
    version             BIGINT          NOT NULL DEFAULT 0,

    UNIQUE KEY uq_comprobante (empresa_ruc, serie, correlativo),
    INDEX idx_empresa_ruc   (empresa_ruc),
    INDEX idx_estado        (estado),
    INDEX idx_fecha_emision (fecha_emision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────
-- Detalle de comprobantes
-- ─────────────────────────────────────────────
CREATE TABLE detalle_comprobantes (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    comprobante_id      BIGINT          NOT NULL,
    numero_orden        INT             NOT NULL,
    codigo_producto     VARCHAR(50),
    codigo_sunat        VARCHAR(20),
    descripcion         VARCHAR(500)    NOT NULL,
    unidad_medida       VARCHAR(10)     NOT NULL DEFAULT 'NIU',
    cantidad            DECIMAL(12,4)   NOT NULL,
    precio_unitario     DECIMAL(12,4)   NOT NULL,
    valor_unitario      DECIMAL(12,4)   NOT NULL,
    valor_venta         DECIMAL(12,2)   NOT NULL,
    igv                 DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    precio_total        DECIMAL(12,2)   NOT NULL,
    tipo_afectacion_igv VARCHAR(2)      NOT NULL DEFAULT '10',
    descuento           DECIMAL(12,2)   NOT NULL DEFAULT 0.00,

    FOREIGN KEY (comprobante_id) REFERENCES comprobantes(id) ON DELETE CASCADE,
    INDEX idx_comprobante_id (comprobante_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────
-- Series y correlativos por empresa
-- ─────────────────────────────────────────────
CREATE TABLE series_correlativos (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_ruc         VARCHAR(11)     NOT NULL,
    tipo_comprobante    VARCHAR(20)     NOT NULL,
    serie               VARCHAR(4)      NOT NULL,
    ultimo_correlativo  INT             NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,

    UNIQUE KEY uq_serie (empresa_ruc, tipo_comprobante, serie)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────
-- CDR (respuesta de SUNAT)
-- ─────────────────────────────────────────────
CREATE TABLE cdr_respuestas (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    comprobante_id              BIGINT          NOT NULL UNIQUE,
    codigo_respuesta            VARCHAR(10),
    descripcion_respuesta       VARCHAR(500),
    xml_cdr                     LONGTEXT,
    aceptado                    BOOLEAN,
    fecha_recepcion_sunat       DATETIME,
    created_at                  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (comprobante_id) REFERENCES comprobantes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



-- ╔══════════════════════════════════════════════════════════════╗
-- ║  SCRIPT POSTGRESQL — Base de datos: db_contabilidad         ║
-- ╚══════════════════════════════════════════════════════════════╝

-- ─────────────────────────────────────────────
-- Empresas emisoras del servicio
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS empresas (
    ruc                 CHAR(11)        PRIMARY KEY,
    razon_social        VARCHAR(200)    NOT NULL,
    nombre_comercial    VARCHAR(200),
    direccion           VARCHAR(300),
    ubigeo              CHAR(6),
    departamento        VARCHAR(100),
    provincia           VARCHAR(100),
    distrito            VARCHAR(100),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

-- ─────────────────────────────────────────────
-- Credenciales SOL por empresa (encriptadas)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS credenciales_sol (
    id                      BIGSERIAL       PRIMARY KEY,
    empresa_ruc             CHAR(11)        NOT NULL REFERENCES empresas(ruc),
    usuario_sol             VARCHAR(50)     NOT NULL,
    password_sol_encrypted  TEXT            NOT NULL,  -- AES-256
    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),

    UNIQUE (empresa_ruc, activo)   -- Solo 1 credencial activa por empresa
);

-- ─────────────────────────────────────────────
-- Certificados digitales por empresa (encriptados)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS certificados_digitales (
    id                          BIGSERIAL   PRIMARY KEY,
    empresa_ruc                 CHAR(11)    NOT NULL REFERENCES empresas(ruc),
    certificado_encrypted       TEXT        NOT NULL,   -- .p12/.pfx encriptado en AES-256
    password_cert_encrypted     TEXT        NOT NULL,   -- Contraseña del cert encriptada
    fecha_vencimiento           DATE        NOT NULL,
    activo                      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP   NOT NULL DEFAULT NOW(),

    UNIQUE (empresa_ruc, activo)   -- Solo 1 certificado activo por empresa
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_cred_empresa   ON credenciales_sol (empresa_ruc);
CREATE INDEX IF NOT EXISTS idx_cert_empresa   ON certificados_digitales (empresa_ruc);
CREATE INDEX IF NOT EXISTS idx_cert_vence     ON certificados_digitales (fecha_vencimiento);
