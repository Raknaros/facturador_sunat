package com.tuempresa.facturador.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class RegistroContribuyenteRequest {

    @NotBlank @Size(min = 11, max = 11)
    private String ruc;

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

    @NotBlank
    private String usuarioSol;

    @NotBlank
    private String passwordSol;

    @NotBlank
    private String certificadoP12Base64;

    @NotBlank
    private String certPassword;

    @NotNull
    private LocalDate certVence;

    private String greClientId;
    private String greClientSecret;

    public RegistroContribuyenteRequest() {}

    public String    getRuc()                  { return ruc; }
    public String    getRazonSocial()           { return razonSocial; }
    public String    getNombreComercial()       { return nombreComercial; }
    public String    getDireccion()            { return direccion; }
    public String    getUbigeo()              { return ubigeo; }
    public String    getDepartamento()         { return departamento; }
    public String    getProvincia()            { return provincia; }
    public String    getDistrito()            { return distrito; }
    public String    getUsuarioSol()           { return usuarioSol; }
    public String    getPasswordSol()          { return passwordSol; }
    public String    getCertificadoP12Base64() { return certificadoP12Base64; }
    public String    getCertPassword()         { return certPassword; }
    public LocalDate getCertVence()            { return certVence; }
    public String    getGreClientId()          { return greClientId; }
    public String    getGreClientSecret()      { return greClientSecret; }

    public void setRuc(String v)                  { this.ruc = v; }
    public void setRazonSocial(String v)           { this.razonSocial = v; }
    public void setNombreComercial(String v)       { this.nombreComercial = v; }
    public void setDireccion(String v)            { this.direccion = v; }
    public void setUbigeo(String v)              { this.ubigeo = v; }
    public void setDepartamento(String v)         { this.departamento = v; }
    public void setProvincia(String v)            { this.provincia = v; }
    public void setDistrito(String v)            { this.distrito = v; }
    public void setUsuarioSol(String v)           { this.usuarioSol = v; }
    public void setPasswordSol(String v)          { this.passwordSol = v; }
    public void setCertificadoP12Base64(String v) { this.certificadoP12Base64 = v; }
    public void setCertPassword(String v)         { this.certPassword = v; }
    public void setCertVence(LocalDate v)          { this.certVence = v; }
    public void setGreClientId(String v)          { this.greClientId = v; }
    public void setGreClientSecret(String v)      { this.greClientSecret = v; }
}
