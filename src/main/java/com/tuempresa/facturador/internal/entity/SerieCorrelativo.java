package com.tuempresa.facturador.internal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "series_correlativos",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ruc_emisor", "tipo", "serie"}))
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

    public SerieCorrelativo() {}

    public SerieCorrelativo(Long id, String rucEmisor, String tipo,
                            String serie, Integer ultimoNumero, Long version) {
        this.id           = id;
        this.rucEmisor    = rucEmisor;
        this.tipo         = tipo;
        this.serie        = serie;
        this.ultimoNumero = ultimoNumero;
        this.version      = version;
    }

    public Long    getId()           { return id; }
    public String  getRucEmisor()    { return rucEmisor; }
    public String  getTipo()         { return tipo; }
    public String  getSerie()        { return serie; }
    public Integer getUltimoNumero() { return ultimoNumero; }
    public Long    getVersion()      { return version; }

    public void setId(Long v)              { this.id = v; }
    public void setRucEmisor(String v)     { this.rucEmisor = v; }
    public void setTipo(String v)          { this.tipo = v; }
    public void setSerie(String v)         { this.serie = v; }
    public void setUltimoNumero(Integer v) { this.ultimoNumero = v; }
    public void setVersion(Long v)         { this.version = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long    id;
        private String  rucEmisor;
        private String  tipo;
        private String  serie;
        private Integer ultimoNumero = 0;
        private Long    version;

        public Builder id(Long v)              { this.id = v;           return this; }
        public Builder rucEmisor(String v)     { this.rucEmisor = v;    return this; }
        public Builder tipo(String v)          { this.tipo = v;         return this; }
        public Builder serie(String v)         { this.serie = v;        return this; }
        public Builder ultimoNumero(Integer v) { this.ultimoNumero = v; return this; }
        public Builder version(Long v)         { this.version = v;      return this; }

        public SerieCorrelativo build() {
            return new SerieCorrelativo(id, rucEmisor, tipo, serie, ultimoNumero, version);
        }
    }
}
