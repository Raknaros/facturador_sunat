package com.tuempresa.facturador.internal.service;

import com.tuempresa.facturador.api.dto.EmisionResponse;
import com.tuempresa.facturador.internal.entity.Comprobante;
import com.tuempresa.facturador.internal.entity.SerieCorrelativo;
import com.tuempresa.facturador.internal.repository.ComprobanteRepository;
import com.tuempresa.facturador.internal.repository.SerieCorrelativoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    private final ComprobanteRepository      comprobanteRepo;
    private final SerieCorrelativoRepository serieRepo;

    /**
     * Genera y reserva el siguiente correlativo para la serie.
     * Usa PESSIMISTIC_WRITE para evitar duplicados en concurrencia.
     */
    @Transactional
    public String siguienteCorrelativo(String ruc, String tipo, String serie) {
        SerieCorrelativo sc = serieRepo.findForUpdate(ruc, tipo, serie)
            .orElseGet(() -> SerieCorrelativo.builder()
                .rucEmisor(ruc)
                .tipo(tipo)
                .serie(serie)
                .ultimoNumero(0)
                .build());

        sc.setUltimoNumero(sc.getUltimoNumero() + 1);
        serieRepo.save(sc);

        return String.format("%08d", sc.getUltimoNumero());
    }

    /**
     * Inicializa (o sobreescribe) el último correlativo de una serie.
     * Uso exclusivo para migración desde otro facturador.
     * El siguiente comprobante emitido tendrá el número ultimoNumero + 1.
     */
    @Transactional
    public void inicializarCorrelativo(String ruc, String tipo, String serie, int ultimoNumero) {
        if (ultimoNumero < 0) {
            throw new IllegalArgumentException("ultimoNumero no puede ser negativo");
        }
        SerieCorrelativo sc = serieRepo.findForUpdate(ruc, tipo, serie)
            .orElseGet(() -> SerieCorrelativo.builder()
                .rucEmisor(ruc)
                .tipo(tipo)
                .serie(serie)
                .ultimoNumero(0)
                .build());
        sc.setUltimoNumero(ultimoNumero);
        serieRepo.save(sc);
        log.info("Correlativo inicializado: RUC={} tipo={} serie={} ultimoNumero={}",
            ruc, tipo, serie, ultimoNumero);
    }

    /**
     * Persiste el comprobante y propaga el ID generado al response.
     */
    @Transactional
    public void registrar(String ruc, String tipo, String serie, String correlativo, EmisionResponse resp) {
        Comprobante comp = Comprobante.builder()
            .rucEmisor(ruc)
            .tipo(Comprobante.TipoComprobante.valueOf(tipo))
            .serie(serie)
            .correlativo(correlativo)
            .fechaEmision(LocalDate.now())
            .estado(determinarEstado(resp))
            .cdrCodigo(resp.getCdrCodigo())
            .cdrDescripcion(resp.getCdrDescripcion())
            .ticketSunat(resp.getTicketSunat())
            .build();

        Comprobante guardado = comprobanteRepo.save(comp);
        resp.setComprobanteId(guardado.getId());
        log.info("Comprobante registrado ID={} | {}-{}", guardado.getId(), serie, correlativo);
    }

    @Transactional(readOnly = true)
    public Comprobante buscarPorId(String ruc, Long id) {
        return comprobanteRepo.findById(id)
            .filter(c -> c.getRucEmisor().equals(ruc))
            .orElseThrow(() -> new RuntimeException("Comprobante no encontrado: " + id));
    }

    private Comprobante.EstadoComprobante determinarEstado(EmisionResponse resp) {
        if (resp.getTicketSunat() != null) return Comprobante.EstadoComprobante.EN_PROCESO;
        if (resp.isAceptado())              return Comprobante.EstadoComprobante.ACEPTADO;
        if (resp.getMensajeError() != null) return Comprobante.EstadoComprobante.ERROR;
        return Comprobante.EstadoComprobante.RECHAZADO;
    }
}
