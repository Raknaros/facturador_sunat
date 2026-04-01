package com.tuempresa.facturador.api.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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
}
