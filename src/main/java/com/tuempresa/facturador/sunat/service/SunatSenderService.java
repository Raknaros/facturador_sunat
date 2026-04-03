package com.tuempresa.facturador.sunat.service;

import com.tuempresa.facturador.api.dto.EmisionRequest;
import com.tuempresa.facturador.api.dto.EmisionResponse;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import com.tuempresa.facturador.security.EncryptionService;
import com.tuempresa.facturador.sunat.dto.SunatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Envía el XML firmado al WebService SOAP de SUNAT y procesa el CDR.
 *
 * Flujo:
 * 1. Cargar credenciales SOL del RUC desde PostgreSQL
 * 2. Empaquetar XML en ZIP (nombre: RUC-TIPO-SERIE-CORRELATIVO.zip)
 * 3. Enviar SOAP request con credenciales SOL en Basic Auth
 * 4. Recibir respuesta ZIP (CDR)
 * 5. Descomprimir CDR y leer código de respuesta SUNAT
 */
@Service
public class SunatSenderService {

    private static final Logger log = LoggerFactory.getLogger(SunatSenderService.class);

    private final ContribuyenteRepository contribuyenteRepo;
    private final EncryptionService       encryptionService;

    public SunatSenderService(ContribuyenteRepository contribuyenteRepo,
                              EncryptionService encryptionService) {
        this.contribuyenteRepo = contribuyenteRepo;
        this.encryptionService = encryptionService;
    }

    @Value("${sunat.endpoints.factura-beta}")
    private String endpointFacturaBeta;

    @Value("${sunat.endpoints.factura-prod}")
    private String endpointFacturaProd;

    @Value("${sunat.modo}")
    private String modo;

    // ─────────────────────────────────────────────
    // ENVIAR FACTURA / BOLETA / NOTA
    // ─────────────────────────────────────────────

    public EmisionResponse enviarFactura(String empresaRuc, String xmlFirmado,
                                          EmisionRequest req, String correlativo) {
        log.info("Enviando {}-{} a SUNAT para RUC: {}", req.getSerie(), correlativo, empresaRuc);

        String tipoDoc    = req.getSerie().startsWith("F") ? "01" : "03";
        String tipoNombre = req.getSerie().startsWith("F") ? "FACTURA" : "BOLETA";
        // SUNAT exige que el correlativo en el nombre del ZIP sea el número entero sin ceros,
        // igual al que xbuilder pone en cbc:ID (ej: "F001-1", no "F001-00000001")
        int numCorrelativo = Integer.parseInt(correlativo);
        String nombreZip  = empresaRuc + "-" + tipoDoc + "-" + req.getSerie() + "-" + numCorrelativo;

        SunatResponse sr = enviar(empresaRuc, xmlFirmado, nombreZip, getEndpoint());

        return EmisionResponse.builder()
            .rucEmisor(empresaRuc)
            .tipo(tipoNombre)
            .serie(req.getSerie())
            .correlativo(correlativo)
            .numeroCompleto(req.getSerie() + "-" + correlativo)
            .aceptado(sr.isAceptado())
            .cdrCodigo(sr.getCodigoRespuesta())
            .cdrDescripcion(sr.getDescripcionRespuesta())
            .estado(sr.isAceptado() ? "ACEPTADO" : "RECHAZADO")
            .mensajeError(sr.getMensajeError())
            .build();
    }

