package com.liu.eemrsagent.agent;

public record PreConsultationResponse(
        boolean success,
        String mode,
        String reply,
        // Backward compatibility: finished means the current conversation should stop.
        boolean finished,
        boolean shouldStopConversation,
        boolean taskComplete,
        String terminationReason,
        int round,
        String recommendedDepartment,
        String urgency,
        String model,
        String provider,
        String error,
        PreConsultationRagDebug ragDebug
) {
    public static PreConsultationResponse ok(
            String mode,
            String reply,
            boolean finished,
            boolean taskComplete,
            String terminationReason,
            int round,
            String recommendedDepartment,
            String urgency,
            String model,
            String provider
    ) {
        return ok(mode, reply, finished, taskComplete, terminationReason, round, recommendedDepartment, urgency, model, provider, null);
    }

    public static PreConsultationResponse ok(
            String mode,
            String reply,
            boolean finished,
            boolean taskComplete,
            String terminationReason,
            int round,
            String recommendedDepartment,
            String urgency,
            String model,
            String provider,
            PreConsultationRagDebug ragDebug
    ) {
        return new PreConsultationResponse(true, mode, reply, finished, finished, taskComplete,
                terminationReason, round, recommendedDepartment, urgency, model, provider, null, ragDebug);
    }

    public static PreConsultationResponse fail(String mode, int round, String model, String provider, String error) {
        String message = error == null || error.isBlank()
                ? "智能体服务暂时不可用，请稍后再试。"
                : error;
        return new PreConsultationResponse(
                false,
                mode,
                message,
                false,
                false,
                false,
                "ERROR",
                round,
                "",
                "normal",
                model,
                provider,
                error,
                null
        );
    }
}
