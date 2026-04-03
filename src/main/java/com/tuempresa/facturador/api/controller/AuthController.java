package com.tuempresa.facturador.api.controller;

import com.tuempresa.facturador.api.dto.LoginRequest;
import com.tuempresa.facturador.api.dto.LoginResponse;
import com.tuempresa.facturador.internal.entity.Contribuyente;
import com.tuempresa.facturador.internal.repository.ContribuyenteRepository;
import com.tuempresa.facturador.security.EncryptionService;
import com.tuempresa.facturador.security.JwtService;
import com.tuempresa.facturador.security.RateLimitService;
import com.tuempresa.facturador.sunat.service.SunatSenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Login con credenciales SOL — retorna Bearer JWT")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ContribuyenteRepository contribuyenteRepo;
    private final EncryptionService       encryptionService;
    private final JwtService              jwtService;
    private final RateLimitService        rateLimitService;
    private final SunatSenderService      sunatSenderService;
    private final long                    expirationMs;
    private final boolean                 validarCredsEnLogin;

    public AuthController(ContribuyenteRepository contribuyenteRepo,
                          EncryptionService encryptionService,
                          JwtService jwtService,
                          RateLimitService rateLimitService,
                          SunatSenderService sunatSenderService,
                          @Value("${jwt.expiration-ms}") long expirationMs,
                          @Value("${sunat.validar-creds-en-login:true}") boolean validarCredsEnLogin) {
        this.contribuyenteRepo   = contribuyenteRepo;
        this.encryptionService   = encryptionService;
        this.jwtService          = jwtService;
        this.rateLimitService    = rateLimitService;
        this.sunatSenderService  = sunatSenderService;
        this.expirationMs        = expirationMs;
        this.validarCredsEnLogin = validarCredsEnLogin;
    }

    @Operation(summary = "Login",
               description = "Autentica con RUC + credenciales SOL. Si validar-creds-en-login=true " +
                             "(defecto), hace un ping a SUNAT para confirmar que las credenciales son válidas. " +
                             "Retorna Bearer JWT con vigencia de 12 horas.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest httpReq) {

        String ip = obtenerIp(httpReq);

        // 1. Rate limiting
        if (!rateLimitService.permitir(ip)) {
            log.warn("[AUTH] Rate limit alcanzado para IP: {}", ip);
            return ResponseEntity.status(429).body(
                    Map.of("error", "Demasiados intentos. Espere antes de intentar de nuevo.",
                           "intentosRestantes", 0));
        }

        // 2. Buscar contribuyente
        Contribuyente c = contribuyenteRepo.findByRucAndActivoTrue(req.getRuc()).orElse(null);
        if (c == null) {
            log.warn("[AUTH] RUC no encontrado o inactivo: {}", req.getRuc());
            return ResponseEntity.status(401).body(
                    Map.of("error", "RUC no registrado en el sistema",
                           "intentosRestantes", rateLimitService.intentosRestantes(ip)));
        }

        // 3. Validar usuario SOL (comparación local, case-insensitive)
        if (!c.getUsuarioSol().equalsIgnoreCase(req.getUsuarioSol())) {
            log.warn("[AUTH] Usuario SOL incorrecto para RUC: {}", req.getRuc());
            return ResponseEntity.status(401).body(
                    Map.of("error", "Credenciales incorrectas",
                           "intentosRestantes", rateLimitService.intentosRestantes(ip)));
        }

        // 4. Validar contraseña SOL (descifrar de BD y comparar)
        String passwordAlmacenada = encryptionService.decryptText(c.getPasswordSolEnc());
        if (!passwordAlmacenada.equals(req.getPasswordSol())) {
            log.warn("[AUTH] Contraseña SOL incorrecta para RUC: {}", req.getRuc());
            return ResponseEntity.status(401).body(
                    Map.of("error", "Credenciales incorrectas",
                           "intentosRestantes", rateLimitService.intentosRestantes(ip)));
        }

        // 5. Ping a SUNAT para confirmar que las credenciales son válidas en la fuente de verdad
        if (validarCredsEnLogin) {
            try {
                boolean credsValidas = sunatSenderService.validarCredencialesSOL(
                        req.getUsuarioSol(), req.getPasswordSol());
                if (!credsValidas) {
                    log.warn("[AUTH] SUNAT rechazó las credenciales SOL para RUC: {}", req.getRuc());
                    return ResponseEntity.status(401).body(
                            Map.of("error", "SUNAT rechazó las credenciales SOL. " +
                                            "Verifique usuario y contraseña SOL.",
                                   "intentosRestantes", rateLimitService.intentosRestantes(ip)));
                }
            } catch (RuntimeException e) {
                log.error("[AUTH] No se pudo conectar con SUNAT para validar credenciales: {}", e.getMessage());
                return ResponseEntity.status(503).body(
                        Map.of("error", "No se pudo verificar credenciales contra SUNAT. " +
                                        "Intente más tarde.",
                               "detalle", e.getMessage()));
            }
        }

        // 6. Login exitoso — emitir JWT y resetear rate limit
        rateLimitService.resetear(ip);
        String token = jwtService.generarToken(c.getRuc(), c.getUsuarioSol());
        long expiraEnMs = Instant.now().toEpochMilli() + expirationMs;
        String expiraEnIso = Instant.ofEpochMilli(expiraEnMs)
                .atZone(ZoneId.of("America/Lima"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        log.info("[AUTH] Login exitoso — RUC: {} | usuario: {} | IP: {}",
                c.getRuc(), c.getUsuarioSol(), ip);

        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .ruc(c.getRuc())
                .razonSocial(c.getRazonSocial())
                .solUser(c.getUsuarioSol())
                .expiraEn(expiraEnIso)
                .expiraEnMs(expiraEnMs)
                .build());
    }

    private String obtenerIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
