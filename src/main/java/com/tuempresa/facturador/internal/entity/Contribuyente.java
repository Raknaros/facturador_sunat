package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contribuyentes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contribuyente {

    @Id
    @Column(name = "ruc", length = 11)
    private String ruc;

    // ── Datos de la empresa (para generar XML emisor) ─────────
    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 200)
    private String nombreComercial;

    @Column(name = "direccion", nullable = false, length = 300)
    private String direccion;

    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    @Column(name = "departamento", length = 50)
    private String departamento;

    @Column(name = "provincia", length = 50)
    private String provincia;

    @Column(name = "distrito", length = 50)
    private String distrito;

    // ── Credenciales SOL (facturas/boletas SOAP) ──────────────
    @Column(name = "usuario_sol", nullable = false, length = 50)
    private String usuarioSol;

    @Column(name = "password_sol_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordSolEnc;

    // ── Certificado digital .p12 ──────────────────────────────
    @Column(name = "certificado_p12_enc", nullable = false, columnDefinition = "TEXT")
    private String certificadoP12Enc;

    @Column(name = "cert_password_enc", nullable = false, columnDefinition = "TEXT")
    private String certPasswordEnc;

    @Column(name = "cert_vence", nullable = false)
    private LocalDate certVence;

    // ── Credenciales GRE (API REST SUNAT OAuth) — opcionales ──
    @Column(name = "gre_client_id", length = 100)
    private String greClientId;

    @Column(name = "gre_client_secret_enc", columnDefinition = "TEXT")
    private String greClientSecretEnc;

    @Column(name = "gre_token", columnDefinition = "TEXT")
    private String greToken;

    @Column(name = "gre_token_expira")
    private LocalDateTime greTokenExpira;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean tieneCredsGre() {
        return greClientId != null && greClientSecretEnc != null;
    }

    public boolean tokenGreVigente() {
        return greToken != null
            && greTokenExpira != null
            && LocalDateTime.now().isBefore(greTokenExpira.minusMinutes(5));
    }
}
