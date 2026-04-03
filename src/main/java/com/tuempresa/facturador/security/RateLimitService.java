package com.tuempresa.facturador.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting en memoria por IP para el endpoint de login.
 * Máximo 12 intentos por IP en una ventana deslizante de 1 hora.
 * El contador se resetea al login exitoso y al reiniciar la aplicación.
 */
@Service
public class RateLimitService {

    private static final int  MAX_INTENTOS = 12;
    private static final long VENTANA_MS   = 3_600_000L; // 1 hora

    private final ConcurrentHashMap<String, Deque<Long>> intentos = new ConcurrentHashMap<>();

    /**
     * Registra un intento para la IP dada.
     * @return true si se permite el intento, false si superó el límite.
     */
    public boolean permitir(String ip) {
        long ahora = Instant.now().toEpochMilli();
        Deque<Long> timestamps = intentos.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Limpiar timestamps fuera de la ventana
            while (!timestamps.isEmpty() && ahora - timestamps.peekFirst() > VENTANA_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_INTENTOS) {
                return false;
            }
            timestamps.addLast(ahora);
            return true;
        }
    }

    /**
     * Resetear contador al login exitoso para no penalizar IPs legítimas.
     */
    public void resetear(String ip) {
        intentos.remove(ip);
    }

    public int intentosRestantes(String ip) {
        long ahora = Instant.now().toEpochMilli();
        Deque<Long> timestamps = intentos.get(ip);
        if (timestamps == null) return MAX_INTENTOS;

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && ahora - timestamps.peekFirst() > VENTANA_MS) {
                timestamps.pollFirst();
            }
            return Math.max(0, MAX_INTENTOS - timestamps.size());
        }
    }
}
