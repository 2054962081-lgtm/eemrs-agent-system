package com.liu.eemrsagent.medicalrecord;

public record AgentMessage(
        String role,
        String content
) {
}
