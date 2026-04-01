package com.tuempresa.facturador.internal.repository;

import com.tuempresa.facturador.internal.entity.Comprobante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByRucEmisorAndSerieAndCorrelativo(
        String rucEmisor, String serie, String correlativo);

    Page<Comprobante> findByRucEmisorAndTipo(
        String rucEmisor, Comprobante.TipoComprobante tipo, Pageable pageable);

    List<Comprobante> findByEstado(Comprobante.EstadoComprobante estado);

    @Modifying
    @Query("UPDATE Comprobante c SET c.estado = :estado, c.cdrCodigo = :codigo, " +
           "c.cdrDescripcion = :descripcion WHERE c.ticketSunat = :ticket")
    void actualizarEstadoPorTicket(
        String ticket,
        Comprobante.EstadoComprobante estado,
        String codigo,
        String descripcion);
}
