package com.liu.eemrsagent.reporttrend;

public record ContextUsed(
        boolean preconsultation,
        boolean longTermHealth,
        boolean triage
) {
}
