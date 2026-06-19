package com.liu.eemrsagent.reporttrend;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LocalReportDecryptor {
    public String decrypt(EncryptedLabReportRecord record) {
        if (record == null || record.ciphertext() == null || record.ciphertext().isBlank()) {
            throw new ReportTrendException(ReportTrendErrorCode.REPORT_DECRYPT_FAILED, "Empty report ciphertext");
        }
        String value = record.ciphertext().trim();
        if (value.startsWith("plain:")) {
            return value.substring("plain:".length());
        }
        if (value.startsWith("base64:")) {
            try {
                return new String(Base64.getDecoder().decode(value.substring("base64:".length())), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new ReportTrendException(ReportTrendErrorCode.REPORT_DECRYPT_FAILED, "Invalid local encrypted report payload", e);
            }
        }
        return value;
    }
}
