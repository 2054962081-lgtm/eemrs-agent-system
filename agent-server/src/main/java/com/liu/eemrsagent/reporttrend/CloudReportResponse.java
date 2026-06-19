package com.liu.eemrsagent.reporttrend;

import java.util.List;

public record CloudReportResponse(
        String doctorSummary,
        String patientExplanation,
        String contextualInterpretation,
        List<KeyAbnormalItem> keyAbnormalItems,
        List<ContextLink> contextLinks,
        List<String> riskNotes,
        List<String> followUpQuestions,
        String suggestedDepartment,
        String suggestedAction
) {
    public record KeyAbnormalItem(String code, String name, String trend, String interpretation) {
    }
}
