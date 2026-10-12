package com.lanchat.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** SHA-256 password hashing with a random salt. Stored format: saltHex$hashHex */
public final class PasswordUtil {

    private PasswordUtil() { }

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        HexFormat hex = HexFormat.of();
        return hex.formatHex(salt) + "$" + hex.formatHex(sha256(salt, password));
    }

    public static boolean verify(String password, String stored) {
        if (stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 2) return false;
        HexFormat hex = HexFormat.of();
        byte[] salt = hex.parseHex(parts[0]);
        byte[] expected = hex.parseHex(parts[1]);
        return MessageDigest.isEqual(expected, sha256(salt, password));
    }

    private static byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
