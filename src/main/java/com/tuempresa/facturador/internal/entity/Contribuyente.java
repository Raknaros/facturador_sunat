package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contribuyentes")
public class Contribuyente {

    @Id
    @Column(name = "ruc", length = 11)
    private String ruc;

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

    @Column(name = "usuario_sol", nullable = false, length = 50)
    private String usuarioSol;

    @Column(name = "password_sol_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordSolEnc;

    @Column(name = "certificado_p12_enc", nullable = false, columnDefinition = "TEXT")
    private String certificadoP12Enc;

    @Column(name = "cert_password_enc", nullable = false, columnDefinition = "TEXT")
    private String certPasswordEnc;

    @Column(name = "cert_vence", nullable = false)
    private LocalDate certVence;

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

    public Contribuyente() {}

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // Getters
    public String        getRuc()               { return ruc; }
    public String        getRazonSocial()        { return razonSocial; }
    public String        getNombreComercial()    { return nombreComercial; }
    public String        getDireccion()         { return direccion; }
    public String        getUbigeo()           { return ubigeo; }
    public String        getDepartamento()      { return departamento; }
    public String        getProvincia()         { return provincia; }
    public String        getDistrito()         { return distrito; }
    public String        getUsuarioSol()        { return usuarioSol; }
    public String        getPasswordSolEnc()    { return passwordSolEnc; }
    public String        getCertificadoP12Enc() { return certificadoP12Enc; }
    public String        getCertPasswordEnc()   { return certPasswordEnc; }
    public LocalDate     getCertVence()         { return certVence; }
    public String        getGreClientId()       { return greClientId; }
    public String        getGreClientSecretEnc(){ return greClientSecretEnc; }
    public String        getGreToken()          { return greToken; }
    public LocalDateTime getGreTokenExpira()    { return greTokenExpira; }
    public Boolean       getActivo()            { return activo; }
    public LocalDateTime getCreatedAt()         { return createdAt; }
    public LocalDateTime getUpdatedAt()         { return updatedAt; }

    // Setters
    public void setRuc(String v)               { this.ruc = v; }
    public void setRazonSocial(String v)        { this.razonSocial = v; }
    public void setNombreComercial(String v)    { this.nombreComercial = v; }
    public void setDireccion(String v)         { this.direccion = v; }
    public void setUbigeo(String v)           { this.ubigeo = v; }
    public void setDepartamento(String v)      { this.departamento = v; }
    public void setProvincia(String v)         { this.provincia = v; }
    public void setDistrito(String v)         { this.distrito = v; }
    public void setUsuarioSol(String v)        { this.usuarioSol = v; }
    public void setPasswordSolEnc(String v)    { this.passwordSolEnc = v; }
    public void setCertificadoP12Enc(String v) { this.certificadoP12Enc = v; }
    public void setCertPasswordEnc(String v)   { this.certPasswordEnc = v; }
    public void setCertVence(LocalDate v)       { this.certVence = v; }
    public void setGreClientId(String v)       { this.greClientId = v; }
    public void setGreClientSecretEnc(String v){ this.greClientSecretEnc = v; }
    public void setGreToken(String v)          { this.greToken = v; }
    public void setGreTokenExpira(LocalDateTime v) { this.greTokenExpira = v; }
    public void setActivo(Boolean v)           { this.activo = v; }

    public boolean tieneCredsGre() {
        return greClientId != null && greClientSecretEnc != null;
    }

    public boolean tokenGreVigente() {
        return greToken != null
            && greTokenExpira != null
            && LocalDateTime.now().isBefore(greTokenExpira.minusMinutes(5));
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String        ruc;
        private String        razonSocial;
        private String        nombreComercial;
        private String        direccion;
        private String        ubigeo;
        private String        departamento;
        private String        provincia;
        private String        distrito;
        private String        usuarioSol;
        private String        passwordSolEnc;
        private String        certificadoP12Enc;
        private String        certPasswordEnc;
        private LocalDate     certVence;
        private String        greClientId;
        private String        greClientSecretEnc;
        private String        greToken;
        private LocalDateTime greTokenExpira;
        private Boolean       activo = true;

        public Builder ruc(String v)               { this.ruc = v;               return this; }
        public Builder razonSocial(String v)        { this.razonSocial = v;        return this; }
        public Builder nombreComercial(String v)    { this.nombreComercial = v;    return this; }
        public Builder direccion(String v)         { this.direccion = v;         return this; }
        public Builder ubigeo(String v)           { this.ubigeo = v;           return this; }
        public Builder departamento(String v)      { this.departamento = v;      return this; }
        public Builder provincia(String v)         { this.provincia = v;         return this; }
        public Builder distrito(String v)         { this.distrito = v;         return this; }
        public Builder usuarioSol(String v)        { this.usuarioSol = v;        return this; }
        public Builder passwordSolEnc(String v)    { this.passwordSolEnc = v;    return this; }
        public Builder certificadoP12Enc(String v) { this.certificadoP12Enc = v; return this; }
        public Builder certPasswordEnc(String v)   { this.certPasswordEnc = v;   return this; }
        public Builder certVence(LocalDate v)       { this.certVence = v;         return this; }
        public Builder greClientId(String v)       { this.greClientId = v;       return this; }
        public Builder greClientSecretEnc(String v){ this.greClientSecretEnc = v; return this; }
        public Builder greToken(String v)          { this.greToken = v;          return this; }
        public Builder greTokenExpira(LocalDateTime v) { this.greTokenExpira = v; return this; }
        public Builder activo(Boolean v)           { this.activo = v;            return this; }

        public Contribuyente build() {
            Contribuyente c = new Contribuyente();
            c.ruc               = ruc;
            c.razonSocial       = razonSocial;
            c.nombreComercial   = nombreComercial;
            c.direccion        = direccion;
            c.ubigeo           = ubigeo;
            c.departamento     = departamento;
            c.provincia        = provincia;
            c.distrito        = distrito;
            c.usuarioSol       = usuarioSol;
            c.passwordSolEnc   = passwordSolEnc;
            c.certificadoP12Enc = certificadoP12Enc;
            c.certPasswordEnc  = certPasswordEnc;
            c.certVence        = certVence;
            c.greClientId      = greClientId;
            c.greClientSecretEnc = greClientSecretEnc;
            c.greToken         = greToken;
            c.greTokenExpira   = greTokenExpira;
            c.activo           = activo;
            return c;
        }
    }
}
