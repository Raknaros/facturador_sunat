package com.tuempresa.facturador.sunat.service;

import com.tuempresa.facturador.api.dto.EmisionRequest;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import io.github.project.openubl.xmlbuilderlib.config.DefaultConfig;
import io.github.project.openubl.xmlbuilderlib.facade.DocumentManager;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ClienteInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.DireccionInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.FirmanteInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ProveedorInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.DocumentLineInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.invoice.InvoiceInputModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el XML UBL 2.1 según los catálogos y reglas de SUNAT.
 * El XML generado aquí NO está firmado — la firma la aplica XmlSignerService.
 *
 * Usa xbuilder 1.1.4.Final (io.github.project-openubl:xbuilder).
 * API: DocumentManager.createXML(InvoiceInputModel, Config).getXml()
 */
@Service
public class XmlBuilderService {

    private static final Logger log = LoggerFactory.getLogger(XmlBuilderService.class);
    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    private final ContribuyenteRepository contribuyenteRepo;

    public XmlBuilderService(ContribuyenteRepository contribuyenteRepo) {
        this.contribuyenteRepo = contribuyenteRepo;
    }

    public String buildFacturaXml(String empresaRuc, EmisionRequest req, String correlativo) {
        log.debug("Construyendo XML Factura para RUC: {}", empresaRuc);
        return buildInvoiceXml(empresaRuc, req, correlativo);
    }

    public String buildBoletaXml(String empresaRuc, EmisionRequest req, String correlativo) {
        log.debug("Construyendo XML Boleta para RUC: {}", empresaRuc);
        return buildInvoiceXml(empresaRuc, req, correlativo);
    }

    // ─────────────────────────────────────────────

    private String buildInvoiceXml(String empresaRuc, EmisionRequest req, String correlativo) {
        Contribuyente c = contribuyenteRepo.findByRucAndActivoTrue(empresaRuc)
            .orElseThrow(() -> new RuntimeException("Contribuyente no encontrado: " + empresaRuc));

        DefaultConfig config = new DefaultConfig();
        config.setDefaultMoneda(req.getMoneda() != null ? req.getMoneda() : "PEN");

        InvoiceInputModel invoice = new InvoiceInputModel();
        invoice.setSerie(req.getSerie());
        invoice.setNumero(Integer.parseInt(correlativo));   // "00000001" → 1
        invoice.setFechaEmision(
            req.getFechaEmision().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        );
        // Firmante: rellena <cac:Signature> con los datos del emisor.
        // Sin esto, xbuilder genera <cac:Signature/> vacío y SUNAT rechaza con error 3244.
        FirmanteInputModel firmante = new FirmanteInputModel();
        firmante.setRuc(c.getRuc());
        firmante.setRazonSocial(c.getRazonSocial());
        invoice.setFirmante(firmante);

        invoice.setProveedor(buildProveedor(c));
        invoice.setCliente(buildCliente(req.getReceptor()));
        invoice.setDetalle(buildDetalle(req.getItems()));

        String xmlBase = DocumentManager.createXML(invoice, config).getXml();

        // xbuilder 1.1.4.Final no expone setTipoOperacion() ni FormaPago en su API.
        // Ambos son obligatorios para SUNAT — los inyectamos vía DOM.
        String formaPago = (req.getFormaPago() != null && !req.getFormaPago().isBlank())
            ? req.getFormaPago() : "Contado";
        String moneda = req.getMoneda() != null ? req.getMoneda() : "PEN";
        String xmlConTipoOp = inyectarTipoOperacion(xmlBase, "0101");
        return inyectarFormaPago(xmlConTipoOp, formaPago, moneda, req.getCuotas());
    }

    private ProveedorInputModel buildProveedor(Contribuyente c) {
        ProveedorInputModel p = new ProveedorInputModel();
        p.setRuc(c.getRuc());
        p.setRazonSocial(c.getRazonSocial());
        p.setNombreComercial(c.getNombreComercial());

        DireccionInputModel dir = new DireccionInputModel();
        dir.setUbigeo(c.getUbigeo());
        dir.setCodigoLocal("0000");          // Establecimiento principal (SUNAT error 3030)
        dir.setUrbanizacion("NONE");         // Requerido por SUNAT; "NONE" si no aplica
        dir.setDepartamento(c.getDepartamento());
        dir.setProvincia(c.getProvincia());
        dir.setDistrito(c.getDistrito());
        dir.setDireccion(c.getDireccion());
        p.setDireccion(dir);

        return p;
    }

