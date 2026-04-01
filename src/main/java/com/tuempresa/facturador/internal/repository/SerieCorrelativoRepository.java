package com.tuempresa.facturador.internal.repository;

import com.tuempresa.facturador.internal.entity.SerieCorrelativo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SerieCorrelativoRepository extends JpaRepository<SerieCorrelativo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerieCorrelativo s WHERE s.rucEmisor = :ruc " +
           "AND s.tipo = :tipo AND s.serie = :serie")
    Optional<SerieCorrelativo> findForUpdate(String ruc, String tipo, String serie);
}
