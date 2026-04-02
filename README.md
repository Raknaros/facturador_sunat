# Facturador SUNAT — API REST Multi-empresa

API REST multi-tenant para emisión de comprobantes electrónicos (Facturas, Boletas, Notas de Crédito/Débito, GRE) hacia SUNAT Perú. Validado en ambiente beta SUNAT con facturas reales.

## Stack Tecnológico

- **Java 21 + Spring Boot 3.3**
- **PostgreSQL 15** — base de datos única (`db_facturador_api`)
- **H2** — base de datos en memoria para perfil `dev` (sin instalar nada)
- **OpenUBL xbuilder 1.1.4.Final** — generación XML UBL 2.1 (`DocumentManager` + `InvoiceInputModel`)
- **Spring Security + JWT (jjwt 0.12.5)**
- **AES-256-GCM** — encriptación de credenciales en reposo
- **springdoc-openapi 2.5.0** — Swagger UI
- **Lombok + MapStruct**
- **Docker + Docker Compose**

---

## Estructura del Proyecto

```
facturador-sunat/
├── src/main/java/com/tuempresa/facturador/
│   ├── api/
│   │   ├── controller/
│   │   │   ├── FacturaController.java        ← POST /api/{ruc}/facturas
│   │   │   ├── ContribuyenteController.java  ← POST /api/contribuyentes
│   │   │   ├── SerieController.java          ← PUT /api/{ruc}/series/{serie}/inicializar
│   │   │   └── GREController.java            ← POST /api/{ruc}/gre/remitente
│   │   └── dto/
│   │       ├── EmisionRequest.java           ← Incluye formaPago + cuotas
│   │       ├── EmisionResponse.java
│   │       └── RegistroContribuyenteRequest.java
│   │
│   ├── config/
│   │   ├── InternalDataSourceConfig.java     ← DataSource PostgreSQL único
│   │   └── SecurityConfig.java               ← JWT + rutas protegidas
│   │
│   ├── internal/                             ← Persistencia unificada (PostgreSQL)
│   │   ├── entity/
│   │   │   ├── Contribuyente.java            ← Empresa + credenciales SOL + certificado + GRE
│   │   │   ├── Comprobante.java              ← Cabecera del comprobante emitido
│   │   │   └── SerieCorrelativo.java         ← Contador por (RUC, tipo, serie)
│   │   ├── repository/
│   │   │   ├── ContribuyenteRepository.java
│   │   │   ├── ComprobanteRepository.java
│   │   │   └── SerieCorrelativoRepository.java
│   │   └── service/
│   │       └── ComprobanteService.java       ← Locking pesimista + persistencia + init correlativos
│   │
│   ├── sunat/
│   │   ├── service/
│   │   │   ├── XmlBuilderService.java        ← Genera XML UBL 2.1 + inyecciones DOM post-xbuilder
│   │   │   ├── XmlSignerService.java         ← Firma XMLDSig RSA-SHA256
│   │   │   ├── SunatSenderService.java       ← SOAP a SUNAT + parseo CDR
│   │   │   ├── CertificadoService.java       ← Carga .p12 en memoria (sin tocar disco)
│   │   │   └── GREService.java               ← GRE REST + OAuth 2.0 (WIP)
│   │   └── dto/
│   │       └── SunatResponse.java
│   │
│   ├── security/
│   │   └── EncryptionService.java            ← AES-256-GCM (IV aleatorio por cifrado)
│   │
│   └── FacturadorApplication.java
│
├── src/main/resources/
│   ├── application.yml                       ← Config general + perfil dev (H2)
│   └── db/
│       ├── schema_internal.sql               ← Schema PostgreSQL actual (v2)
│       └── schema.sql                        ← Schema legacy (referencia)
│
├── docker-compose.yml
├── pom.xml
└── CLAUDE.md
```

---

## Compilar

```bash
mvn clean package -DskipTests
```

---

## Ejecutar

### Modo desarrollo — H2 en memoria (recomendado para pruebas locales)

No requiere instalar PostgreSQL ni configurar variables de entorno.

```bash
# Bash / Linux / Mac
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# PowerShell (Windows)
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:facturador`)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- El esquema se crea automáticamente con `ddl-auto: create-drop`

> **Nota dev:** Los datos (contribuyentes, comprobantes) se pierden al reiniciar. Registra el contribuyente nuevamente después de cada reinicio.

### Con PostgreSQL real

Requiere una instancia PostgreSQL con la base de datos `db_facturador_api` creada y el schema de `src/main/resources/db/schema_internal.sql` aplicado.

```bash
DB_USER=xxx DB_PASS=xxx JWT_SECRET=xxx ENCRYPTION_KEY=xxx mvn spring-boot:run
```

