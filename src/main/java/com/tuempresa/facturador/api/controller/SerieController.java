package com.tuempresa.facturador.api.controller;

import com.tuempresa.facturador.internal.service.ComprobanteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/{ruc}/series")
@Validated
@Tag(name = "Series y Correlativos", description = "Administración de series para migración entre facturadores")
public class SerieController {

    private final ComprobanteService comprobanteService;

    public SerieController(ComprobanteService comprobanteService) {
        this.comprobanteService = comprobanteService;
    }

    /**
     * Inicializa el contador de una serie para migración.
     *
     * Si la empresa venía de otro facturador y ya emitió hasta F001-00000150,
     * llama a este endpoint con ultimoNumero=150 antes de emitir el primer
     * comprobante en este sistema. El siguiente será F001-00000151.
     *
     * Tipos de comprobante válidos: FACTURA, BOLETA, NOTA_CREDITO, NOTA_DEBITO
     */
    @PutMapping("/{serie}/inicializar")
    @Operation(summary = "Inicializar correlativo de serie (migración desde otro facturador)")
    public ResponseEntity<Map<String, Object>> inicializar(
            @PathVariable String ruc,
            @PathVariable @NotBlank @Pattern(regexp = "[A-Z]\\d{3}",
                message = "Serie debe tener formato: letra mayúscula + 3 dígitos (ej: F001, B001)")
            String serie,
            @RequestParam @NotBlank String tipo,
            @RequestParam @Min(0) int ultimoNumero) {

        comprobanteService.inicializarCorrelativo(ruc, tipo.toUpperCase(), serie, ultimoNumero);

        return ResponseEntity.ok(Map.of(
            "ruc", ruc,
            "tipo", tipo.toUpperCase(),
            "serie", serie,
            "ultimoNumero", ultimoNumero,
            "proximoNumero", String.format("%08d", ultimoNumero + 1),
            "mensaje", "Correlativo inicializado. El próximo comprobante será "
                + serie + "-" + String.format("%08d", ultimoNumero + 1)
        ));
    }
}
