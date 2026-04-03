package com.tuempresa.facturador.api.dto;

public class EmisionResponse {

    private Long    comprobanteId;
    private String  rucEmisor;
    private String  tipo;
    private String  serie;
    private String  correlativo;
    private String  numeroCompleto;
    private String  estado;
    private String  cdrCodigo;
    private String  cdrDescripcion;
    private String  ticketSunat;
    private boolean aceptado;
    private String  mensajeError;

    public EmisionResponse() {}

    // Getters
    public Long    getComprobanteId()   { return comprobanteId; }
    public String  getRucEmisor()       { return rucEmisor; }
    public String  getTipo()            { return tipo; }
    public String  getSerie()           { return serie; }
    public String  getCorrelativo()     { return correlativo; }
    public String  getNumeroCompleto()  { return numeroCompleto; }
    public String  getEstado()          { return estado; }
    public String  getCdrCodigo()       { return cdrCodigo; }
    public String  getCdrDescripcion()  { return cdrDescripcion; }
    public String  getTicketSunat()     { return ticketSunat; }
    public boolean isAceptado()         { return aceptado; }
    public String  getMensajeError()    { return mensajeError; }

    // Setters
    public void setComprobanteId(Long v)    { this.comprobanteId = v; }
    public void setRucEmisor(String v)      { this.rucEmisor = v; }
    public void setTipo(String v)           { this.tipo = v; }
    public void setSerie(String v)          { this.serie = v; }
    public void setCorrelativo(String v)    { this.correlativo = v; }
    public void setNumeroCompleto(String v) { this.numeroCompleto = v; }
    public void setEstado(String v)         { this.estado = v; }
    public void setCdrCodigo(String v)      { this.cdrCodigo = v; }
    public void setCdrDescripcion(String v) { this.cdrDescripcion = v; }
    public void setTicketSunat(String v)    { this.ticketSunat = v; }
    public void setAceptado(boolean v)      { this.aceptado = v; }
    public void setMensajeError(String v)   { this.mensajeError = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long    comprobanteId;
        private String  rucEmisor;
        private String  tipo;
        private String  serie;
        private String  correlativo;
        private String  numeroCompleto;
        private String  estado;
        private String  cdrCodigo;
        private String  cdrDescripcion;
        private String  ticketSunat;
        private boolean aceptado;
        private String  mensajeError;

        public Builder comprobanteId(Long v)    { this.comprobanteId = v;  return this; }
        public Builder rucEmisor(String v)      { this.rucEmisor = v;      return this; }
        public Builder tipo(String v)           { this.tipo = v;           return this; }
        public Builder serie(String v)          { this.serie = v;          return this; }
        public Builder correlativo(String v)    { this.correlativo = v;    return this; }
        public Builder numeroCompleto(String v) { this.numeroCompleto = v; return this; }
        public Builder estado(String v)         { this.estado = v;         return this; }
        public Builder cdrCodigo(String v)      { this.cdrCodigo = v;      return this; }
        public Builder cdrDescripcion(String v) { this.cdrDescripcion = v; return this; }
        public Builder ticketSunat(String v)    { this.ticketSunat = v;    return this; }
        public Builder aceptado(boolean v)      { this.aceptado = v;       return this; }
        public Builder mensajeError(String v)   { this.mensajeError = v;   return this; }

        public EmisionResponse build() {
            EmisionResponse r = new EmisionResponse();
            r.comprobanteId  = comprobanteId;
            r.rucEmisor      = rucEmisor;
            r.tipo           = tipo;
            r.serie          = serie;
            r.correlativo    = correlativo;
            r.numeroCompleto = numeroCompleto;
            r.estado         = estado;
            r.cdrCodigo      = cdrCodigo;
            r.cdrDescripcion = cdrDescripcion;
            r.ticketSunat    = ticketSunat;
            r.aceptado       = aceptado;
            r.mensajeError   = mensajeError;
            return r;
        }
    }
}
