package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CryptoService {
    private final SecretKey aesKey;
    private static final SecureRandom RAND = new SecureRandom();

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; // GCM std IV
            RAND.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            c.init(Cipher.ENCRYPT_MODE, aesKey, spec);
            byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String decrypt(String cipherB64) {
        try {
            byte[] all = Base64.getDecoder().decode(cipherB64);
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12);
            byte[] ct = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            c.init(Cipher.DECRYPT_MODE, aesKey, spec);
            byte[] pt = c.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
