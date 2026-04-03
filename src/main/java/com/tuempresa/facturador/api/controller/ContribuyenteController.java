package com.tuempresa.facturador.api.controller;

import com.tuempresa.facturador.api.dto.RegistroContribuyenteRequest;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import com.tuempresa.facturador.security.EncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/contribuyentes")
@Tag(name = "Contribuyentes", description = "Registro y gestión de empresas emisoras")
public class ContribuyenteController {

    private final ContribuyenteRepository contribuyenteRepo;
    private final EncryptionService       encryptionService;

    public ContribuyenteController(ContribuyenteRepository contribuyenteRepo,
                                   EncryptionService encryptionService) {
        this.contribuyenteRepo = contribuyenteRepo;
        this.encryptionService = encryptionService;
    }

    @PostMapping
    @Operation(summary = "Registrar contribuyente y subir credenciales")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroContribuyenteRequest req) {

        if (contribuyenteRepo.existsById(req.getRuc())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "RUC ya registrado: " + req.getRuc()));
        }

        byte[] p12Bytes = Base64.getDecoder().decode(req.getCertificadoP12Base64());

        Contribuyente c = Contribuyente.builder()
            .ruc(req.getRuc())
            .razonSocial(req.getRazonSocial())
            .nombreComercial(req.getNombreComercial())
            .direccion(req.getDireccion())
            .ubigeo(req.getUbigeo())
            .departamento(req.getDepartamento())
            .provincia(req.getProvincia())
            .distrito(req.getDistrito())
            .usuarioSol(req.getUsuarioSol())
            .passwordSolEnc(encryptionService.encryptText(req.getPasswordSol()))
            .certificadoP12Enc(encryptionService.encryptBytes(p12Bytes))
            .certPasswordEnc(encryptionService.encryptText(req.getCertPassword()))
            .certVence(req.getCertVence())
            .greClientId(req.getGreClientId())
            .greClientSecretEnc(req.getGreClientSecret() != null
                ? encryptionService.encryptText(req.getGreClientSecret())
                : null)
            .activo(true)
            .build();

        contribuyenteRepo.save(c);

        return ResponseEntity.ok(Map.of(
            "ruc", c.getRuc(),
            "razonSocial", c.getRazonSocial(),
            "mensaje", "Contribuyente registrado correctamente",
            "tieneGre", c.tieneCredsGre()
        ));
    }

    @PutMapping("/{ruc}/certificado")
    @Operation(summary = "Renovar certificado digital")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> renovarCertificado(
            @PathVariable String ruc,
            @RequestParam String certificadoP12Base64,
            @RequestParam String certPassword,
            @RequestParam String certVence) {

        return contribuyenteRepo.findByRucAndActivoTrue(ruc).map(c -> {
            byte[] p12Bytes = Base64.getDecoder().decode(certificadoP12Base64);
            c.setCertificadoP12Enc(encryptionService.encryptBytes(p12Bytes));
            c.setCertPasswordEnc(encryptionService.encryptText(certPassword));
            c.setCertVence(LocalDate.parse(certVence));
            contribuyenteRepo.save(c);
            return ResponseEntity.ok(Map.of("mensaje", "Certificado actualizado"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{ruc}/gre-credenciales")
    @Operation(summary = "Configurar credenciales GRE (client_id / client_secret)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> actualizarCredsGre(
            @PathVariable String ruc,
            @RequestParam String clientId,
            @RequestParam String clientSecret) {

        return contribuyenteRepo.findByRucAndActivoTrue(ruc).map(c -> {
            c.setGreClientId(clientId);
            c.setGreClientSecretEnc(encryptionService.encryptText(clientSecret));
            c.setGreToken(null);
            c.setGreTokenExpira(null);
            contribuyenteRepo.save(c);
            return ResponseEntity.ok(Map.of("mensaje", "Credenciales GRE actualizadas"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
