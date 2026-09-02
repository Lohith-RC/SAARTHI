package com.saarthi.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time token comparison utility to avoid timing side-channels when
 * authenticating device and operator tokens.
 */
public final class TokenDecoding {

    private TokenDecoding() {
    }

    /**
     * Compares two secrets in constant (best-effort) time without leaking
     * content length or byte positions. Treats a null expected value as "never
     * matches" so that misconfiguration fails closed rather than authenticating.
     */
    public static boolean constantTimeEquals(String expected, String candidate) {
        if (expected == null || expected.isBlank() || candidate == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, candidateBytes);
    }
}