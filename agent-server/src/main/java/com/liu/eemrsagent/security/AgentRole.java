package com.liu.eemrsagent.security;

public enum AgentRole {
    PATIENT,
    DOCTOR;

    public static AgentRole from(String value) {
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("Missing role");
        }
        return AgentRole.valueOf(value.trim().toUpperCase());
    }
}
