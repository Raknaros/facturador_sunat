# Facturador SUNAT — API REST Multi-empresa

API REST multi-tenant para emisión de comprobantes electrónicos (Facturas, Boletas, Notas de Crédito/Débito, GRE) hacia SUNAT Perú. Validado en ambiente beta SUNAT con facturas reales.

## Stack Tecnológico

- **Java 21 + Spring Boot 3.3**
- **PostgreSQL** — base de datos central, schema `facturador`
- **H2** — base de datos en memoria para perfil `dev` (sin instalar nada)
- **Flyway** — migraciones versionadas de schema (automáticas al arrancar)
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
│   │   │   ├── AuthController.java              ← POST /auth/login — retorna JWT
│   │   │   ├── FacturaController.java           ← POST /api/{ruc}/facturas
│   │   │   ├── ContribuyenteController.java     ← POST /api/contribuyentes
│   │   │   ├── SerieController.java             ← PUT /api/{ruc}/series/{serie}/inicializar
│   │   │   └── GREController.java               ← POST /api/{ruc}/gre/remitente
│   │   └── dto/
│   │       ├── EmisionRequest.java              ← Incluye formaPago + cuotas
│   │       ├── EmisionResponse.java
│   │       ├── LoginRequest.java
│   │       ├── LoginResponse.java
│   │       └── RegistroContribuyenteRequest.java
│   │
│   ├── config/
│   │   ├── InternalDataSourceConfig.java        ← DataSource + EntityManagerFactory (schema facturador)
│   │   └── SecurityConfig.java                  ← JWT filter chain
│   │
│   ├── internal/                                ← Persistencia (schema facturador)
│   │   ├── entity/
│   │   │   ├── Contribuyente.java
│   │   │   ├── Comprobante.java
│   │   │   └── SerieCorrelativo.java
│   │   ├── repository/
│   │   └── service/
│   │       └── ComprobanteService.java          ← Locking pesimista + correlativos
│   │
│   ├── sunat/
│   │   ├── service/
│   │   │   ├── XmlBuilderService.java           ← UBL 2.1 + inyecciones DOM
│   │   │   ├── XmlSignerService.java            ← XMLDSig RSA-SHA256
│   │   │   ├── SunatSenderService.java          ← SOAP a SUNAT + parseo CDR
│   │   │   ├── CertificadoService.java
│   │   │   └── GREService.java                  ← GRE REST + OAuth 2.0 (WIP)
│   │   └── dto/
│   │       └── SunatResponse.java
│   │
│   ├── security/
│   │   ├── EncryptionService.java               ← AES-256-GCM
│   │   ├── JwtService.java                      ← Genera y valida tokens
│   │   ├── JwtAuthFilter.java                   ← Filtro Bearer + validación RUC en path
│   │   └── RateLimitService.java                ← 12 intentos/hora por IP
│   │
│   └── FacturadorApplication.java
│
├── src/main/resources/
│   ├── application.yml
│   └── db/
│       └── migration/
│           └── V1__init.sql                     ← Migración inicial (Flyway)
│
├── docker-compose.yml
├── Dockerfile
└── pom.xml
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
- Hibernate crea las tablas bajo el schema `facturador` en memoria con `ddl-auto: create-drop`

> **Nota dev:** Los datos (contribuyentes, comprobantes) se pierden al reiniciar. Registra el contribuyente nuevamente después de cada reinicio.

### Con PostgreSQL central (sin Docker)

```bash
DB_HOST=tu-servidor DB_PORT=5432 DB_NAME=tu_bd \
DB_USER=xxx DB_PASS=xxx \
JWT_SECRET=xxx ENCRYPTION_KEY=xxx \
mvn spring-boot:run
```

Flyway aplica `V1__init.sql` automáticamente al primer arranque — no es necesario ejecutar scripts manualmente.

| Variable | Descripción |
|----------|-------------|
| `DB_HOST` | Host del servidor PostgreSQL |
| `DB_PORT` | Puerto (default: 5432) |
| `DB_NAME` | Nombre de tu base de datos central |
| `DB_USER` | Usuario PostgreSQL |
| `DB_PASS` | Contraseña PostgreSQL |
| `JWT_SECRET` | Mínimo 32 caracteres para firma JWT |
| `ENCRYPTION_KEY` | Exactamente 32 caracteres para AES-256-GCM |

### Docker

```bash
# 1. Crear .env desde el ejemplo y completar los valores
cp .env.example .env

# 2. Levantar
docker-compose up -d
docker logs facturador_api
docker-compose down
```

La app apunta a tu BD PostgreSQL externa definida en el `.env`. Flyway crea el schema `facturador` y todas las tablas al primer arranque.

---

## Autenticación

Todos los endpoints de emisión requieren Bearer JWT. Flujo:

```
1. POST /api/contribuyentes    ← registrar empresa (público)
2. POST /auth/login            ← obtener token (público)
   { "ruc": "...", "usuarioSol": "...", "passwordSol": "..." }
3. GET/POST /api/{ruc}/...     ← requiere Authorization: Bearer <token>
```

