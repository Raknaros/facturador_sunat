package com.tuempresa.facturador.api.controller;

import com.tuempresa.facturador.api.dto.EmisionRequest;
import com.tuempresa.facturador.api.dto.EmisionResponse;
import com.tuempresa.facturador.internal.service.ComprobanteService;
import com.tuempresa.facturador.sunat.service.SunatSenderService;
import com.tuempresa.facturador.sunat.service.XmlBuilderService;
import com.tuempresa.facturador.sunat.service.XmlSignerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/{ruc}/facturas")
@Tag(name = "Facturas", description = "Emisión de facturas electrónicas")
public class FacturaController {

    private final XmlBuilderService  xmlBuilderService;
    private final XmlSignerService   xmlSignerService;
    private final SunatSenderService sunatSenderService;
    private final ComprobanteService comprobanteService;

    public FacturaController(XmlBuilderService xmlBuilderService, XmlSignerService xmlSignerService,
                             SunatSenderService sunatSenderService, ComprobanteService comprobanteService) {
        this.xmlBuilderService  = xmlBuilderService;
        this.xmlSignerService   = xmlSignerService;
        this.sunatSenderService = sunatSenderService;
        this.comprobanteService = comprobanteService;
    }

    /**
     * Emite UNA factura → sendBill → CDR inmediato.
     */
    @PostMapping
    @Operation(summary = "Emitir factura (individual, CDR inmediato)")
    public ResponseEntity<EmisionResponse> emitir(
            @PathVariable String ruc,
            @Valid @RequestBody EmisionRequest request) {

        String correlativo = comprobanteService.siguienteCorrelativo(ruc, "FACTURA", request.getSerie());
        String xml         = xmlBuilderService.buildFacturaXml(ruc, request, correlativo);
        String xmlFirmado  = xmlSignerService.firmar(ruc, xml);
        EmisionResponse resp = sunatSenderService.enviarFactura(ruc, xmlFirmado, request, correlativo);
        comprobanteService.registrar(ruc, "FACTURA", request.getSerie(), correlativo, resp);

        return ResponseEntity.ok(resp);
    }

    /**
     * Emite un LOTE de facturas → sendPack → ticket asíncrono.
     */
    @PostMapping("/lote")
    @Operation(summary = "Emitir lote de facturas (asíncrono, devuelve ticket)")
    public ResponseEntity<EmisionResponse> emitirLote(
            @PathVariable String ruc,
            @Valid @RequestBody List<EmisionRequest> requests) {

        if (requests.isEmpty() || requests.size() > 500) {
            return ResponseEntity.badRequest().build();
        }
        EmisionResponse resp = sunatSenderService.enviarLoteFacturas(ruc, requests, comprobanteService);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar estado de factura")
    public ResponseEntity<?> consultar(@PathVariable String ruc, @PathVariable Long id) {
        return ResponseEntity.ok(comprobanteService.buscarPorId(ruc, id));
    }

    @GetMapping("/lote/{ticket}")
    @Operation(summary = "Consultar estado de lote por ticket")
    public ResponseEntity<?> consultarTicket(
            @PathVariable String ruc, @PathVariable String ticket) {
        return ResponseEntity.ok(sunatSenderService.consultarTicket(ruc, ticket));
    }
}
