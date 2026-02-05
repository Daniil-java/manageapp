package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CodeVerifierUtil {
    private static final SecureRandom RAND = new SecureRandom();

    public static String generateVerifier() {
        byte[] bytes = new byte[32];
        RAND.nextBytes(bytes);
        return b64UrlNoPad(bytes);
    }

    public static String toS256Challenge(String verifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return b64UrlNoPad(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String b64UrlNoPad(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
