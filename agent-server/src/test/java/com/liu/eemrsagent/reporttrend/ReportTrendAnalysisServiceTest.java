package com.liu.eemrsagent.reporttrend;

import com.liu.eemrsagent.trace.NoopTraceRecorder;
import com.liu.eemrsagent.trace.TraceRedactor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportTrendAnalysisServiceTest {

    @Test
    void decryptFailureDoesNotCallCloudModel() {
        LabReportCipherRepository reportRepository = mock(LabReportCipherRepository.class);
        LocalReportDecryptor decryptor = mock(LocalReportDecryptor.class);
        CloudReportAnalysisClient cloudClient = mock(CloudReportAnalysisClient.class);
        ReportAnalysisResultRepository resultRepository = mock(ReportAnalysisResultRepository.class);
        when(reportRepository.findEncryptedReports(any(), any(), any(), any())).thenReturn(List.of(
                new EncryptedLabReportRecord("r1", LocalDate.parse("2026-01-01"), "LAB", "bad-cipher")
        ));
        when(decryptor.decrypt(any())).thenThrow(new ReportTrendException(ReportTrendErrorCode.REPORT_DECRYPT_FAILED, "mock decrypt failed"));

        ReportTrendAnalysisService service = new ReportTrendAnalysisService(
                reportRepository,
                decryptor,
                new LocalPiiRedactor(),
                mock(LocalReportStructuringService.class),
                new TrendAnalysisService(new ReportTrendProperties()),
                mock(ReportTrendContextService.class),
                new CloudPayloadBuilder(),
                mock(CloudPayloadPrivacyGuard.class),
                cloudClient,
                resultRepository,
                new NoopTraceRecorder(),
                new TraceRedactor()
        );

        ReportTrendAnalysisResponse response = service.analyze(new ReportTrendAnalysisRequest(
                "patient-1", null, false, false, "LAB", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"), List.of("WBC"), "DOCTOR_AND_PATIENT"
        ));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.errorCode()).isEqualTo(ReportTrendErrorCode.REPORT_DECRYPT_FAILED.name());
        verify(cloudClient, never()).analyze(any());
    }

    @Test
    void cloudFailureKeepsDeterministicTrendVisibleAsPartialSuccess() {
        LabReportCipherRepository reportRepository = mock(LabReportCipherRepository.class);
        LocalReportDecryptor decryptor = mock(LocalReportDecryptor.class);
        LocalReportStructuringService structuringService = mock(LocalReportStructuringService.class);
        ReportTrendContextService contextService = mock(ReportTrendContextService.class);
        CloudPayloadPrivacyGuard privacyGuard = mock(CloudPayloadPrivacyGuard.class);
        CloudReportAnalysisClient cloudClient = mock(CloudReportAnalysisClient.class);
        ReportAnalysisResultRepository resultRepository = mock(ReportAnalysisResultRepository.class);
        EncryptedLabReportRecord r1 = new EncryptedLabReportRecord("r1", LocalDate.parse("2026-01-01"), "LAB", "cipher-1");
        EncryptedLabReportRecord r2 = new EncryptedLabReportRecord("r2", LocalDate.parse("2026-02-01"), "LAB", "cipher-2");
        when(reportRepository.findEncryptedReports(any(), any(), any(), any())).thenReturn(List.of(r1, r2));
        when(decryptor.decrypt(r1)).thenReturn("plain-1");
        when(decryptor.decrypt(r2)).thenReturn("plain-2");
        when(structuringService.structure("r1", LocalDate.parse("2026-01-01"), "LAB", "plain-1"))
                .thenReturn(report("r1", "2026-01-01", value("WBC", "白细胞", "8.0", "3.5", "9.5")));
        when(structuringService.structure("r2", LocalDate.parse("2026-02-01"), "LAB", "plain-2"))
                .thenReturn(report("r2", "2026-02-01", value("WBC", "白细胞", "12.0", "3.5", "9.5")));
        when(contextService.load(any())).thenReturn(ReportTrendContext.empty());
        when(cloudClient.request(any())).thenThrow(new ReportTrendException(ReportTrendErrorCode.CLOUD_MODEL_FAILED, "Cloud model call failed"));

        ReportTrendAnalysisService service = new ReportTrendAnalysisService(
                reportRepository,
                decryptor,
                new LocalPiiRedactor(),
                structuringService,
                new TrendAnalysisService(new ReportTrendProperties()),
                contextService,
                new CloudPayloadBuilder(),
                privacyGuard,
                cloudClient,
                resultRepository,
                new NoopTraceRecorder(),
                new TraceRedactor()
        );

        ReportTrendAnalysisResponse response = service.analyze(new ReportTrendAnalysisRequest(
                "patient-1", null, false, false, "LAB", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"), List.of("WBC"), "DOCTOR_AND_PATIENT"
        ));

        assertThat(response.status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(response.errorCode()).isEqualTo(ReportTrendErrorCode.CLOUD_MODEL_FAILED.name());
        assertThat(response.trendItems()).hasSize(1);
        assertThat(response.trendItems().get(0).changeAbsolute()).isEqualByComparingTo("4.0");
    }

    private StructuredLabReport report(String id, String date, LabIndicatorItem item) {
        return new StructuredLabReport(id, LocalDate.parse(date), "LAB", List.of(item));
    }

    private LabIndicatorItem value(String code, String name, String value, String low, String high) {
        java.math.BigDecimal current = new java.math.BigDecimal(value);
        java.math.BigDecimal referenceLow = new java.math.BigDecimal(low);
        java.math.BigDecimal referenceHigh = new java.math.BigDecimal(high);
        AbnormalFlag flag = current.compareTo(referenceLow) < 0 ? AbnormalFlag.LOW
                : current.compareTo(referenceHigh) > 0 ? AbnormalFlag.HIGH : AbnormalFlag.NORMAL;
        return new LabIndicatorItem(name, code, name, current, "10^9/L", referenceLow, referenceHigh, flag);
    }
}
