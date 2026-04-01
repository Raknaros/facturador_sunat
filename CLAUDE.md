# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant REST API for electronic invoice (comprobante electrónico) emission to Peru's SUNAT tax authority. Handles multiple companies (tenants identified by RUC) for Facturas, Boletas, Notas de Crédito/Débito, and GRE (Guía de Remisión Electrónica).

## Commands

```bash
# Build
mvn clean package -DskipTests

# Run tests
mvn test

# Run locally en modo dev (H2 en memoria, no requiere PostgreSQL)
# Bash/Linux/Mac:
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
# PowerShell (Windows — entorno del usuario):
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# Run con PostgreSQL real (requiere variables de entorno)
DB_USER=xxx DB_PASS=xxx JWT_SECRET=xxx ENCRYPTION_KEY=xxx mvn spring-boot:run

# Docker (stack completo)
docker-compose up -d
docker logs facturador_api
docker-compose down
```

**Variables de entorno requeridas (producción/staging):**
- `DB_USER` / `DB_PASS` — credenciales de PostgreSQL (`db_facturador_api`)
- `JWT_SECRET` — mínimo 32 chars para JWT signing
- `ENCRYPTION_KEY` — exactamente 32 chars para AES-256-GCM

**Perfil `dev`:** usa H2 en memoria con `ddl-auto: create-drop`. No requiere ninguna variable de entorno (valores hardcodeados en el yml). No conecta a SUNAT real.

Swagger UI disponible en `http://localhost:8080/swagger-ui.html`.

## Architecture

### Single-Datasource (v2)

Una sola base de datos PostgreSQL (`db_facturador_api`) con todas las entidades bajo `internal/`:

| Tabla | Entidad | Contenido |
|-------|---------|-----------|
| `contribuyentes` | `Contribuyente` | Empresa + credenciales SOL + certificado .p12 + GRE OAuth |
| `comprobantes` | `Comprobante` | Cabecera de cada comprobante emitido |
| `series_correlativos` | `SerieCorrelativo` | Contador por (RUC, tipo, serie) |

DataSource configurado en `config/InternalDataSourceConfig.java` usando `DataSourceProperties` para el correcto mapeo `url → jdbcUrl` de HikariCP. El `docker-compose.yml` raíz es legado (dual-DB) — no refleja la arquitectura actual.

### Invoice Emission Flow

```
POST /api/{ruc}/facturas
  → FacturaController
  → ComprobanteService.siguienteCorrelativo()   # PESSIMISTIC_WRITE → "00000001"
  → XmlBuilderService.buildFacturaXml()         # Contribuyente de PostgreSQL → UBL 2.1 XML
  → XmlSignerService.firmar()                   # .p12 descifrado en RAM → XMLDSig RSA-SHA256
  → SunatSenderService.enviarFactura()          # ZIP + SOAP POST a SUNAT → parseo CDR
  → ComprobanteService.registrar()              # Persiste cabecera + estado CDR
```

### Tenant Isolation

- RUC (11-digit tax ID) is the tenant key, embedded in every URL path: `/api/{ruc}/...`
- Credential lookup, XML generation, signing, and persistence all scope by RUC

### Encryption

`security/EncryptionService.java` implements **AES-256-GCM** with a random 12-byte IV. Stored format: `Base64(IV + ciphertext)`. All passwords and certificates in PostgreSQL are encrypted at rest. Private keys are decrypted to RAM only during signing.

### Series & Correlativo

`SerieCorrelativo` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` to prevent duplicate correlativo assignment. SUNAT requires zero-padded 8-digit numbers (e.g., `00000001`). Counter is scoped per RUC + tipo_comprobante + serie.

### Synchronous vs Asynchronous

- **Facturas** (invoices): synchronous — CDR returned immediately via `sendBill`
- **Boletas en lote** (batch receipts): asynchronous — ticket returned, polled via `sendPack`
- **GRE**: asynchronous — requires OAuth 2.0 (client credentials), token cached up to 1 hour

### SUNAT Endpoints

Configured in `application.yml` with separate beta and production URLs:
- Facturas/Boletas: SOAP `billService`
- GRE: REST API with OAuth 2.0

## Database Schema

Schema SQL for PostgreSQL v2 (single DB):
- `src/main/resources/db/schema_internal.sql` → PostgreSQL (`db_facturador_api`)

`src/main/resources/db/schema.sql` is legacy (dual-DB, reference only).

## Key Dependencies

- **OpenUBL xbuilder 1.1.4.Final** (`io.github.project-openubl:xbuilder`) — UBL 2.1 XML generation
  - API: `DocumentManager.createXML(InvoiceInputModel, DefaultConfig).getXml()`
  - Package: `io.github.project.openubl.xmlbuilderlib.*`
  - Models: `InvoiceInputModel`, `ClienteInputModel`, `ProveedorInputModel`, `DireccionInputModel`, `DocumentLineInputModel`
  - **xsender is NOT used** — SOAP sending is implemented directly in `SunatSenderService` with `HttpURLConnection`
- **jjwt 0.12.5** — JWT authentication (implementation pending)
- **springdoc-openapi 2.5.0** — Swagger UI
- **Lombok + MapStruct** — boilerplate and DTO mapping

## Known Issues Resolved

- `xbuilder:1.2.0` does not exist in Maven Central → fixed to `1.1.4.Final`
- `xsender` dependency removed (was never used)
- `mysql-connector-j` dependency removed (v2 is PostgreSQL-only)
- `InternalDataSourceConfig` HikariCP URL binding fixed: replaced `DataSourceBuilder` + `@ConfigurationProperties` with `DataSourceProperties.initializeDataSourceBuilder()` pattern to correctly map `spring.datasource.url` → `jdbcUrl`
- `XmlBuilderService` rewritten for the real `1.1.4.Final` API (the original code used a non-existent API)

## Pending Work (Next Sessions)

- **JWT**: Implement `JwtService` + `JwtAuthFilter` for Bearer token authentication. Currently all endpoints are `permitAll`. `SecurityConfig` has the structure but the filter is missing.
- **Emisión en lote** (`sendPack`): `SunatSenderService.enviarLoteFacturas()` throws `UnsupportedOperationException`
- **Polling de tickets**: `SunatSenderService.consultarTicket()` throws `UnsupportedOperationException`
- **GRE XML builder**: `GREService.emitirGreRemitente()` has placeholder XML — needs real UBL 2.1 GRE builder
- **docker-compose.yml**: Update to v2 architecture (single PostgreSQL, remove MySQL service)
- **BoletaController**: No controller exists for `/api/{ruc}/boletas` — only facturas and GRE
