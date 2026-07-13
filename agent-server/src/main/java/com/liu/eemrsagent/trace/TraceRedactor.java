package com.liu.eemrsagent.trace;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TraceRedactor {

    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[0-9Xx]\\b");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern AUTH = Pattern.compile("(?i)(Authorization\\s*[:=]\\s*)(Bearer\\s+)?[A-Za-z0-9._~+/=-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)((api[-_]?key|token|cookie|password|secret)\\s*[:=]\\s*)[^,;\\s}\\\"]+");
    private static final Pattern PATIENT_ID = Pattern.compile("(?i)((patientId|doctorId|idNumber)\\s*[:=]\\s*)[^,;\\s}\\\"]+");

    public String redact(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        result = ID_CARD.matcher(result).replaceAll("[REDACTED_ID_CARD]");
        result = MOBILE.matcher(result).replaceAll("[REDACTED_MOBILE]");
        result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = AUTH.matcher(result).replaceAll("$1[REDACTED_AUTH]");
        result = API_KEY.matcher(result).replaceAll("$1[REDACTED_SECRET]");
        result = PATIENT_ID.matcher(result).replaceAll("$1[REDACTED_ID]");
        return result;
    }

    public String hashUserId(String raw, String salt) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "sha256:" + sha256((salt == null ? "" : salt) + ":" + raw.trim().toLowerCase(Locale.ROOT));
    }

    public String stableHash(String raw) {
        if (raw == null) {
            return null;
        }
        return "sha256:" + sha256(raw);
    }

    public String redactMapSummary(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return redact(values.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
