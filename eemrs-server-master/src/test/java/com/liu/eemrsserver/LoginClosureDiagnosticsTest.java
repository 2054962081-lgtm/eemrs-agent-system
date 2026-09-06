package com.liu.eemrsserver;

import com.liu.eemrsserver.auth.AuthServiceAdapter;
import com.liu.eemrsserver.auth.dto.LoginRequest;
import com.liu.eemrsserver.auth.dto.LoginResponse;
import com.liu.eemrsserver.auth.dto.RegisterRequest;
import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.crypto.UserLogCrypto;
import com.liu.eemrsserver.domain.PatLog;
import com.liu.eemrsserver.mapper.UserLogMapper;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
public class LoginClosureDiagnosticsTest {
    private static final String TEST_ID = "110101199001010001";
    private static final String TEST_PASSWORD = "123456";

    @Autowired
    private AuthServiceAdapter authServiceAdapter;
    @Autowired
    private UserLogCrypto userLogCrypto;
    @Autowired
    private UserLogMapper userLogMapper;
    @Autowired
    private SMServerKey smServerKey;

    @Test
    public void patientRegisterThenLoginClosesAgainstCurrentDatabase() {
        String normalizedId = userLogCrypto.normalizeIdNumber(TEST_ID);
        String hash = userLogCrypto.buildIdHash(normalizedId);
        List<PatLog> hashHitsBefore = userLogMapper.getPatByHash(hash);
        boolean hashMatchedBefore = anyPlainPatientCredentialMatches(hashHitsBefore);
        boolean fallbackMatchedBefore = anyPlainPatientCredentialMatches(userLogMapper.listAllPatsForLoginRepair());

        System.out.println("LOGIN_DIAG before branch=patient idTail=" + tail(normalizedId)
                + " hashPrefix=" + prefix(hash)
                + " hashHitCount=" + hashHitsBefore.size()
                + " hashCredentialMatch=" + hashMatchedBefore
                + " fallbackWouldMatch=" + fallbackMatchedBefore);

        if (!hashMatchedBefore && !fallbackMatchedBefore) {
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setType("pt");
            registerRequest.setIdNumber(TEST_ID);
            registerRequest.setUserName("LoginClosurePatient");
            registerRequest.setPassword(TEST_PASSWORD);
            registerRequest.setDepartment("");
            boolean registered = authServiceAdapter.register(registerRequest);
            System.out.println("LOGIN_DIAG registerAttempted=true registerResult=" + registered);
        } else {
            System.out.println("LOGIN_DIAG registerAttempted=false");
        }

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setType("pt");
        loginRequest.setIdNumber(TEST_ID);
        loginRequest.setPassword(TEST_PASSWORD);
        loginRequest.setDepartment("");
        LoginResponse response = authServiceAdapter.login(loginRequest);

        List<PatLog> hashHitsAfter = userLogMapper.getPatByHash(hash);
        System.out.println("LOGIN_DIAG after success=true branch=patient departmentUsed=false"
                + " hashHitCount=" + hashHitsAfter.size()
                + " fallbackTriggered=" + (hashHitsBefore.isEmpty() && fallbackMatchedBefore)
                + " hashRepaired=" + (hashHitsBefore.isEmpty() && !hashHitsAfter.isEmpty())
                + " tokenReturned=" + (response.getToken() != null));

        assertNotNull(response.getToken());
        assertTrue(hashHitsAfter.size() > 0);
    }

    private boolean anyPlainPatientCredentialMatches(List<PatLog> encryptedPatients) {
        for (PatLog encryptedPatient : encryptedPatients) {
            try {
                PatLog patient = JavaBeanEnc.decPat(encryptedPatient, smServerKey.getSm4Key());
                if (TEST_ID.equals(userLogCrypto.normalizeIdNumber(patient.getIdNumber()))
                        && TEST_PASSWORD.equals(patient.getPassword())) {
                    return true;
                }
            } catch (RuntimeException e) {
                System.out.println("LOGIN_DIAG skipUnreadablePatientRow id=" + encryptedPatient.getId());
            }
        }
        return false;
    }

    private String prefix(String value) {
        return value == null || value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String tail(String value) {
        return value == null || value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
