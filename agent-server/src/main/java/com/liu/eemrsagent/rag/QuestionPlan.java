package com.liu.eemrsagent.rag;

import java.util.List;

public record QuestionPlan(
        String riskLevel,
        List<String> recommendedDepartments,
        String urgencyLevel,
        List<String> keyQuestions,
        List<String> redFlags,
        List<String> forbiddenActions,
        List<String> expectedResponsePoints,
        List<String> doctorRecordFields,
        List<String> evidenceTitles,
        List<String> sourceDocTypes
) {
    public static QuestionPlan empty() {
        return new QuestionPlan("", List.of(), "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean hasContent() {
        return !keyQuestions.isEmpty() || !redFlags.isEmpty() || !recommendedDepartments.isEmpty();
    }
}