| Variable | Descripción |
|----------|-------------|
| `DB_USER` | Usuario PostgreSQL |
| `DB_PASS` | Contraseña PostgreSQL |
| `JWT_SECRET` | Mínimo 32 caracteres para firma JWT |
| `ENCRYPTION_KEY` | Exactamente 32 caracteres para AES-256-GCM |

### Docker (stack completo)

> **Nota:** El `docker-compose.yml` actual usa la arquitectura legacy (MySQL + PostgreSQL).
> Para la arquitectura v2 (PostgreSQL único), aplica `schema_internal.sql` manualmente o actualiza el compose.

```bash
docker-compose up -d
docker logs facturador_api
docker-compose down
```

---

## Endpoints API

| Método | Ruta | Descripción | Estado |
|--------|------|-------------|--------|
| `POST` | `/api/contribuyentes` | Registrar empresa + credenciales | ✅ |
| `PUT` | `/api/contribuyentes/{ruc}/certificado` | Renovar certificado digital | ✅ |
| `PUT` | `/api/contribuyentes/{ruc}/gre-credenciales` | Configurar OAuth GRE | ✅ |
| `PUT` | `/api/{ruc}/series/{serie}/inicializar` | Inicializar correlativo (migración) | ✅ |
| `POST` | `/api/{ruc}/facturas` | Emitir factura (síncrono, CDR inmediato) | ✅ |
| `GET` | `/api/{ruc}/facturas/{id}` | Consultar factura por ID | ✅ |
| `POST` | `/api/{ruc}/facturas/lote` | Emitir lote de facturas (asíncrono) | 🔄 WIP |
| `GET` | `/api/{ruc}/facturas/lote/{ticket}` | Consultar estado de ticket | 🔄 WIP |
| `POST` | `/api/{ruc}/gre/remitente` | Emitir GRE Remitente (asíncrono) | 🔄 WIP |
| `GET` | `/api/{ruc}/gre/{id}/estado` | Consultar estado GRE | 🔄 WIP |

Swagger UI completo en `http://localhost:8080/swagger-ui.html`.

---

## Flujo Completo — Primer Test en Modo Dev

### 1. Registrar un contribuyente

```bash
curl -s -X POST http://localhost:8080/api/contribuyentes \
  -H "Content-Type: application/json" \
  -d '{
    "ruc": "20123456789",
    "razonSocial": "MI EMPRESA SAC",
    "nombreComercial": "Mi Empresa",
    "direccion": "Av. Test 123",
    "ubigeo": "150101",
    "departamento": "LIMA",
    "provincia": "LIMA",
    "distrito": "LIMA",
    "usuarioSol": "MODDATOS",
    "passwordSol": "moddatos",
    "certificadoP12Base64": "MIIB...",
    "certPassword": "123456",
    "certVence": "2026-12-31"
  }'
```

### 2. Emitir una Factura — Pago al Contado

```bash
curl -s -X POST http://localhost:8080/api/20123456789/facturas \
  -H "Content-Type: application/json" \
  -d '{
    "serie": "F001",
    "fechaEmision": "2026-04-02",
    "moneda": "PEN",
    "formaPago": "Contado",
    "receptor": {
      "tipoDocumento": "6",
      "nroDocumento": "20999888777",
      "razonSocial": "CLIENTE SA",
      "direccion": "Av. Cliente 456"
    },
    "items": [
      {
        "descripcion": "Servicio de consultoría",
        "cantidad": 1,
        "precioUnitario": 118.00,
        "tipoAfectacionIgv": "10"
      }
    ]
  }'
```

> `precioUnitario` se envía **con IGV incluido** (118.00 = 100.00 base + 18.00 IGV). El servicio calcula el desglose internamente.
> `formaPago` es opcional — si se omite, el sistema asume `"Contado"`.

### 3. Emitir una Factura — Pago a Crédito con Cuotas

```bash
curl -s -X POST http://localhost:8080/api/20123456789/facturas \
  -H "Content-Type: application/json" \
  -d '{
    "serie": "F001",
    "fechaEmision": "2026-04-02",
    "moneda": "PEN",
    "formaPago": "Credito",
    "cuotas": [
      { "monto": 590.00, "fechaVencimiento": "2026-05-02" },
      { "monto": 590.00, "fechaVencimiento": "2026-06-02" }
    ],
    "receptor": {
      "tipoDocumento": "6",
      "nroDocumento": "20999888777",
      "razonSocial": "CLIENTE SA"
    },
    "items": [
      {
        "descripcion": "Laptop HP",
        "cantidad": 1,
        "precioUnitario": 1180.00,
        "tipoAfectacionIgv": "10"
      }
    ]
  }'
```

**Respuesta esperada:**
```json
{
  "comprobanteId": 1,
  "serie": "F001",
  "correlativo": "00000001",
  "numeroCompleto": "F001-00000001",
  "aceptado": true,
  "cdrCodigo": "0",
  "cdrDescripcion": "La Factura numero F001-1, ha sido aceptada",
  "estado": "ACEPTADO"
}
```

