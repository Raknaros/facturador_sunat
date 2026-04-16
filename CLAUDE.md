# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant REST API for electronic invoice (comprobante electrónico) emission to Peru's SUNAT tax authority. Handles multiple companies (tenants identified by RUC) for Facturas, Boletas, Notas de Crédito/Débito, and GRE (Guía de Remisión Electrónica).

**Status:** First factura successfully accepted by SUNAT beta (CDR código 0). Core emission flow is working end-to-end. JWT auth + rate limiting implemented and wired.

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

# Run con PostgreSQL central (requiere variables de entorno)
DB_HOST=xxx DB_PORT=5432 DB_NAME=xxx DB_USER=xxx DB_PASS=xxx JWT_SECRET=xxx ENCRYPTION_KEY=xxx mvn spring-boot:run

# Docker (apunta a BD PostgreSQL externa definida en .env)
docker-compose up -d
docker logs facturador_api
docker-compose down
```

**Variables de entorno requeridas (producción/staging):**
- `DB_HOST` / `DB_PORT` / `DB_NAME` — servidor y base de datos PostgreSQL central
- `DB_USER` / `DB_PASS` — credenciales PostgreSQL
- `JWT_SECRET` — mínimo 32 chars para JWT signing
- `ENCRYPTION_KEY` — exactamente 32 chars para AES-256-GCM

**Perfil `dev`:** usa H2 en memoria. Hibernate crea el schema `facturador` y tablas con `ddl-auto: create-drop`. No requiere ninguna variable de entorno. No conecta a SUNAT real.
**IMPORTANTE dev:** Los datos (contribuyentes, comprobantes) se pierden al reiniciar. Siempre registrar el contribuyente antes de emitir en cada sesión.

Swagger UI disponible en `http://localhost:8080/swagger-ui.html`.

## Architecture

### Single-Schema (v2)

Una sola base de datos PostgreSQL central con todas las entidades bajo el schema `facturador`:

| Tabla | Entidad | Contenido |
|-------|---------|-----------|
| `facturador.contribuyentes` | `Contribuyente` | Empresa + credenciales SOL + certificado .p12 + GRE OAuth |
| `facturador.comprobantes` | `Comprobante` | Cabecera de cada comprobante emitido |
| `facturador.series_correlativos` | `SerieCorrelativo` | Contador por (RUC, tipo, serie) |

DataSource configurado en `config/InternalDataSourceConfig.java` usando `DataSourceProperties` para el correcto mapeo `url → jdbcUrl` de HikariCP. La propiedad `hibernate.default_schema=facturador` está seteada en `InternalDataSourceConfig.entityManagerFactory()`.

### Flyway — Schema Migrations

Flyway gestiona el schema automáticamente al arrancar. Configurado para el schema `facturador`. Migraciones en `src/main/resources/db/migration/`:

- `V1__init.sql` — schema inicial (tablas contribuyentes, comprobantes, series_correlativos)
- Futuras: `V2__...sql`, `V3__...sql`, etc.

En dev (H2), Flyway está desactivado — Hibernate usa `ddl-auto: create-drop`.

### Invoice Emission Flow

```
POST /api/{ruc}/facturas
  → JwtAuthFilter                               # Valida Bearer token + RUC del path
  → FacturaController
  → ComprobanteService.siguienteCorrelativo()   # PESSIMISTIC_WRITE → "00000001"
  → XmlBuilderService.buildFacturaXml()         # Contribuyente de PostgreSQL → UBL 2.1 XML
      → DocumentManager.createXML()             # xbuilder genera XML base
      → inyectarTipoOperacion()                 # DOM: añade listID/name a InvoiceTypeCode
      → inyectarFormaPago()                     # DOM: añade cac:PaymentTerms (Contado/Credito+cuotas)
  → XmlSignerService.firmar()                   # .p12 descifrado en RAM → XMLDSig RSA-SHA256
  → SunatSenderService.enviarFactura()          # ZIP + SOAP POST a SUNAT → parseo CDR
  → ComprobanteService.registrar()              # Persiste cabecera + estado CDR
```

