package com.tuempresa.facturador.sunat.service;

import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import com.tuempresa.facturador.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.time.LocalDate;

/**
 * Carga el certificado digital (.p12) del contribuyente desde PostgreSQL.
 * El .p12 NUNCA toca el disco — solo vive en memoria durante la operación de firma.
 */
@Service
public class CertificadoService {

    private static final Logger log = LoggerFactory.getLogger(CertificadoService.class);

    private final ContribuyenteRepository contribuyenteRepo;
    private final EncryptionService       encryptionService;

    public CertificadoService(ContribuyenteRepository contribuyenteRepo,
                              EncryptionService encryptionService) {
        this.contribuyenteRepo = contribuyenteRepo;
        this.encryptionService = encryptionService;
    }

    /**
     * Devuelve un KeyStore PKCS12 listo para usar en la firma XMLDSig.
     */
    public KeyStore cargarKeyStore(String empresaRuc) {
        log.debug("Cargando certificado para RUC: {}", empresaRuc);

        Contribuyente c = contribuyenteRepo.findByRucAndActivoTrue(empresaRuc)
            .orElseThrow(() -> new CertificadoException(
                "No existe contribuyente activo para RUC: " + empresaRuc));

        if (c.getCertVence().isBefore(LocalDate.now())) {
            throw new CertificadoException(
                "Certificado digital vencido para RUC: " + empresaRuc +
                " | Venció: " + c.getCertVence());
        }

        try {
            byte[] p12Bytes = encryptionService.decryptBytes(c.getCertificadoP12Enc());
            String password = encryptionService.decryptText(c.getCertPasswordEnc());

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(p12Bytes), password.toCharArray());

            log.debug("Certificado cargado correctamente para RUC: {}", empresaRuc);
            return keyStore;

        } catch (CertificadoException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificadoException(
                "Error cargando certificado para RUC: " + empresaRuc + " → " + e.getMessage(), e);
        }
    }

    /**
     * Retorna la contraseña desencriptada del certificado.
     * Necesaria para acceder a la PrivateKey del KeyStore.
     */
    public String obtenerPassword(String empresaRuc) {
        Contribuyente c = contribuyenteRepo.findByRucAndActivoTrue(empresaRuc)
            .orElseThrow(() -> new CertificadoException(
                "No existe contribuyente activo para RUC: " + empresaRuc));
        return encryptionService.decryptText(c.getCertPasswordEnc());
    }

    /**
     * Retorna el alias del primer certificado encontrado en el KeyStore.
     */
    public String obtenerAlias(KeyStore keyStore) {
        try {
            return keyStore.aliases().nextElement();
        } catch (Exception e) {
            throw new CertificadoException("No se pudo obtener el alias del KeyStore", e);
        }
    }

    public static class CertificadoException extends RuntimeException {
        public CertificadoException(String msg)                { super(msg); }
        public CertificadoException(String msg, Throwable t)   { super(msg, t); }
    }
}
