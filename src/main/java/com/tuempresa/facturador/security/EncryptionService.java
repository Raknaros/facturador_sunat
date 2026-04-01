package com.tuempresa.facturador.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servicio de encriptación AES-256-GCM.
 * Usado para proteger credenciales SOL y certificados digitales en la DB.
 *
 * Archivo: src/main/java/com/tuempresa/facturador/security/EncryptionService.java
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_LENGTH = 128;
    private static final int    IV_LENGTH    = 12; // 96 bits recomendado para GCM

    @Value("${encryption.secret-key}")
    private String secretKeyBase64; // 32 chars en application.yml → se usa como clave AES-256

    // ─────────────────────────────────────────────
    // Encriptar texto (ej: password SOL)
    // ─────────────────────────────────────────────

    /**
     * Encripta un String plano.
     * @return Base64(IV + ciphertext) para almacenar en DB
     */
    public String encryptText(String plainText) {
        try {
            byte[] iv         = generateIv();
            Cipher cipher     = buildCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted  = cipher.doFinal(plainText.getBytes("UTF-8"));

            // Concatenar IV + encrypted → Base64
            byte[] combined = concat(iv, encrypted);
            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("Error encriptando texto", e);
        }
    }

    /**
     * Desencripta un String previamente encriptado con encryptText().
     */
    public String decryptText(String encryptedBase64) {
        try {
            byte[] combined  = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv        = extract(combined, 0, IV_LENGTH);
            byte[] encrypted = extract(combined, IV_LENGTH, combined.length - IV_LENGTH);

            Cipher cipher    = buildCipher(Cipher.DECRYPT_MODE, iv);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, "UTF-8");

        } catch (Exception e) {
            throw new RuntimeException("Error desencriptando texto", e);
        }
    }

    // ─────────────────────────────────────────────
    // Encriptar bytes (ej: archivo .p12)
    // ─────────────────────────────────────────────

    /**
     * Encripta un byte[] (contenido del .p12).
     * @return Base64(IV + ciphertext) para almacenar en DB
     */
    public String encryptBytes(byte[] plainBytes) {
        try {
            byte[] iv         = generateIv();
            Cipher cipher     = buildCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted  = cipher.doFinal(plainBytes);

            byte[] combined = concat(iv, encrypted);
            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("Error encriptando bytes", e);
        }
    }

    /**
     * Desencripta hacia byte[] (para reconstruir el .p12 en memoria).
     */
    public byte[] decryptBytes(String encryptedBase64) {
        try {
            byte[] combined  = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv        = extract(combined, 0, IV_LENGTH);
            byte[] encrypted = extract(combined, IV_LENGTH, combined.length - IV_LENGTH);

            Cipher cipher    = buildCipher(Cipher.DECRYPT_MODE, iv);
            return cipher.doFinal(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Error desencriptando bytes", e);
        }
    }

    // ─────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────

    private Cipher buildCipher(int mode, byte[] iv) throws Exception {
        SecretKey key    = new SecretKeySpec(secretKeyBase64.getBytes("UTF-8"), "AES");
        Cipher cipher    = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(mode, key, spec);
        return cipher;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0,        a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private byte[] extract(byte[] src, int offset, int length) {
        byte[] dest = new byte[length];
        System.arraycopy(src, offset, dest, 0, length);
        return dest;
    }
}