    private ClienteInputModel buildCliente(EmisionRequest.ReceptorDto dto) {
        ClienteInputModel cl = new ClienteInputModel();
        cl.setTipoDocumentoIdentidad(dto.getTipoDocumento());
        cl.setNumeroDocumentoIdentidad(dto.getNroDocumento());
        cl.setNombre(dto.getRazonSocial());

        if (dto.getDireccion() != null) {
            DireccionInputModel dir = new DireccionInputModel();
            dir.setDireccion(dto.getDireccion());
            cl.setDireccion(dir);
        }

        return cl;
    }

    private List<DocumentLineInputModel> buildDetalle(List<EmisionRequest.ItemDto> items) {
        List<DocumentLineInputModel> lineas = new ArrayList<>();

        for (EmisionRequest.ItemDto item : items) {
            String tipoIgv = item.getTipoAfectacionIgv() != null
                ? item.getTipoAfectacionIgv() : "10";

            // El usuario envía el precio con IGV incluido
            BigDecimal precioConIgv = item.getPrecioUnitario();
            BigDecimal precioSinIgv = tipoIgv.equals("10")
                ? precioConIgv.divide(BigDecimal.ONE.add(IGV_RATE), 10, RoundingMode.HALF_UP)
                : precioConIgv;

            DocumentLineInputModel line = new DocumentLineInputModel();
            line.setDescripcion(item.getDescripcion());
            line.setCantidad(item.getCantidad());
            line.setPrecioConIgv(precioConIgv);
            line.setPrecioUnitario(precioSinIgv);
            line.setUnidadMedida(item.getUnidadMedida() != null ? item.getUnidadMedida() : "NIU");
            line.setTipoIgv(tipoIgv);
            lineas.add(line);
        }

        return lineas;
    }

    /**
     * xbuilder 1.1.4.Final no expone API para el "tipo de operación" (Catálogo 51).
     * Este método agrega listID y name al nodo cbc:InvoiceTypeCode generado por xbuilder.
     * Debe llamarse ANTES de firmar para que la firma cubra el XML completo y correcto.
     */
    private String inyectarTipoOperacion(String xml, String tipoOperacion) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // StringReader evita el conflicto de encoding: xbuilder declara ISO-8859-1
            // pero el String Java ya es Unicode — parsear como Reader ignora esa declaración.
            org.xml.sax.InputSource is = new org.xml.sax.InputSource(new java.io.StringReader(xml));
            Document doc = dbf.newDocumentBuilder().parse(is);

