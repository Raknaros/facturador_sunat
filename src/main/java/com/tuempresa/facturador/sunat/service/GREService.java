package com.tuempresa.facturador.sunat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.facturador.internal.entity.Comprobante;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ComprobanteRepository;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import com.tuempresa.facturador.security.EncryptionService;
import com.tuempresa.facturador.api.dto.EmisionResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de emisión de Guías de Remisión Electrónica (GRE Remitente).
 *
 * Archivo: src/main/java/com/tuempresa/facturador/sunat/service/GREService.java
 *
 * Diferencias clave vs Facturas:
 *  - Usa API REST de SUNAT (no SOAP)
 *  - Requiere OAuth 2.0: client_id + client_secret + creds SOL → Bearer token (1h)
 *  - El token se cachea en la tabla contribuyentes para reutilizarse
 *  - La respuesta es un TICKET (asíncrono) — no CDR inmediato
 *  - Serie obligatoriamente empieza con "T" (ej: T001)
 *  - El ZIP envía: nombre del archivo + Base64 + hash SHA-256 del ZIP
 *
 * ESTADO: Esqueleto listo para implementar.
 *         Los métodos buildGreXml() y los DTOs de GRE están marcados con TODO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GREService {

    private final ContribuyenteRepository contribuyenteRepo;
    private final ComprobanteRepository   comprobanteRepo;
    private final EncryptionService       encryptionService;
    private final XmlBuilderService       xmlBuilderService;
    private final XmlSignerService        xmlSignerService;
    private final ObjectMapper            objectMapper;

    @Value("${sunat.modo}")
    private String modo;

    @Value("${sunat.endpoints.gre-auth-beta}")
    private String greAuthBeta;

    @Value("${sunat.endpoints.gre-auth-prod}")
    private String greAuthProd;

    @Value("${sunat.endpoints.gre-api-beta}")
    private String greApiBeta;

    @Value("${sunat.endpoints.gre-api-prod}")
    private String greApiProd;

    // ─────────────────────────────────────────────────────────
    // PUNTO DE ENTRADA PRINCIPAL
    // ─────────────────────────────────────────────────────────

    /**
     * Emite una GRE Remitente.
     *
     * @param rucEmisor RUC del remitente
     * @param request   Datos de la guía (TODO: definir GreRequest DTO)
     * @return EmisionResponse con ticketSunat para consulta posterior
     */
    @Transactional
    public EmisionResponse emitirGreRemitente(String rucEmisor, Object request) {
        log.info("Iniciando emisión GRE Remitente para RUC: {}", rucEmisor);

        // 1. Cargar contribuyente y verificar que tiene credenciales GRE
        Contribuyente contribuyente = contribuyenteRepo
            .findByRucAndActivoTrue(rucEmisor)
            .orElseThrow(() -> new RuntimeException("Contribuyente no encontrado: " + rucEmisor));

        if (!contribuyente.tieneCredsGre()) {
            throw new RuntimeException(
                "El RUC " + rucEmisor + " no tiene credenciales GRE configuradas. " +
                "Registre client_id y client_secret desde el menú SOL de SUNAT.");
        }

        try {
            // 2. Obtener (o renovar) token OAuth de SUNAT
            String bearerToken = obtenerTokenGre(contribuyente);

            // 3. Generar XML GRE UBL 2.1
            // TODO: implementar xmlBuilderService.buildGreXml(rucEmisor, request)
            String xmlSinFirma = buildGreXmlPlaceholder(rucEmisor, request);

            // 4. Firmar XML con certificado .p12 del emisor
            String xmlFirmado = xmlSignerService.firmar(rucEmisor, xmlSinFirma);

            // 5. Determinar nombre del archivo y correlativo
            // Formato SUNAT: RUC-09-T001-CORRELATIVO
            // "09" = código de tipo GRE Remitente
            String serie       = extraerSerie(request);        // TODO: desde DTO
            String correlativo = extraerCorrelativo(request);  // TODO: desde DTO
            String nombreBase  = rucEmisor + "-09-" + serie + "-" + correlativo;

            // 6. Comprimir en ZIP y calcular hash SHA-256
            byte[] xmlBytes  = xmlFirmado.getBytes(StandardCharsets.UTF_8);
            byte[] zipBytes  = comprimirEnZip(nombreBase + ".xml", xmlBytes);
            String zipBase64 = Base64.getEncoder().encodeToString(zipBytes);
            String hashZip   = sha256Hex(zipBytes);

            // 7. Enviar a API REST de SUNAT
            String ticket = enviarGreRest(
                rucEmisor, bearerToken,
                nombreBase + ".zip", zipBase64, hashZip);

            log.info("GRE enviada. Ticket SUNAT: {}", ticket);

            // 8. Persistir comprobante con estado EN_PROCESO
            Comprobante comp = Comprobante.builder()
                .rucEmisor(rucEmisor)
                .tipo(Comprobante.TipoComprobante.GRE_REMITENTE)
                .serie(serie)
                .correlativo(correlativo)
                .estado(Comprobante.EstadoComprobante.EN_PROCESO)
                .ticketSunat(ticket)
                .hashXml(sha256Hex(xmlFirmado.getBytes(StandardCharsets.UTF_8)))
                .fechaEmision(LocalDate.now()) // TODO: desde DTO
                .build();

            comprobanteRepo.save(comp);

            return EmisionResponse.builder()
                .comprobanteId(comp.getId())
                .rucEmisor(rucEmisor)
                .tipo("GRE_REMITENTE")
                .serie(serie)
                .correlativo(correlativo)
                .numeroCompleto(serie + "-" + correlativo)
                .estado("EN_PROCESO")
                .ticketSunat(ticket)
                .aceptado(false) // Se confirmará al consultar el ticket
                .build();

        } catch (Exception e) {
            log.error("Error emitiendo GRE para RUC {}: {}", rucEmisor, e.getMessage(), e);
            return EmisionResponse.builder()
                .rucEmisor(rucEmisor)
                .tipo("GRE_REMITENTE")
                .estado("ERROR")
                .aceptado(false)
                .mensajeError(e.getMessage())
                .build();
        }
    }

    // ─────────────────────────────────────────────────────────
    // CONSULTAR ESTADO DE TICKET GRE
    // ─────────────────────────────────────────────────────────

    /**
     * Consulta el estado de una GRE enviada previamente usando su ticket.
     * Se llama desde un job periódico o desde el endpoint GET /gre/{id}/estado
     */
    public EmisionResponse consultarTicket(String rucEmisor, String ticketSunat) {
        log.debug("Consultando ticket GRE: {} para RUC: {}", ticketSunat, rucEmisor);

        Contribuyente contribuyente = contribuyenteRepo
            .findByRucAndActivoTrue(rucEmisor)
            .orElseThrow(() -> new RuntimeException("Contribuyente no encontrado: " + rucEmisor));

        try {
            String bearerToken = obtenerTokenGre(contribuyente);
            String url = getGreApiBase() + "/contribuyente/gre/comprobantes/" + ticketSunat;

            // GET al endpoint de consulta de SUNAT
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            conn.setConnectTimeout(15_000);

            byte[] respBytes   = conn.getInputStream().readAllBytes();
            JsonNode respJson  = objectMapper.readTree(respBytes);

            String codigoResp  = respJson.path("codigoRespuesta").asText();
            String descripcion = respJson.path("descripcion").asText();
            boolean aceptado   = "0".equals(codigoResp);

            // Actualizar en DB
            comprobanteRepo.actualizarEstadoPorTicket(
                ticketSunat,
                aceptado ? Comprobante.EstadoComprobante.ACEPTADO
                         : Comprobante.EstadoComprobante.RECHAZADO,
                codigoResp,
                descripcion);

            return EmisionResponse.builder()
                .ticketSunat(ticketSunat)
                .estado(aceptado ? "ACEPTADO" : "RECHAZADO")
                .cdrCodigo(codigoResp)
                .cdrDescripcion(descripcion)
                .aceptado(aceptado)
                .build();

        } catch (Exception e) {
            log.error("Error consultando ticket GRE {}: {}", ticketSunat, e.getMessage());
            return EmisionResponse.builder()
                .ticketSunat(ticketSunat)
                .estado("ERROR")
                .mensajeError(e.getMessage())
                .build();
        }
    }

    // ─────────────────────────────────────────────────────────
    // OAUTH — Obtener / renovar token GRE
    // ─────────────────────────────────────────────────────────

    /**
     * Retorna el token OAuth vigente, o solicita uno nuevo si expiró.
     * El token se cachea en la tabla contribuyentes (columna gre_token).
     */
    private String obtenerTokenGre(Contribuyente c) throws Exception {
        if (c.tokenGreVigente()) {
            log.debug("Reutilizando token GRE en caché para RUC: {}", c.getRuc());
            return c.getGreToken();
        }

        log.info("Solicitando nuevo token GRE a SUNAT para RUC: {}", c.getRuc());

        String clientId     = c.getGreClientId();
        String clientSecret = encryptionService.decryptText(c.getGreClientSecretEnc());
        String usuarioSol   = c.getUsuarioSol();
        String passwordSol  = encryptionService.decryptText(c.getPasswordSolEnc());

        // POST a SUNAT OAuth endpoint
        String authUrl = getGreAuthBase() + "/" + clientId + "/oauth2/token/";
        String body    = "grant_type=password"
            + "&scope=https://api-cpe.sunat.gob.pe"
            + "&client_id="     + clientId
            + "&client_secret=" + urlEncode(clientSecret)
            + "&username="      + urlEncode(usuarioSol)
            + "&password="      + urlEncode(passwordSol);

        HttpURLConnection conn = (HttpURLConnection) new URL(authUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(15_000);

        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        byte[]   respBytes = conn.getInputStream().readAllBytes();
        JsonNode respJson  = objectMapper.readTree(respBytes);

        String  token    = respJson.path("access_token").asText();
        long    expiresIn = respJson.path("expires_in").asLong(3600);

        // Cachear token en DB
        LocalDateTime expira = LocalDateTime.now().plusSeconds(expiresIn);
        contribuyenteRepo.actualizarTokenGre(c.getRuc(), token, expira);

        return token;
    }

    // ─────────────────────────────────────────────────────────
    // ENVÍO REST A SUNAT
    // ─────────────────────────────────────────────────────────

    private String enviarGreRest(String rucEmisor, String bearerToken,
                                  String nombreZip, String zipBase64,
                                  String hashZip) throws Exception {

        String url = getGreApiBase() + "/contribuyente/gre/comprobantes";

        // Body JSON según especificación SUNAT GRE
        String jsonBody = """
            {
              "archivo": {
                "nomArchivo": "%s",
                "arcGreZip": "%s",
                "hashZip": "%s"
              }
            }
            """.formatted(nombreZip, zipBase64, hashZip);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
            ? conn.getInputStream() : conn.getErrorStream();

        JsonNode respJson = objectMapper.readTree(is.readAllBytes());

        if (status != 200) {
            String cod = respJson.path("cod").asText();
            String msg = respJson.path("msg").asText();
            throw new RuntimeException("SUNAT GRE error " + cod + ": " + msg);
        }

        return respJson.path("numTicket").asText();
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private byte[] comprimirEnZip(String nombreArchivo, byte[] contenido) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(nombreArchivo));
            zos.write(contenido);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String urlEncode(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }

    private String getGreAuthBase() {
        return "prod".equalsIgnoreCase(modo) ? greAuthProd : greAuthBeta;
    }

    private String getGreApiBase() {
        return "prod".equalsIgnoreCase(modo) ? greApiProd : greApiBeta;
    }

    // ─────────────────────────────────────────────────────────
    // TODOs — a implementar cuando se defina el DTO de GRE
    // ─────────────────────────────────────────────────────────

    /** TODO: reemplazar con xmlBuilderService.buildGreXml(ruc, greRequest) */
    private String buildGreXmlPlaceholder(String ruc, Object request) {
        throw new UnsupportedOperationException(
            "TODO: Implementar generación de XML GRE UBL 2.1. " +
            "Ver estructura en: https://cpe.sunat.gob.pe (Anexo GRE)");
    }

    /** TODO: extraer serie del GreRequest DTO cuando esté definido */
    private String extraerSerie(Object request) {
        throw new UnsupportedOperationException("TODO: extraer serie del GreRequest");
    }

    /** TODO: extraer correlativo del GreRequest DTO cuando esté definido */
    private String extraerCorrelativo(Object request) {
        throw new UnsupportedOperationException("TODO: extraer correlativo del GreRequest");
    }
}
