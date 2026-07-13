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
}
