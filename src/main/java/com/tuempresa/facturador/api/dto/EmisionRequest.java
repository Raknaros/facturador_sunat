package com.tuempresa.facturador.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EmisionRequest {

    @NotBlank @Size(min = 4, max = 4)
    private String serie;

    @NotNull
    private LocalDate fechaEmision;

    @NotBlank @Size(min = 3, max = 3)
    private String moneda;

    private BigDecimal tipoCambio;

    @NotNull @Valid
    private ReceptorDto receptor;

    @NotEmpty @Valid
    private List<ItemDto> items;

    private String observaciones;

    /** "Contado" (default) o "Credito" — Resolución SUNAT 000193-2020 */
    private String formaPago;

    @Valid
    private List<CuotaDto> cuotas = new ArrayList<>();

    public EmisionRequest() {}

    public String      getSerie()        { return serie; }
    public LocalDate   getFechaEmision() { return fechaEmision; }
    public String      getMoneda()       { return moneda; }
    public BigDecimal  getTipoCambio()   { return tipoCambio; }
    public ReceptorDto getReceptor()     { return receptor; }
    public List<ItemDto> getItems()      { return items; }
    public String      getObservaciones(){ return observaciones; }
    public String      getFormaPago()    { return formaPago; }
    public List<CuotaDto> getCuotas()    { return cuotas; }

    public void setSerie(String v)            { this.serie = v; }
    public void setFechaEmision(LocalDate v)  { this.fechaEmision = v; }
    public void setMoneda(String v)           { this.moneda = v; }
    public void setTipoCambio(BigDecimal v)   { this.tipoCambio = v; }
    public void setReceptor(ReceptorDto v)    { this.receptor = v; }
    public void setItems(List<ItemDto> v)     { this.items = v; }
    public void setObservaciones(String v)    { this.observaciones = v; }
    public void setFormaPago(String v)        { this.formaPago = v; }
    public void setCuotas(List<CuotaDto> v)   { this.cuotas = v; }

    // ── Nested DTOs ────────────────────────────────────────────

    public static class CuotaDto {
        @NotNull @DecimalMin("0.01")
        private BigDecimal monto;

        @NotNull
        private LocalDate fechaVencimiento;

        public CuotaDto() {}

        public CuotaDto(BigDecimal monto, LocalDate fechaVencimiento) {
            this.monto            = monto;
            this.fechaVencimiento = fechaVencimiento;
        }

        public BigDecimal getMonto()            { return monto; }
        public LocalDate  getFechaVencimiento() { return fechaVencimiento; }
        public void setMonto(BigDecimal v)            { this.monto = v; }
        public void setFechaVencimiento(LocalDate v)  { this.fechaVencimiento = v; }
    }

    public static class ReceptorDto {
        @NotBlank private String tipoDocumento;
        @NotBlank private String nroDocumento;
        @NotBlank private String razonSocial;
        private String direccion;

        public ReceptorDto() {}

        public String getTipoDocumento() { return tipoDocumento; }
        public String getNroDocumento()  { return nroDocumento; }
        public String getRazonSocial()   { return razonSocial; }
        public String getDireccion()    { return direccion; }

        public void setTipoDocumento(String v) { this.tipoDocumento = v; }
        public void setNroDocumento(String v)  { this.nroDocumento = v; }
        public void setRazonSocial(String v)   { this.razonSocial = v; }
        public void setDireccion(String v)    { this.direccion = v; }
    }

    public static class ItemDto {
        private String codigoProducto;
        @NotBlank private String descripcion;
        private String unidadMedida;
        @NotNull @DecimalMin("0.0001") private BigDecimal cantidad;
        @NotNull @DecimalMin("0.00")   private BigDecimal precioUnitario;
        private String tipoAfectacionIgv;
        private BigDecimal descuento;

        public ItemDto() {}

        public String     getCodigoProducto()   { return codigoProducto; }
        public String     getDescripcion()       { return descripcion; }
        public String     getUnidadMedida()      { return unidadMedida; }
        public BigDecimal getCantidad()          { return cantidad; }
        public BigDecimal getPrecioUnitario()    { return precioUnitario; }
        public String     getTipoAfectacionIgv() { return tipoAfectacionIgv; }
        public BigDecimal getDescuento()        { return descuento; }

        public void setCodigoProducto(String v)     { this.codigoProducto = v; }
        public void setDescripcion(String v)         { this.descripcion = v; }
        public void setUnidadMedida(String v)        { this.unidadMedida = v; }
        public void setCantidad(BigDecimal v)        { this.cantidad = v; }
        public void setPrecioUnitario(BigDecimal v)  { this.precioUnitario = v; }
        public void setTipoAfectacionIgv(String v)   { this.tipoAfectacionIgv = v; }
        public void setDescuento(BigDecimal v)      { this.descuento = v; }
    }
}
