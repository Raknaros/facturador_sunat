package com.tuempresa.facturador.internal.repository;

import com.tuempresa.facturador.internal.entity.Contribuyente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ContribuyenteRepository extends JpaRepository<Contribuyente, String> {

    Optional<Contribuyente> findByRucAndActivoTrue(String ruc);

    @Modifying
    @Query("UPDATE Contribuyente c SET c.greToken = :token, c.greTokenExpira = :expira WHERE c.ruc = :ruc")
    void actualizarTokenGre(String ruc, String token, LocalDateTime expira);
}
