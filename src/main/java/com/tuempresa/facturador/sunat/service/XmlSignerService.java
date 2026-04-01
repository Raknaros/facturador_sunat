package com.tuempresa.facturador.sunat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Firma el XML con XMLDSig usando el certificado digital (.p12) de la empresa.
 * Es el paso más crítico — SUNAT rechaza XMLs mal firmados o con firma inválida.
 *
 * Archivo: src/main/java/com/tuempresa/facturador/sunat/service/XmlSignerService.java
 *
 * El proceso es:
 * 1. Cargar KeyStore del RUC (vía CertificadoService)
 * 2. Obtener PrivateKey y X509Certificate del KeyStore
 * 3. Aplicar firma XMLDSig sobre el documento
 * 4. Insertar el bloque <ds:Signature> dentro del XML
 * 5. Retornar XML firmado como String
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmlSignerService {

    private final CertificadoService certificadoService;

    /**
     * Firma el XML UBL con el certificado digital del emisor.
     *
     * @param empresaRuc RUC del emisor (para buscar su certificado en PostgreSQL)
     * @param xmlSinFirma XML generado por XmlBuilderService (sin firma)
     * @return XML firmado listo para enviar a SUNAT
     */
    public String firmar(String empresaRuc, String xmlSinFirma) {
        log.debug("Firmando XML para RUC: {}", empresaRuc);

        try {
            // 1. Cargar KeyStore del RUC desde PostgreSQL (en memoria, no en disco)
            KeyStore keyStore    = certificadoService.cargarKeyStore(empresaRuc);
            String alias         = certificadoService.obtenerAlias(keyStore);
            String certPassword  = certificadoService.obtenerPassword(empresaRuc);

            // 2. Extraer PrivateKey y certificado X509
            PrivateKey    privateKey = (PrivateKey)   keyStore.getKey(alias, certPassword.toCharArray());
            X509Certificate cert     = (X509Certificate) keyStore.getCertificate(alias);

            if (privateKey == null) {
                throw new RuntimeException("No se encontró PrivateKey en el certificado del RUC: " + empresaRuc);
            }

            // 3. Parsear el XML a Document DOM
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document document = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlSinFirma.getBytes("UTF-8")));

            // 4. Construir la firma XMLDSig
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            // Reference: firma sobre todo el documento (#)
            Reference ref = fac.newReference(
                "",
                fac.newDigestMethod(DigestMethod.SHA256, null),
                Collections.singletonList(
                    fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)
                ),
                null, null
            );

            // SignedInfo con algoritmo RSA-SHA256
            SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(
                    CanonicalizationMethod.INCLUSIVE,
                    (C14NMethodParameterSpec) null
                ),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                Collections.singletonList(ref)
            );

            // KeyInfo: incluir el certificado X509 para que SUNAT lo valide
            KeyInfoFactory kif      = fac.getKeyInfoFactory();
            List<Object> x509Content = Collections.singletonList(cert);
            X509Data x509Data       = kif.newX509Data(x509Content);
            KeyInfo keyInfo         = kif.newKeyInfo(Collections.singletonList(x509Data));

            // 5. Firmar el documento
            XMLSignature signature = fac.newXMLSignature(signedInfo, keyInfo);
            DOMSignContext signContext = new DOMSignContext(privateKey, document.getDocumentElement());
            signature.sign(signContext);

            // 6. Serializar Document firmado a String
            String xmlFirmado = documentToString(document);
            log.debug("XML firmado correctamente para RUC: {}", empresaRuc);
            return xmlFirmado;

        } catch (Exception e) {
            throw new RuntimeException("Error al firmar XML para RUC: " + empresaRuc + " → " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // Helper: Document DOM → String XML
    // ─────────────────────────────────────────────
    private String documentToString(Document document) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