### Authentication Flow

```
POST /auth/login
  → RateLimitService.permitir(ip)               # 12 intentos/hora por IP (ventana deslizante)
  → ContribuyenteRepository.findByRucAndActivoTrue()
  → EncryptionService.decryptText(passwordSolEnc) + comparación local
  → SunatSenderService.validarCredencialesSOL() # Ping SUNAT (si validar-creds-en-login=true)
  → JwtService.generarToken(ruc, usuarioSol)    # JWT HS256, 12h de vigencia
```

El `JwtAuthFilter` intercepta todas las rutas protegidas, valida el token y verifica que el RUC del claim `sub` coincida con el RUC embebido en el path `/api/{ruc}/...`.

### Tenant Isolation

- RUC (11-digit tax ID) is the tenant key, embedded in every URL path: `/api/{ruc}/...`
- Credential lookup, XML generation, signing, and persistence all scope by RUC

### Encryption

`security/EncryptionService.java` implements **AES-256-GCM** with a random 12-byte IV. Stored format: `Base64(IV + ciphertext)`. All passwords and certificates in PostgreSQL are encrypted at rest. Private keys are decrypted to RAM only during signing.

### Series & Correlativo

`SerieCorrelativo` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` to prevent duplicate correlativo assignment. SUNAT requires zero-padded 8-digit numbers (e.g., `00000001`). Counter is scoped per RUC + tipo_comprobante + serie.

**Migration:** `PUT /api/{ruc}/series/{serie}/inicializar?tipo=FACTURA&ultimoNumero=150` sets the counter for companies migrating from another invoicing system. Next emission will be 151.

### XMLDSig Signing — Critical Notes

- `ds:Signature` MUST go inside `ext:ExtensionContent`, NOT at document root → `DOMSignContext(privateKey, extensionContent)`
- `setDefaultNamespacePrefix("ds")` on DOMSignContext
- `Id="PROJECT-OPENUBL-SIGN"` must be set on ds:Signature AFTER signing (matches xbuilder's `cbc:URI`)
- CanonicalizationMethod: `INCLUSIVE` (not EXCLUSIVE)
- Reference: URI="" with only ENVELOPED transform (no second C14N transform)
- Parse XML with `InputSource(StringReader)` — avoids encoding conflict (xbuilder declares ISO-8859-1)
- Serialize with `ByteArrayOutputStream` + `OutputKeys.ENCODING=UTF-8`

### xbuilder 1.1.4.Final — DOM Post-Processing Required

xbuilder does NOT generate all elements required by SUNAT. These are injected via DOM after `DocumentManager.createXML()`:

| Method | What it injects | Why needed |
|--------|----------------|------------|
| `inyectarTipoOperacion("0101")` | `cbc:InvoiceTypeCode/@name="VENTA INTERNA"` | xbuilder generates empty name |
| `inyectarFormaPago(formaPago, moneda, cuotas)` | `cac:PaymentTerms` block(s) | **Required since Resolución 000193-2020 (April 2021). Without it: SUNAT error 3244.** |

**FormaPago — Contado:**
```xml
<cac:PaymentTerms>
  <cbc:ID>FormaPago</cbc:ID>
  <cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>
</cac:PaymentTerms>
```

**FormaPago — Crédito** (one header block + one block per installment):
```xml
<cac:PaymentTerms>
  <cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Credito</cbc:PaymentMeansID>
  <cbc:Amount currencyID="PEN">{totalPayable}</cbc:Amount>
</cac:PaymentTerms>
<cac:PaymentTerms>
  <cbc:ID>Cuota001</cbc:ID><cbc:PaymentMeansID>Cuota</cbc:PaymentMeansID>
  <cbc:Amount currencyID="PEN">590.00</cbc:Amount>
  <cbc:PaymentDueDate>2026-05-02</cbc:PaymentDueDate>
