package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;
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

    public Comprobante() {}

    @PrePersist  protected void onCreate() { this.createdAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate()  { this.updatedAt = LocalDateTime.now(); }

    public Long              getId()             { return id; }
    public String            getRucEmisor()      { return rucEmisor; }
    public TipoComprobante   getTipo()           { return tipo; }
    public String            getSerie()          { return serie; }
    public String            getCorrelativo()    { return correlativo; }
    public EstadoComprobante getEstado()         { return estado; }
    public String            getTicketSunat()    { return ticketSunat; }
    public String            getCdrCodigo()      { return cdrCodigo; }
    public String            getCdrDescripcion() { return cdrDescripcion; }
    public String            getHashXml()        { return hashXml; }
    public LocalDate         getFechaEmision()   { return fechaEmision; }
    public LocalDateTime     getCreatedAt()      { return createdAt; }
    public LocalDateTime     getUpdatedAt()      { return updatedAt; }

    public void setId(Long v)                         { this.id = v; }
    public void setRucEmisor(String v)                { this.rucEmisor = v; }
    public void setTipo(TipoComprobante v)            { this.tipo = v; }
    public void setSerie(String v)                    { this.serie = v; }
    public void setCorrelativo(String v)              { this.correlativo = v; }
    public void setEstado(EstadoComprobante v)        { this.estado = v; }
    public void setTicketSunat(String v)              { this.ticketSunat = v; }
    public void setCdrCodigo(String v)                { this.cdrCodigo = v; }
    public void setCdrDescripcion(String v)           { this.cdrDescripcion = v; }
    public void setHashXml(String v)                  { this.hashXml = v; }
    public void setFechaEmision(LocalDate v)          { this.fechaEmision = v; }
    public void setCreatedAt(LocalDateTime v)         { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v)         { this.updatedAt = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long              id;
        private String            rucEmisor;
        private TipoComprobante   tipo;
        private String            serie;
        private String            correlativo;
        private EstadoComprobante estado = EstadoComprobante.PENDIENTE;
        private String            ticketSunat;
        private String            cdrCodigo;
        private String            cdrDescripcion;
        private String            hashXml;
        private LocalDate         fechaEmision;

        public Builder id(Long v)                         { this.id = v;             return this; }
        public Builder rucEmisor(String v)                { this.rucEmisor = v;      return this; }
        public Builder tipo(TipoComprobante v)            { this.tipo = v;           return this; }
        public Builder serie(String v)                    { this.serie = v;          return this; }
        public Builder correlativo(String v)              { this.correlativo = v;    return this; }
        public Builder estado(EstadoComprobante v)        { this.estado = v;         return this; }
        public Builder ticketSunat(String v)              { this.ticketSunat = v;    return this; }
        public Builder cdrCodigo(String v)                { this.cdrCodigo = v;      return this; }
        public Builder cdrDescripcion(String v)           { this.cdrDescripcion = v; return this; }
        public Builder hashXml(String v)                  { this.hashXml = v;        return this; }
        public Builder fechaEmision(LocalDate v)          { this.fechaEmision = v;   return this; }

        public Comprobante build() {
            Comprobante c = new Comprobante();
            c.id             = id;
            c.rucEmisor      = rucEmisor;
            c.tipo           = tipo;
            c.serie          = serie;
            c.correlativo    = correlativo;
            c.estado         = estado;
            c.ticketSunat    = ticketSunat;
            c.cdrCodigo      = cdrCodigo;
            c.cdrDescripcion = cdrDescripcion;
            c.hashXml        = hashXml;
            c.fechaEmision   = fechaEmision;
            return c;
        }
    }

    public enum TipoComprobante {
        FACTURA, BOLETA, NOTA_CREDITO, NOTA_DEBITO,
        GRE_REMITENTE, GRE_TRANSPORTISTA
    }

    public enum EstadoComprobante {
        PENDIENTE, ENVIADO, EN_PROCESO, ACEPTADO, RECHAZADO, ERROR
    }
}
