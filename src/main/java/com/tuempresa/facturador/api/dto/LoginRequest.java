package com.tuempresa.facturador.api.dto;

public class LoginRequest {

    private String ruc;
    private String usuarioSol;
    private String passwordSol;

    public String getRuc()          { return ruc; }
    public String getUsuarioSol()   { return usuarioSol; }
    public String getPasswordSol()  { return passwordSol; }

    public void setRuc(String ruc)                  { this.ruc = ruc; }
    public void setUsuarioSol(String usuarioSol)    { this.usuarioSol = usuarioSol; }
    public void setPasswordSol(String passwordSol)  { this.passwordSol = passwordSol; }
}
