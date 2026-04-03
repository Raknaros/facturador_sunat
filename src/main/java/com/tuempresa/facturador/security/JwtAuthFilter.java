package com.tuempresa.facturador.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT que intercepta cada request una vez.
 *
 * Lógica:
 * 1. Si no hay header Authorization → continúa (Spring Security rechazará si la ruta lo requiere)
 * 2. Valida el token JWT
 * 3. Extrae el RUC del claim "sub"
 * 4. Si la ruta es /api/{ruc}/..., verifica que el RUC del path coincida con el del token
 * 5. Registra la autenticación en el SecurityContext
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.esValido(token)) {
            escribirError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        }

        Claims claims = jwtService.validarYExtraer(token);
        String rucToken = claims.getSubject();

        // Validar que el RUC del path coincida con el del token en rutas /api/{ruc}/...
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        // parts: ["", "api", "{ruc-o-recurso}", ...]
        if (parts.length > 2 && parts[2].matches("\\d{11}")) {
            if (!rucToken.equals(parts[2])) {
                escribirError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Token no autorizado para el RUC " + parts[2]);
                return;
            }
        }

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(rucToken, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void escribirError(HttpServletResponse response, int status, String mensaje)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
