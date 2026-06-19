package com.liu.eemrsagent.reporttrend;

import java.time.LocalDate;
import java.util.List;

public interface LabReportCipherRepository {
    List<EncryptedLabReportRecord> findEncryptedReports(String patientId, String reportType, LocalDate startDate, LocalDate endDate);
}
