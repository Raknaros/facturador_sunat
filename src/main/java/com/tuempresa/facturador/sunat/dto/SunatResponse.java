package com.tuempresa.facturador.sunat.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SunatResponse {

    private boolean aceptado;
    private String  codigoRespuesta;
    private String  descripcionRespuesta;
    private String  xmlCdr;
    private String  mensajeError;
}
