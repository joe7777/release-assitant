package com.example.mcpknowledgerag.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashingUtils {

    private HashingUtils() {
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        normalized = normalized.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("[\\t ]+", " ");
        normalized = normalized.replaceAll("\n{2,}", "\n");
        return normalized.trim();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