</cac:PaymentTerms>
```

### CDR ZIP Format

SUNAT's CDR ZIP contains a `dummy/` directory entry BEFORE the actual XML. `SunatSenderService.descomprimirZip()` iterates entries until finding the first non-directory entry with content. Do NOT call `getNextEntry()` just once.

### Synchronous vs Asynchronous

- **Facturas** (invoices): synchronous — CDR returned immediately via `sendBill`
- **Boletas en lote** (batch receipts): asynchronous — ticket returned, polled via `sendPack`
- **GRE**: asynchronous — requires OAuth 2.0 (client credentials), token cached up to 1 hour

### SUNAT Endpoints

Configured in `application.yml` with separate beta and production URLs:
- Facturas/Boletas: SOAP `billService`
- GRE: REST API with OAuth 2.0

## Database Schema

Migraciones en `src/main/resources/db/migration/`. Flyway las aplica automáticamente al arrancar contra el schema `facturador` de la BD central.

Para agregar tablas o columnas: crear `V{n}__descripcion.sql` con las sentencias DDL prefijadas con `facturador.`.

## Key Dependencies

- **OpenUBL xbuilder 1.1.4.Final** (`io.github.project-openubl:xbuilder`) — UBL 2.1 XML generation
  - API: `DocumentManager.createXML(InvoiceInputModel, DefaultConfig).getXml()`
  - Package: `io.github.project.openubl.xmlbuilderlib.*`
  - Models: `InvoiceInputModel`, `ClienteInputModel`, `ProveedorInputModel`, `DireccionInputModel`, `DocumentLineInputModel`, `FirmanteInputModel`
  - `FirmanteInputModel` MUST be set on invoice — without it SUNAT rejects with empty `cac:Signature`
  - **xsender is NOT used** — SOAP sending is implemented directly in `SunatSenderService` with `HttpURLConnection`
  - **javax.validation compatibility:** xbuilder 1.1.4.Final uses Java EE; Spring Boot 3 uses jakarta. Fix: add `javax.validation:validation-api:2.0.1.Final` + `org.apache.bval:bval-jsr:2.0.6` to pom.xml
- **Flyway** — versionado automático de migraciones de schema
- **jjwt 0.12.5** — JWT authentication (`Jwts.parser().verifyWith(...)`)
- **springdoc-openapi 2.5.0** — Swagger UI
- **Lombok + MapStruct** — boilerplate and DTO mapping

## SUNAT Error Reference (resolved)

| Code | Description | Root cause & fix |
|------|-------------|-----------------|
| 2334 | Incorrect reference digest value | `ds:Signature` was at document root instead of inside `ext:ExtensionContent` |
| 1036 | Filename mismatch | ZIP filename must use integer correlativo (`F001-1`), not zero-padded (`F001-00000001`) |
| 3030 | Código de local | `dir.setCodigoLocal("0000")` and `dir.setUrbanizacion("NONE")` required in `buildProveedor()` |
| 3244 | Tipo de transacción | **NOT about `listID`** — missing `cac:PaymentTerms` (Resolución 000193-2020). `inyectarFormaPago()` fixes this |
| CDR parse error | Premature end of file | CDR ZIP has `dummy/` entry first; `descomprimirZip()` must skip directory entries |

## Pending Work (Next Sessions)

- **BoletaController**: No controller exists for `/api/{ruc}/boletas`. Should mirror `FacturaController` but route to `buildBoletaXml()` and use series B001.
- **Emisión en lote** (`sendPack`): `SunatSenderService.enviarLoteFacturas()` throws `UnsupportedOperationException`. Boletas use async `sendPack` → returns ticket.
- **Polling de tickets**: `SunatSenderService.consultarTicket()` throws `UnsupportedOperationException`. Needed for boletas en lote and GRE.
- **GRE XML builder**: `GREService.emitirGreRemitente()` has placeholder XML — needs real UBL 2.1 GRE (Guía de Remisión Electrónica) builder.