### 4. Migración desde otro facturador — Inicializar correlativo

Si la empresa ya emitió comprobantes en otro sistema (ej. hasta F001-00000150), inicializa el contador antes de emitir:

```bash
curl -s -X PUT \
  "http://localhost:8080/api/20123456789/series/F001/inicializar?tipo=FACTURA&ultimoNumero=150"
```

```json
{
  "ruc": "20123456789",
  "tipo": "FACTURA",
  "serie": "F001",
  "ultimoNumero": 150,
  "proximoNumero": "00000151",
  "mensaje": "Correlativo inicializado. El próximo comprobante será F001-00000151"
}
```

A partir de este punto, las facturas emitidas comenzarán desde `F001-00000151`.

---

## Arquitectura

### Base de Datos Única (v2)

Toda la data vive en PostgreSQL (`db_facturador_api`):

| Tabla | Contenido |
|-------|-----------|
| `contribuyentes` | Empresa, credenciales SOL, certificado .p12, token GRE — todo cifrado con AES-256-GCM |
| `comprobantes` | Cabecera de cada comprobante emitido (estado, CDR, ticket) |
| `series_correlativos` | Contador de correlativos por (RUC, tipo, serie) con `@Version` optimistic + `PESSIMISTIC_WRITE` |

### Flujo de Emisión de Factura

```
POST /api/{ruc}/facturas
  → FacturaController
  → ComprobanteService.siguienteCorrelativo()   # PESSIMISTIC_WRITE lock → "00000001"
  → XmlBuilderService.buildFacturaXml()         # xbuilder → XML UBL 2.1 + inyecciones DOM
  → XmlSignerService.firmar()                   # .p12 descifrado en RAM → XMLDSig RSA-SHA256
  → SunatSenderService.enviarFactura()          # ZIP + SOAP POST a SUNAT → CDR
  → ComprobanteService.registrar()              # Persiste cabecera + respuesta CDR
```

### Inyecciones DOM post-xbuilder

xbuilder 1.1.4.Final no genera todos los elementos requeridos por SUNAT. `XmlBuilderService` los inyecta vía DOM después de la generación:

| Elemento inyectado | Por qué es necesario |
|--------------------|----------------------|
| `cbc:InvoiceTypeCode/@name` | xbuilder lo genera vacío |
| `cac:PaymentTerms` (FormaPago) | Obligatorio desde Resolución SUNAT 000193-2020 (abril 2021). Sin él: error 3244 |

### Tenant Isolation

El RUC (11 dígitos) es la clave de tenant. Está embebido en cada URL (`/api/{ruc}/...`) y delimita credenciales, correlativos y comprobantes.

### Encriptación

`EncryptionService` usa **AES-256-GCM** con IV aleatorio de 12 bytes. Formato almacenado: `Base64(IV + ciphertext)`. Las claves privadas se descifran a RAM solo durante la firma y no tocan disco.

### Sincrono vs Asíncrono

| Tipo | Método SUNAT | Respuesta |
|------|-------------|-----------|
| Facturas | SOAP `sendBill` | CDR inmediato |
| Boletas en lote | SOAP `sendPack` | Ticket para polling |
| GRE | REST + OAuth 2.0 | Ticket para polling |

### Endpoints SUNAT

Configurados en `application.yml`, modo `beta` por defecto:

| Tipo | Beta | Producción |
|------|------|-----------|
| Facturas/Boletas | `e-beta.sunat.gob.pe/.../billService` | `e-factura.sunat.gob.pe/.../billService` |
| GRE Auth | `gre-test.nubefact.com/v1/clientessol` | `api-seguridad.sunat.gob.pe/v1/clientessol` |
| GRE API | `gre-test.nubefact.com/v1` | `api.sunat.gob.pe/v1` |

Cambiar a producción: `sunat.modo: prod` en `application.yml`.

---

## Estado del Proyecto

| Componente | Estado |
|------------|--------|
| Registro de contribuyentes + encriptación AES-256-GCM | ✅ Completo |
| Emisión de facturas (síncrona, CDR inmediato) | ✅ Completo |
| FormaPago Contado y Crédito con cuotas | ✅ Completo |
| Inicialización de correlativos para migración | ✅ Completo |
| Consulta de comprobantes | ✅ Completo |
| Series y correlativos con locking pesimista | ✅ Completo |
| Firma XMLDSig RSA-SHA256 | ✅ Completo |
| Envío SOAP a SUNAT + parseo CDR | ✅ Completo |
| JWT (autenticación Bearer) | 🔄 Pendiente — estructura en `SecurityConfig`, falta `JwtAuthFilter` |
| Boletas (controller + endpoint) | 🔄 Pendiente |
| Emisión en lote (`sendPack`) | 🔄 Pendiente |
| Polling de tickets | 🔄 Pendiente |
| GRE — builder XML UBL 2.1 | 🔄 Pendiente |