El token tiene vigencia de 12 horas. Login incluye rate limiting (12 intentos/hora por IP) y validación opcional contra SUNAT.

---

## Endpoints API

| Método | Ruta | Descripción | Estado |
|--------|------|-------------|--------|
| `POST` | `/auth/login` | Login con credenciales SOL — retorna JWT | ✅ |
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

### 2. Login — obtener JWT

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "ruc": "20123456789",
    "usuarioSol": "MODDATOS",
    "passwordSol": "moddatos"
  }'
```

### 3. Emitir una Factura — Pago al Contado

```bash
curl -s -X POST http://localhost:8080/api/20123456789/facturas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "serie": "F001",
    "fechaEmision": "2026-04-16",
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

### 4. Emitir una Factura — Pago a Crédito con Cuotas

```bash
curl -s -X POST http://localhost:8080/api/20123456789/facturas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "serie": "F001",
    "fechaEmision": "2026-04-16",
    "moneda": "PEN",
    "formaPago": "Credito",
    "cuotas": [
      { "monto": 590.00, "fechaVencimiento": "2026-05-16" },
      { "monto": 590.00, "fechaVencimiento": "2026-06-16" }
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

### 5. Migración desde otro facturador — Inicializar correlativo

Si la empresa ya emitió comprobantes en otro sistema (ej. hasta F001-00000150):

```bash
curl -s -X PUT \
  -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/20123456789/series/F001/inicializar?tipo=FACTURA&ultimoNumero=150"
```

A partir de este punto, las facturas emitidas comenzarán desde `F001-00000151`.

---

## Migraciones de Schema (Flyway)

Flyway gestiona el schema automáticamente. Para agregar tablas o columnas en futuras versiones, crear un archivo nuevo en `src/main/resources/db/migration/`:

```
V1__init.sql          ← schema inicial (ya aplicado)
V2__add_boletas.sql   ← ejemplo de próxima migración
```

```sql
-- V2__add_boletas.sql (ejemplo)
ALTER TABLE facturador.comprobantes ADD COLUMN xml_firmado TEXT;
```

Al reiniciar la app, Flyway detecta y aplica solo las migraciones pendientes. El historial queda en `facturador.flyway_schema_history`.

---

## Arquitectura

### Schema `facturador` (BD central)

Todas las tablas viven bajo el schema `facturador` en tu BD PostgreSQL central:

| Tabla | Contenido |
|-------|-----------|
| `facturador.contribuyentes` | Empresa, credenciales SOL, certificado .p12, token GRE — todo cifrado con AES-256-GCM |
| `facturador.comprobantes` | Cabecera de cada comprobante emitido (estado, CDR, ticket) |
| `facturador.series_correlativos` | Contador de correlativos por (RUC, tipo, serie) con `PESSIMISTIC_WRITE` |
| `facturador.flyway_schema_history` | Historial de migraciones aplicadas (gestionado por Flyway) |

### Flujo de Emisión de Factura

```
POST /api/{ruc}/facturas
  → JwtAuthFilter                               # Valida Bearer token + RUC del path
  → FacturaController
  → ComprobanteService.siguienteCorrelativo()   # PESSIMISTIC_WRITE lock → "00000001"
  → XmlBuilderService.buildFacturaXml()         # xbuilder → XML UBL 2.1 + inyecciones DOM
  → XmlSignerService.firmar()                   # .p12 descifrado en RAM → XMLDSig RSA-SHA256
  → SunatSenderService.enviarFactura()          # ZIP + SOAP POST a SUNAT → CDR
  → ComprobanteService.registrar()              # Persiste cabecera + respuesta CDR
```

### Tenant Isolation

El RUC (11 dígitos) es la clave de tenant. Está embebido en cada URL (`/api/{ruc}/...`) y delimita credenciales, correlativos y comprobantes. El `JwtAuthFilter` verifica que el RUC del token coincida con el del path.

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

Cambiar a producción: `SUNAT_MODO=prod` en `.env` (o `sunat.modo: prod` en `application.yml`).

---

## Estado del Proyecto

| Componente | Estado |
|------------|--------|
| Registro de contribuyentes + encriptación AES-256-GCM | ✅ Completo |
| Autenticación JWT + rate limiting | ✅ Completo |
| Emisión de facturas (síncrona, CDR inmediato) | ✅ Completo |
| FormaPago Contado y Crédito con cuotas | ✅ Completo |
| Inicialización de correlativos para migración | ✅ Completo |
| Consulta de comprobantes | ✅ Completo |
| Series y correlativos con locking pesimista | ✅ Completo |
| Firma XMLDSig RSA-SHA256 | ✅ Completo |
| Envío SOAP a SUNAT + parseo CDR | ✅ Completo |
| Migraciones automáticas con Flyway | ✅ Completo |
| Boletas (controller + endpoint) | 🔄 Pendiente |
| Emisión en lote (`sendPack`) | 🔄 Pendiente |
| Polling de tickets | 🔄 Pendiente |
| GRE — builder XML UBL 2.1 | 🔄 Pendiente |
