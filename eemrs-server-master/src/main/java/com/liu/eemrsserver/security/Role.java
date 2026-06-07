package com.liu.eemrsserver.security;

public enum Role {
    PATIENT,
    DOCTOR;

    public static Role fromType(String type) {
        if ("pt".equals(type)) {
            return PATIENT;
        }
        if ("dt".equals(type) || "dr".equals(type)) {
            return DOCTOR;
        }
        throw new UnauthorizedException("Unsupported user type: " + type);
    }
}
