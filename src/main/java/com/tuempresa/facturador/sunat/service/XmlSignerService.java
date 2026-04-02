package com.tuempresa.facturador.sunat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Firma el XML UBL 2.1 con XMLDSig usando el certificado digital (.p12) del emisor.
 *
 * Configuración validada contra el firmador oficial de SUNAT:
 * - ds:Signature va dentro de ext:ExtensionContent (no en el root del documento)
 * - CanonicalizationMethod SignedInfo: INCLUSIVE (http://www.w3.org/TR/2001/REC-xml-c14n-20010315)
 * - Reference URI="": todo el documento; único transform ENVELOPED
 * - DigestMethod + SignatureMethod: SHA-256 / RSA-SHA256
 * - Serialización: ByteArrayOutputStream → UTF-8 (no StringWriter)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmlSignerService {

    private static final String NS_EXT =
        "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String NS_DS =
        "http://www.w3.org/2000/09/xmldsig#";

    private final CertificadoService certificadoService;

    public String firmar(String empresaRuc, String xmlSinFirma) {
        log.info("[FIRMA] XML sin firmar (primeros 3000 chars):\n{}",
            xmlSinFirma.length() > 3000 ? xmlSinFirma.substring(0, 3000) + "..." : xmlSinFirma);

        try {
            // 1. Cargar KeyStore y extraer PrivateKey + certificado X509
            KeyStore keyStore   = certificadoService.cargarKeyStore(empresaRuc);
            String alias        = certificadoService.obtenerAlias(keyStore);
            String certPassword = certificadoService.obtenerPassword(empresaRuc);

            PrivateKey      privateKey = (PrivateKey)      keyStore.getKey(alias, certPassword.toCharArray());
            X509Certificate cert       = (X509Certificate) keyStore.getCertificate(alias);

            if (privateKey == null) {
                throw new RuntimeException("No se encontró PrivateKey en el certificado del RUC: " + empresaRuc);
            }

            // 2. Parsear el XML a Document DOM (namespace-aware)
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // InputSource con StringReader: ignora la declaración encoding del XML
            // (xbuilder puede generar ISO-8859-1; el String Java ya es Unicode).
            Document document = dbf.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlSinFirma)));

            // 3. Localizar ext:ExtensionContent — el ds:Signature va AQUÍ, no en el root
            NodeList extList = document.getDocumentElement()
                .getElementsByTagNameNS(NS_EXT, "ExtensionContent");
            if (extList.getLength() == 0) {
                throw new RuntimeException(
                    "El XML no contiene <ext:ExtensionContent>. " +
                    "xbuilder debe generar ese nodo para UBL 2.1 con extensiones.");
            }
            Element extensionContent = (Element) extList.item(0);
            log.info("[FIRMA] ext:ExtensionContent encontrado. Hijos actuales: {}",
                extensionContent.getChildNodes().getLength());

            // 4. Construir la firma XMLDSig
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            // Reference URI="" → todo el documento.
            // Solo transform ENVELOPED: excluye el propio <ds:Signature> del cálculo de digest.
            // NO se agrega un segundo transform C14N — el oficial SUNAT no lo usa y
            // agregar EXCLUSIVE/INCLUSIVE en la Reference con UBL multi-namespace puede
            // producir diferencias entre lo que Java hashea y lo que SUNAT verifica.
            Reference ref = fac.newReference(
                "",
                fac.newDigestMethod(DigestMethod.SHA256, null),
                Collections.singletonList(
                    fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)
                ),
                null, null
            );

            // INCLUSIVE C14N en SignedInfo — algoritmo que usa el firmador oficial de SUNAT
            SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(
                    CanonicalizationMethod.INCLUSIVE,
                    (C14NMethodParameterSpec) null
                ),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                Collections.singletonList(ref)
            );

            // KeyInfo: incluir certificado X509 completo para que SUNAT lo valide
            KeyInfoFactory kif      = fac.getKeyInfoFactory();
            X509Data       x509Data = kif.newX509Data(Collections.singletonList(cert));
            KeyInfo        keyInfo  = kif.newKeyInfo(Collections.singletonList(x509Data));

            // 5. Firmar — DOMSignContext apunta a extensionContent (no al root)
            XMLSignature   signature  = fac.newXMLSignature(signedInfo, keyInfo);
            DOMSignContext signCtx    = new DOMSignContext(privateKey, extensionContent);
            signCtx.setDefaultNamespacePrefix("ds");
            signature.sign(signCtx);

            // 6. Asignar Id="SignatureSP" al <ds:Signature> para que el
            //    <cbc:URI>#SignatureSP</cbc:URI> generado por xbuilder lo resuelva
            NodeList sigNodes = extensionContent.getElementsByTagNameNS(NS_DS, "Signature");
            if (sigNodes.getLength() > 0) {
                // Debe coincidir con <cbc:URI>#PROJECT-OPENUBL-SIGN</cbc:URI> que genera xbuilder
                ((Element) sigNodes.item(0)).setAttribute("Id", "PROJECT-OPENUBL-SIGN");
            }

            // 7. Serializar con ByteArrayOutputStream → UTF-8 bytes → String
            //    (más fiable que StringWriter que ignora el encoding property)
            String xmlFirmado = documentToString(document);
            log.info("[FIRMA] XML firmado OK para RUC {}. Longitud: {}", empresaRuc, xmlFirmado.length());
            // Log del XML firmado para verificar que listID sobrevive la firma
            int idxInvoiceType = xmlFirmado.indexOf("InvoiceTypeCode");
            if (idxInvoiceType >= 0) {
                int inicio = Math.max(0, idxInvoiceType - 5);
                int fin    = Math.min(xmlFirmado.length(), idxInvoiceType + 250);
                log.info("[FIRMA] InvoiceTypeCode en XML firmado: ...{}...", xmlFirmado.substring(inicio, fin));
            } else {
                log.warn("[FIRMA] InvoiceTypeCode NO encontrado en XML firmado!");
            }
            return xmlFirmado;

        } catch (Exception e) {
            throw new RuntimeException("Error al firmar XML para RUC: " + empresaRuc + " → " + e.getMessage(), e);
        }
    }

    private String documentToString(Document document) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        // ByteArrayOutputStream: el Transformer escribe bytes UTF-8 reales
        // y luego construimos el String desde esos mismos bytes.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(baos));
        return baos.toString("UTF-8");
    }
}
