package com.tuempresa.facturador.api.controller;

import com.tuempresa.facturador.api.dto.EmisionResponse;
import com.tuempresa.facturador.sunat.service.GREService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/{ruc}/gre")
@RequiredArgsConstructor
@Tag(name = "GRE", description = "Guías de Remisión Electrónica (API REST SUNAT + OAuth)")
public class GREController {

    private final GREService greService;

    /**
     * Emite una GRE Remitente — asíncrono, devuelve ticket.
     * TODO: tipificar request con GreRemitenteRequest cuando esté definido.
     */
    @PostMapping("/remitente")
    @Operation(summary = "Emitir GRE Remitente (asíncrono — devuelve ticket)")
    public ResponseEntity<EmisionResponse> emitirRemitente(
            @PathVariable String ruc,
            @RequestBody Object request) {

        EmisionResponse resp = greService.emitirGreRemitente(ruc, request);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/estado")
    @Operation(summary = "Consultar estado de GRE por ticket SUNAT")
    public ResponseEntity<EmisionResponse> consultarEstado(
            @PathVariable String ruc,
            @PathVariable Long id) {

        return ResponseEntity.ok(EmisionResponse.builder()
            .mensajeError("TODO: implementar lookup del ticket por comprobanteId=" + id)
            .build());
    }
}
