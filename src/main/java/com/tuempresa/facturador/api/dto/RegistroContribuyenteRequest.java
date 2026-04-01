package com.tuempresa.facturador.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistroContribuyenteRequest {

    @NotBlank @Size(min = 11, max = 11)
    private String ruc;

    // ── Datos de la empresa ───────────────────────────────────
    @NotBlank
    private String razonSocial;

    private String nombreComercial;

    @NotBlank
    private String direccion;

    @Size(min = 6, max = 6)
    private String ubigeo;

    private String departamento;
    private String provincia;
    private String distrito;

    // ── Credenciales SOL ──────────────────────────────────────
    @NotBlank
    private String usuarioSol;

    @NotBlank
    private String passwordSol;

    // ── Certificado .p12 ─────────────────────────────────────
    @NotBlank
    private String certificadoP12Base64;

    @NotBlank
    private String certPassword;

    @NotNull
    private LocalDate certVence;

    // ── Credenciales GRE (opcionales) ────────────────────────
    private String greClientId;
    private String greClientSecret;
}
