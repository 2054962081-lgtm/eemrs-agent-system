package com.liu.eemrsagent.eval;

public record ReportFixtureCleanupRequest(
        String evalRunId,
        String caseId
) {
}
