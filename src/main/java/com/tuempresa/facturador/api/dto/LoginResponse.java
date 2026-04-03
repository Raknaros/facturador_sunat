package com.tuempresa.facturador.api.dto;

public class LoginResponse {

    private final String token;
    private final String ruc;
    private final String razonSocial;
    private final String solUser;
    private final String expiraEn;
    private final long   expiraEnMs;

    private LoginResponse(Builder b) {
        this.token      = b.token;
        this.ruc        = b.ruc;
        this.razonSocial = b.razonSocial;
        this.solUser    = b.solUser;
        this.expiraEn   = b.expiraEn;
        this.expiraEnMs = b.expiraEnMs;
    }

    public String getToken()       { return token; }
    public String getRuc()         { return ruc; }
    public String getRazonSocial() { return razonSocial; }
    public String getSolUser()     { return solUser; }
    public String getExpiraEn()    { return expiraEn; }
    public long   getExpiraEnMs()  { return expiraEnMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String token;
        private String ruc;
        private String razonSocial;
        private String solUser;
        private String expiraEn;
        private long   expiraEnMs;

        public Builder token(String v)       { this.token = v;       return this; }
        public Builder ruc(String v)         { this.ruc = v;         return this; }
        public Builder razonSocial(String v) { this.razonSocial = v; return this; }
        public Builder solUser(String v)     { this.solUser = v;     return this; }
        public Builder expiraEn(String v)    { this.expiraEn = v;    return this; }
        public Builder expiraEnMs(long v)    { this.expiraEnMs = v;  return this; }

        public LoginResponse build() { return new LoginResponse(this); }
    }
}
