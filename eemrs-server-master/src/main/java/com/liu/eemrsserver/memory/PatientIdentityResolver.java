package com.liu.eemrsserver.memory;

import com.liu.eemrsserver.crypto.UserLogCrypto;
import com.liu.eemrsserver.memory.dto.MemoryUserScopeDTO;
import com.liu.eemrsserver.security.CurrentUserHolder;
import com.liu.eemrsserver.security.Role;
import com.liu.eemrsserver.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PatientIdentityResolver {
    @Autowired
    private UserLogCrypto userLogCrypto;

    public MemoryUserScopeDTO resolveCurrentPatientScope() {
        UserPrincipal currentUser = CurrentUserHolder.get();
        if (currentUser.getRole() != Role.PATIENT) {
            return emptyScope();
        }
        String normalizedIdNumber = userLogCrypto.normalizeIdNumber(currentUser.getIdNumber());
        if (normalizedIdNumber == null || normalizedIdNumber.trim().isEmpty()) {
            return emptyScope();
        }
        String patientIdHash = userLogCrypto.buildIdHash(normalizedIdNumber);
        return new MemoryUserScopeDTO(
                null,
                normalizedIdNumber,
                "patient:" + patientIdHash,
                patientIdHash,
                maskIdNumber(normalizedIdNumber)
        );
    }

    private MemoryUserScopeDTO emptyScope() {
        return new MemoryUserScopeDTO(null, null, null, null, null);
    }

    private String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() <= 4) {
            return "patient";
        }
        return "patient-****" + idNumber.substring(idNumber.length() - 4);
    }
}
