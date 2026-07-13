package com.liu.eemrsagent.reporttrend;

import java.time.LocalDate;

public record EncryptedLabReportRecord(
        String reportId,
        LocalDate reportDate,
        String reportType,
        String ciphertext
) {
}