    /**
     * Valida credenciales SOL contra SUNAT haciendo un ping con getStatus(ticket="00000000").
     * - HTTP 401 de SUNAT → credenciales inválidas → retorna false
     * - Cualquier otra respuesta (200, 500 con SOAP fault "ticket no existe") → auth aceptada → retorna true
     * - Error de red / timeout → lanza RuntimeException
     */
    public boolean validarCredencialesSOL(String usuarioSol, String passwordSol) {
        String soapPing = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:bil="http://service.sunat.gob.pe">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <bil:getStatus>
                      <ticket>00000000</ticket>
                    </bil:getStatus>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;
        try {
            URL url = new URL(getEndpoint());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);

            String auth = Base64.getEncoder().encodeToString(
                    (usuarioSol + ":" + passwordSol).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
            conn.setRequestProperty("SOAPAction", "");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(soapPing.getBytes(StandardCharsets.UTF_8));
            }

            int httpStatus = conn.getResponseCode();
            log.debug("[AUTH] Ping SUNAT usuario={} → HTTP {}", usuarioSol, httpStatus);

            // 401 = SUNAT rechazó las credenciales
            return httpStatus != HttpURLConnection.HTTP_UNAUTHORIZED;

        } catch (Exception e) {
            log.error("[AUTH] Error al conectar con SUNAT para validar credenciales: {}", e.getMessage());
            throw new RuntimeException(
                    "No se pudo verificar las credenciales contra SUNAT: " + e.getMessage(), e);
        }
    }

    public EmisionResponse consultarTicket(String empresaRuc, String ticket) {
        throw new UnsupportedOperationException("TODO: implementar consulta de ticket SUNAT");
    }

    public EmisionResponse enviarLoteFacturas(String empresaRuc,
            java.util.List<EmisionRequest> requests,
            com.tuempresa.facturador.internal.service.ComprobanteService comprobanteService) {
        throw new UnsupportedOperationException("TODO: implementar envío de lote (sendPack)");
    }

    // ─────────────────────────────────────────────
    // CORE: empaquetar, enviar y procesar CDR
    // ─────────────────────────────────────────────

    private SunatResponse enviar(String empresaRuc, String xmlFirmado,
                                  String nombreArchivo, String endpoint) {
        try {
            Contribuyente c = contribuyenteRepo.findByRucAndActivoTrue(empresaRuc)
                .orElseThrow(() -> new RuntimeException("Sin credenciales para RUC: " + empresaRuc));

            String usuarioSol  = c.getUsuarioSol();
            String passwordSol = encryptionService.decryptText(c.getPasswordSolEnc());

            byte[] zipBytes  = comprimirEnZip(nombreArchivo + ".xml",
                                              xmlFirmado.getBytes(StandardCharsets.UTF_8));
            String zipBase64 = Base64.getEncoder().encodeToString(zipBytes);
            String soapBody  = buildSoapEnvelope(nombreArchivo + ".zip", zipBase64);

            byte[] responseBytes = enviarSoap(endpoint, soapBody, usuarioSol, passwordSol);
            log.info("[SOAP] Respuesta SUNAT cruda (primeros 2000 chars):\n{}",
                new String(responseBytes, StandardCharsets.UTF_8).length() > 2000
                    ? new String(responseBytes, StandardCharsets.UTF_8).substring(0, 2000) + "..."
                    : new String(responseBytes, StandardCharsets.UTF_8));
            String cdrBase64     = extraerCdrBase64DeRespuestaSoap(responseBytes);
            byte[] cdrBytes      = Base64.getDecoder().decode(cdrBase64);
            String xmlCdr        = descomprimirZip(cdrBytes);
            log.info("[CDR] XML CDR completo:\n{}", xmlCdr);

            return parsearCdr(xmlCdr);

        } catch (Exception e) {
            log.error("Error enviando a SUNAT para RUC {}: {}", empresaRuc, e.getMessage(), e);
            return SunatResponse.builder()
                .aceptado(false)
                .mensajeError(e.getMessage())
                .build();
        }
    }

    // ─────────────────────────────────────────────
    // SOAP Envelope
    // ─────────────────────────────────────────────

    private String buildSoapEnvelope(String nombreZip, String zipBase64) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope
                xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:bil="http://service.sunat.gob.pe">
              <soapenv:Header/>
              <soapenv:Body>
                <bil:sendBill>
                  <fileName>%s</fileName>
                  <contentFile>%s</contentFile>
                </bil:sendBill>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(nombreZip, zipBase64);
    }

    // ─────────────────────────────────────────────
    // HTTP POST con Basic Auth (credenciales SOL)
    // ─────────────────────────────────────────────

    private byte[] enviarSoap(String endpoint, String soapBody,
                               String usuario, String password) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        String auth = Base64.getEncoder().encodeToString(
            (usuario + ":" + password).getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        conn.setRequestProperty("SOAPAction", "");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(soapBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
            ? conn.getInputStream() : conn.getErrorStream();

        return is.readAllBytes();
    }

    // ─────────────────────────────────────────────
    // Parsear CDR
    // ─────────────────────────────────────────────

    private SunatResponse parsearCdr(String xmlCdr) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlCdr.getBytes(StandardCharsets.UTF_8)));

            String codigo      = getTagValue(doc, "ResponseCode");
            String descripcion = getTagValue(doc, "Description");
            boolean aceptado   = "0".equals(codigo);

            log.info("CDR recibido — Código: {} | {}", codigo, descripcion);

            return SunatResponse.builder()
                .aceptado(aceptado)
                .codigoRespuesta(codigo)
                .descripcionRespuesta(descripcion)
                .xmlCdr(xmlCdr)
                .mensajeError(aceptado ? null : descripcion)
                .build();

        } catch (Exception e) {
            return SunatResponse.builder()
                .aceptado(false)
                .mensajeError("Error al parsear CDR: " + e.getMessage())
                .xmlCdr(xmlCdr)
                .build();
        }
    }

    // ─────────────────────────────────────────────
    // Helpers ZIP / XML
    // ─────────────────────────────────────────────

    private byte[] comprimirEnZip(String nombreArchivo, byte[] contenido) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(nombreArchivo));
            zos.write(contenido);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String descomprimirZip(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            // El CDR de SUNAT puede incluir una entrada "dummy/" antes del XML real.
            // Iteramos hasta encontrar la primera entrada no-directorio con contenido.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] content = zis.readAllBytes();
                    if (content.length > 0) {
                        log.info("[CDR] Entrada ZIP leída: '{}' ({} bytes)", entry.getName(), content.length);
                        return new String(content, StandardCharsets.UTF_8);
                    }
                }
            }
            throw new IOException("El ZIP del CDR no contiene ningún archivo XML válido");
        }
    }

    private String extraerCdrBase64DeRespuestaSoap(byte[] soapResponse) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc = dbf.newDocumentBuilder()
            .parse(new ByteArrayInputStream(soapResponse));

        // Detectar SOAP Fault antes de intentar extraer el CDR
        NodeList faultNodes = doc.getElementsByTagName("faultstring");
        if (faultNodes.getLength() > 0) {
            throw new RuntimeException("SUNAT SOAP Fault: " + faultNodes.item(0).getTextContent());
        }

        String cdrBase64 = getTagValue(doc, "applicationResponse");
        if (cdrBase64.isEmpty()) {
            throw new RuntimeException("SUNAT no devolvió applicationResponse. Respuesta: "
                + new String(soapResponse, StandardCharsets.UTF_8));
        }
        return cdrBase64;
    }

    private String getTagValue(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0)
            nodes = doc.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }

    private String getEndpoint() {
        return "prod".equalsIgnoreCase(modo) ? endpointFacturaProd : endpointFacturaBeta;
    }
}
