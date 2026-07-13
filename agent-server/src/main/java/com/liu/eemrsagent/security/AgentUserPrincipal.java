package com.liu.eemrsagent.security;

public record AgentUserPrincipal(
        String idNumber,
        String type,
        AgentRole role,
        String department
) {
    public boolean isDoctor() {
        return role == AgentRole.DOCTOR;
    }
}
