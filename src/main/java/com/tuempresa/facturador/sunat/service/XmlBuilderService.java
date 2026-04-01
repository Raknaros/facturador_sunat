package com.tuempresa.facturador.sunat.service;

import com.tuempresa.facturador.api.dto.EmisionRequest;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import io.github.project.openubl.xmlbuilderlib.config.DefaultConfig;
import io.github.project.openubl.xmlbuilderlib.facade.DocumentManager;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ClienteInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.DireccionInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.common.ProveedorInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.DocumentLineInputModel;
import io.github.project.openubl.xmlbuilderlib.models.input.standard.invoice.InvoiceInputModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class XmlBuilderService {

    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    private final ContribuyenteRepository contribuyenteRepo;

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
        invoice.setProveedor(buildProveedor(c));
        invoice.setCliente(buildCliente(req.getReceptor()));
        invoice.setDetalle(buildDetalle(req.getItems()));

        return DocumentManager.createXML(invoice, config).getXml();
    }

    private ProveedorInputModel buildProveedor(Contribuyente c) {
        ProveedorInputModel p = new ProveedorInputModel();
        p.setRuc(c.getRuc());
        p.setRazonSocial(c.getRazonSocial());
        p.setNombreComercial(c.getNombreComercial());

        DireccionInputModel dir = new DireccionInputModel();
        dir.setUbigeo(c.getUbigeo());
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
}
