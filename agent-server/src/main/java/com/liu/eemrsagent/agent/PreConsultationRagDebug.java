package com.liu.eemrsagent.agent;

import java.util.List;
import java.util.Map;

public record PreConsultationRagDebug(
        List<String> ragQuestionPlanKeyQuestions,
        List<String> ragQuestionPlanRedFlags,
        String ragQuestionPlanUrgency,
        List<String> ragQuestionPlanDepartments,
        String expandedQuery,
        Map<String, Integer> ragDocTypeCounts,
        double coverageBeforePostprocess,
        double coverageAfterPostprocess,
        int postProcessAddedQuestions,
        boolean usedQuestionPlan,
        boolean usedQueryExpansion,
        boolean usedPostprocess
) {
}
