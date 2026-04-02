package com.tuempresa.facturador.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmisionRequest {

    @NotBlank @Size(min = 4, max = 4)
    private String serie;

    @NotNull
    private LocalDate fechaEmision;

    @NotBlank @Size(min = 3, max = 3)
    private String moneda;

    private BigDecimal tipoCambio;

    @NotNull @Valid
    private ReceptorDto receptor;

    @NotEmpty @Valid
    private List<ItemDto> items;

    private String observaciones;

    /**
     * Forma de pago según Resolución SUNAT 000193-2020.
     * Valores: "Contado" (default) o "Credito".
     * Si es null se asume Contado.
     */
    private String formaPago;

    /**
     * Cuotas para pago a crédito. Solo aplica cuando formaPago="Credito".
     * Máximo 10 cuotas práctico (SUNAT no define un límite duro).
     * La suma de montos debe coincidir con el importe total del comprobante.
     */
    @Valid
    private List<CuotaDto> cuotas = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CuotaDto {
        @NotNull @DecimalMin("0.01")
        private BigDecimal monto;

        @NotNull
        private LocalDate fechaVencimiento;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReceptorDto {
        @NotBlank private String tipoDocumento;  // 6=RUC, 1=DNI
        @NotBlank private String nroDocumento;
        @NotBlank private String razonSocial;
        private String direccion;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemDto {
        private String codigoProducto;
        @NotBlank private String descripcion;
        private String unidadMedida;
        @NotNull @DecimalMin("0.0001") private BigDecimal cantidad;
        @NotNull @DecimalMin("0.00")   private BigDecimal precioUnitario;
        private String tipoAfectacionIgv;  // Default "10" (Gravado)
        private BigDecimal descuento;
    }
}
