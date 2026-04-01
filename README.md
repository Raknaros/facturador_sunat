# Facturador SUNAT — API REST Multi-empresa

API REST multi-tenant para emisión de comprobantes electrónicos (Facturas, Boletas, Notas de Crédito/Débito, GRE) hacia SUNAT Perú.

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
│   │   │   └── GREController.java            ← POST /api/{ruc}/gre/remitente
│   │   └── dto/
│   │       ├── EmisionRequest.java
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
│   │       └── ComprobanteService.java       ← Locking pesimista + persistencia
│   │
│   ├── sunat/
│   │   ├── service/
│   │   │   ├── XmlBuilderService.java        ← Genera XML UBL 2.1 (OpenUBL)
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

## Primer Test — Flujo completo en modo dev

Levanta la API en modo dev y sigue estos pasos con `curl` (o desde Swagger UI).

### 1. Registrar un contribuyente

> En modo dev el certificado no se valida contra SUNAT, puedes usar cualquier Base64 válido.

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

**Respuesta esperada:**
```json
{
  "ruc": "20123456789",
  "razonSocial": "MI EMPRESA SAC",
  "mensaje": "Contribuyente registrado correctamente",
  "tieneGre": false
}
```

### 2. Emitir una Factura

```bash
curl -s -X POST http://localhost:8080/api/20123456789/facturas \
  -H "Content-Type: application/json" \
  -d '{
    "serie": "F001",
    "fechaEmision": "2026-03-27",
    "moneda": "PEN",
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

> `precioUnitario` se envía con IGV incluido (118.00 = 100 + 18%). El servicio calcula el valor neto internamente.

**Respuesta esperada (en modo beta/dev):**
```json
{
  "comprobanteId": 1,
  "serie": "F001",
  "correlativo": "00000001",
  "aceptado": true,
  "cdrCodigo": "0",
  "cdrDescripcion": "La Factura numero ...",
  "estado": "ACEPTADO"
}
```

### 3. Consultar el comprobante emitido

```bash
curl -s http://localhost:8080/api/20123456789/facturas/1
```

---

## Endpoints API

| Método | Ruta | Descripción | Estado |
|--------|------|-------------|--------|
| `POST` | `/api/contribuyentes` | Registrar empresa + credenciales | ✅ |
| `PUT` | `/api/contribuyentes/{ruc}/certificado` | Renovar certificado digital | ✅ |
| `PUT` | `/api/contribuyentes/{ruc}/gre-credenciales` | Configurar OAuth GRE | ✅ |
| `POST` | `/api/{ruc}/facturas` | Emitir factura (síncrono, CDR inmediato) | ✅ |
| `POST` | `/api/{ruc}/facturas/lote` | Emitir lote de facturas (asíncrono) | 🔄 WIP |
| `GET` | `/api/{ruc}/facturas/{id}` | Consultar factura por ID | ✅ |
| `GET` | `/api/{ruc}/facturas/lote/{ticket}` | Consultar estado de ticket | 🔄 WIP |
| `POST` | `/api/{ruc}/gre/remitente` | Emitir GRE Remitente (asíncrono) | 🔄 WIP |
| `GET` | `/api/{ruc}/gre/{id}/estado` | Consultar estado GRE | 🔄 WIP |

Swagger UI completo en `http://localhost:8080/swagger-ui.html`.

---

## Arquitectura

### Base de Datos Única (v2)

Toda la data vive en PostgreSQL (`db_facturador_api`):

| Tabla | Contenido |
|-------|-----------|
| `contribuyentes` | Empresa, credenciales SOL, certificado .p12, token GRE — todo cifrado |
| `comprobantes` | Cabecera de cada comprobante emitido (estado, CDR, ticket) |
| `series_correlativos` | Contador de correlativos por (RUC, tipo, serie) |

### Flujo de Emisión de Factura

```
POST /api/{ruc}/facturas
  → FacturaController
  → ComprobanteService.siguienteCorrelativo()   # PESSIMISTIC_WRITE lock → "00000001"
  → XmlBuilderService.buildFacturaXml()         # Contribuyente de PostgreSQL → XML UBL 2.1
  → XmlSignerService.firmar()                   # .p12 descifrado en RAM → XMLDSig RSA-SHA256
  → SunatSenderService.enviarFactura()          # ZIP + SOAP POST a SUNAT → CDR
  → ComprobanteService.registrar()              # Persiste cabecera + respuesta CDR
```

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

## Tests

```bash
mvn test
```

---

## Estado del Proyecto

| Componente | Estado |
|------------|--------|
| Registro de contribuyentes + encriptación | ✅ Completo |
| Emisión de facturas (síncrona) | ✅ Completo |
| Consulta de comprobantes | ✅ Completo |
| Series y correlativos con locking pesimista | ✅ Completo |
| Firma XMLDSig RSA-SHA256 | ✅ Completo |
| Envío SOAP a SUNAT + parseo CDR | ✅ Completo |
| JWT (autenticación) | 🔄 Pendiente implementación completa |
| Emisión en lote (`sendPack`) | 🔄 Pendiente |
| Polling de tickets | 🔄 Pendiente |
| GRE — builder XML UBL 2.1 | 🔄 Pendiente |
