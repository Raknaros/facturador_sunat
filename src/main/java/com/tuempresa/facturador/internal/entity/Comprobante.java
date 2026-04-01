package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "comprobantes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ruc_emisor", "serie", "correlativo"}),
    indexes = {
        @Index(name = "idx_comp_ruc",    columnList = "ruc_emisor"),
        @Index(name = "idx_comp_estado", columnList = "estado"),
        @Index(name = "idx_comp_ticket", columnList = "ticket_sunat")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ruc_emisor", nullable = false, length = 11)
    private String rucEmisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoComprobante tipo;

    @Column(name = "serie", nullable = false, length = 4)
    private String serie;

    @Column(name = "correlativo", nullable = false, length = 8)
    private String correlativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoComprobante estado = EstadoComprobante.PENDIENTE;

    @Column(name = "ticket_sunat", length = 50)
    private String ticketSunat;

    @Column(name = "cdr_codigo", length = 10)
    private String cdrCodigo;

    @Column(name = "cdr_descripcion", length = 500)
    private String cdrDescripcion;

    @Column(name = "hash_xml", length = 100)
    private String hashXml;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { this.createdAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate()  { this.updatedAt = LocalDateTime.now(); }

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO, NOTA_DEBITO,
        GRE_REMITENTE, GRE_TRANSPORTISTA
    }

    public enum EstadoComprobante {
        PENDIENTE, ENVIADO, EN_PROCESO, ACEPTADO, RECHAZADO, ERROR
    }
}