            NodeList nodes = doc.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                "InvoiceTypeCode");

            log.info("[INJECT] Buscando cbc:InvoiceTypeCode — encontrados: {}", nodes.getLength());

            if (nodes.getLength() > 0) {
                Element el = (Element) nodes.item(0);
                log.info("[INJECT] InvoiceTypeCode ANTES — valor='{}' listID='{}' name='{}'",
                    el.getTextContent(), el.getAttribute("listID"), el.getAttribute("name"));
                el.setAttribute("listID", tipoOperacion);
                el.setAttribute("name", "VENTA INTERNA");
                log.info("[INJECT] InvoiceTypeCode DESPUES — listID='{}' name='{}'",
                    el.getAttribute("listID"), el.getAttribute("name"));
            } else {
                log.warn("cbc:InvoiceTypeCode no encontrado en el XML de xbuilder — error 3244 probable");
                // Intentar sin namespace como fallback
                NodeList nodes2 = doc.getElementsByTagName("InvoiceTypeCode");
                log.warn("[INJECT] Fallback sin NS — encontrados: {}", nodes2.getLength());
                if (nodes2.getLength() > 0) {
                    Element el = (Element) nodes2.item(0);
                    log.warn("[INJECT] InvoiceTypeCode (sin NS) — valor='{}' listID='{}'",
                        el.getTextContent(), el.getAttribute("listID"));
                }
                return xml;
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toString("UTF-8");

        } catch (Exception e) {
            log.error("No se pudo inyectar tipoOperacion en el XML: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Inyecta cac:PaymentTerms requerido por Resolución SUNAT 000193-2020.
     *
     * Contado:
     *   <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Contado</cbc:PaymentMeansID></cac:PaymentTerms>
     *
     * Crédito (un bloque cabecera + un bloque por cuota):
     *   <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Credito</cbc:PaymentMeansID>
     *     <cbc:Amount currencyID="PEN">1180.00</cbc:Amount></cac:PaymentTerms>
     *   <cac:PaymentTerms><cbc:ID>Cuota001</cbc:ID><cbc:PaymentMeansID>Cuota</cbc:PaymentMeansID>
     *     <cbc:Amount currencyID="PEN">590.00</cbc:Amount>
     *     <cbc:PaymentDueDate>2026-05-01</cbc:PaymentDueDate></cac:PaymentTerms>
     *   ...
     */
    private String inyectarFormaPago(String xml, String formaPago, String moneda,
                                      List<EmisionRequest.CuotaDto> cuotas) {
        try {
            final String nsCac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
            final String nsCbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            org.xml.sax.InputSource is = new org.xml.sax.InputSource(new java.io.StringReader(xml));
            Document doc = dbf.newDocumentBuilder().parse(is);

            if (doc.getElementsByTagNameNS(nsCac, "PaymentTerms").getLength() > 0) {
                log.info("[INJECT] cac:PaymentTerms ya presente, no se inyecta");
                return xml;
            }

            NodeList taxTotalNodes = doc.getElementsByTagNameNS(nsCac, "TaxTotal");
            Element root = doc.getDocumentElement();

            boolean esCredito = "Credito".equalsIgnoreCase(formaPago)
                && cuotas != null && !cuotas.isEmpty();

            if (esCredito) {
                // Obtener monto total payable del XML (ya calculado por xbuilder)
                String totalStr = "0.00";
                NodeList payable = doc.getElementsByTagNameNS(nsCbc, "PayableAmount");
                if (payable.getLength() > 0) totalStr = payable.item(0).getTextContent().trim();

                // Bloque cabecera: FormaPago/Credito con total
                Element ptHead = doc.createElementNS(nsCac, "cac:PaymentTerms");
                appendText(doc, ptHead, nsCbc, "cbc:ID", "FormaPago");
                appendText(doc, ptHead, nsCbc, "cbc:PaymentMeansID", "Credito");
                Element amtHead = doc.createElementNS(nsCbc, "cbc:Amount");
                amtHead.setAttribute("currencyID", moneda);
                amtHead.setTextContent(totalStr);
                ptHead.appendChild(amtHead);
                insertBeforeTaxTotal(root, ptHead, taxTotalNodes);

                // Un bloque por cuota
                for (int i = 0; i < cuotas.size(); i++) {
                    EmisionRequest.CuotaDto cuota = cuotas.get(i);
                    String cuotaId = String.format("Cuota%03d", i + 1);

                    Element ptCuota = doc.createElementNS(nsCac, "cac:PaymentTerms");
                    appendText(doc, ptCuota, nsCbc, "cbc:ID", cuotaId);
                    appendText(doc, ptCuota, nsCbc, "cbc:PaymentMeansID", "Cuota");
                    Element amtCuota = doc.createElementNS(nsCbc, "cbc:Amount");
                    amtCuota.setAttribute("currencyID", moneda);
                    amtCuota.setTextContent(cuota.getMonto().setScale(2, RoundingMode.HALF_UP).toPlainString());
                    ptCuota.appendChild(amtCuota);
                    appendText(doc, ptCuota, nsCbc, "cbc:PaymentDueDate",
                        cuota.getFechaVencimiento().toString());
                    insertBeforeTaxTotal(root, ptCuota, taxTotalNodes);
                }
                log.info("[INJECT] cac:PaymentTerms Credito inyectado con {} cuota(s)", cuotas.size());

            } else {
                // Contado: un único bloque sin monto ni fecha
                Element pt = doc.createElementNS(nsCac, "cac:PaymentTerms");
                appendText(doc, pt, nsCbc, "cbc:ID", "FormaPago");
                appendText(doc, pt, nsCbc, "cbc:PaymentMeansID", "Contado");
                insertBeforeTaxTotal(root, pt, taxTotalNodes);
                log.info("[INJECT] cac:PaymentTerms Contado inyectado");
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toString("UTF-8");

        } catch (Exception e) {
            log.error("No se pudo inyectar FormaPago en el XML: {}", e.getMessage());
            return xml;
        }
    }

    private void appendText(Document doc, Element parent, String ns, String qname, String value) {
        Element el = doc.createElementNS(ns, qname);
        el.setTextContent(value);
        parent.appendChild(el);
    }

    private void insertBeforeTaxTotal(Element root, Element newNode, NodeList taxTotalNodes) {
        if (taxTotalNodes.getLength() > 0) {
            root.insertBefore(newNode, taxTotalNodes.item(0));
        } else {
            root.appendChild(newNode);
            log.warn("[INJECT] cac:TaxTotal no encontrado — nodo inyectado al final");
        }
    }
}
