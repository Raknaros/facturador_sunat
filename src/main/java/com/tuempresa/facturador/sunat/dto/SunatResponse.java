package com.tuempresa.facturador.sunat.dto;

public class SunatResponse {

    private boolean aceptado;
    private String  codigoRespuesta;
    private String  descripcionRespuesta;
    private String  xmlCdr;
    private String  mensajeError;

    public SunatResponse() {}

    public SunatResponse(boolean aceptado, String codigoRespuesta,
                         String descripcionRespuesta, String xmlCdr, String mensajeError) {
        this.aceptado             = aceptado;
        this.codigoRespuesta      = codigoRespuesta;
        this.descripcionRespuesta = descripcionRespuesta;
        this.xmlCdr               = xmlCdr;
        this.mensajeError         = mensajeError;
    }

    public boolean isAceptado()              { return aceptado; }
    public String  getCodigoRespuesta()      { return codigoRespuesta; }
    public String  getDescripcionRespuesta() { return descripcionRespuesta; }
    public String  getXmlCdr()              { return xmlCdr; }
    public String  getMensajeError()         { return mensajeError; }

    public void setAceptado(boolean v)             { this.aceptado = v; }
    public void setCodigoRespuesta(String v)       { this.codigoRespuesta = v; }
    public void setDescripcionRespuesta(String v)  { this.descripcionRespuesta = v; }
    public void setXmlCdr(String v)               { this.xmlCdr = v; }
    public void setMensajeError(String v)          { this.mensajeError = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean aceptado;
        private String  codigoRespuesta;
        private String  descripcionRespuesta;
        private String  xmlCdr;
        private String  mensajeError;

        public Builder aceptado(boolean v)             { this.aceptado = v;             return this; }
        public Builder codigoRespuesta(String v)       { this.codigoRespuesta = v;      return this; }
        public Builder descripcionRespuesta(String v)  { this.descripcionRespuesta = v; return this; }
        public Builder xmlCdr(String v)               { this.xmlCdr = v;               return this; }
        public Builder mensajeError(String v)          { this.mensajeError = v;         return this; }

        public SunatResponse build() {
            return new SunatResponse(aceptado, codigoRespuesta, descripcionRespuesta, xmlCdr, mensajeError);
        }
    }
}
