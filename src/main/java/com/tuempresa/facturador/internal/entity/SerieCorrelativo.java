package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "series_correlativos",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ruc_emisor", "tipo", "serie"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SerieCorrelativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruc_emisor", nullable = false, length = 11)
    private String rucEmisor;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "serie", nullable = false, length = 4)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero = 0;

    @Version
    private Long version;
}
