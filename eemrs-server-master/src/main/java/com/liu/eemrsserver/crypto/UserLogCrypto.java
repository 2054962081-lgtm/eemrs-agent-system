package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.domain.DocLog;
import com.liu.eemrsserver.domain.PatLog;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.mapper.UserLogMapper;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import com.liu.eemrsserver.utils.crypto.SM3;
import lombok.NonNull;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("userOpCrypto")
@Scope("prototype")
public class UserLogCrypto {
    private static final Logger logger = Logger.getLogger(UserLogCrypto.class);

    @Autowired
    private SMServerKey smServerKey;

    @Autowired
    private UserLogMapper userOpMapper;

    public boolean insertUserWithType(@NonNull UserLog deUserOp) {
        try {
            deUserOp.setIdNumber(normalizeIdNumber(deUserOp.getIdNumber()));
            deUserOp.setHashCode(buildIdHash(deUserOp.getIdNumber()));
            UserLog userOPDo = JavaBeanEnc.encUserOp(deUserOp, smServerKey.getSm4Key());
            boolean out = userOpMapper.insertUserWithType(userOPDo);
            logger.info("insert user result=" + out + ", type=" + deUserOp.getType()
                    + ", idTail=" + maskTail(deUserOp.getIdNumber()));
            return out;
        } catch (Exception e) {
            logger.warn("Insert user failed, type=" + deUserOp.getType()
                    + ", idTail=" + maskTail(deUserOp.getIdNumber()), e);
        }
        return false;
    }

    public boolean loginUserWithType(UserLog deUserOp) {
        try {
            deUserOp.setIdNumber(normalizeIdNumber(deUserOp.getIdNumber()));
            String hashCode = buildIdHash(deUserOp.getIdNumber());
            if (isDoctorType(deUserOp.getType())) {
                logger.debug("Login branch=doctor type=" + deUserOp.getType()
                        + ", departmentBlank=" + isBlank(deUserOp.getDepartment())
                        + ", idTail=" + maskTail(deUserOp.getIdNumber())
                        + ", hashPrefix=" + hashPrefix(hashCode));
                DocLog user = new DocLog(null, deUserOp.getIdNumber(), null,
                        deUserOp.getDepartment(), deUserOp.getPassword(), hashCode);
                List<DocLog> enUser = userOpMapper.getDocByHash(hashCode);
                logger.debug("Login branch=doctor candidateCount=" + enUser.size()
                        + ", idTail=" + maskTail(deUserOp.getIdNumber())
                        + ", hashPrefix=" + hashPrefix(hashCode));
                if (matchesDoctor(user, enUser, null)) {
                    return true;
                }
                List<DocLog> fallbackUsers = userOpMapper.listAllDocsForLoginRepair();
                logger.debug("Login branch=doctor fallbackAllCount=" + fallbackUsers.size()
                        + ", idTail=" + maskTail(deUserOp.getIdNumber())
                        + ", hashPrefix=" + hashPrefix(hashCode));
                return matchesDoctor(user, fallbackUsers, hashCode);
            }

            logger.debug("Login branch=patient type=" + deUserOp.getType()
                    + ", departmentBlank=" + isBlank(deUserOp.getDepartment())
                    + ", idTail=" + maskTail(deUserOp.getIdNumber())
                    + ", hashPrefix=" + hashPrefix(hashCode));
            PatLog user = new PatLog(null, deUserOp.getIdNumber(), null,
                    deUserOp.getPassword(), hashCode);
            List<PatLog> enUser = userOpMapper.getPatByHash(hashCode);
            logger.debug("Login branch=patient candidateCount=" + enUser.size()
                    + ", idTail=" + maskTail(deUserOp.getIdNumber())
                    + ", hashPrefix=" + hashPrefix(hashCode));
            if (matchesPatient(user, enUser, null)) {
                return true;
            }
            List<PatLog> fallbackUsers = userOpMapper.listAllPatsForLoginRepair();
            logger.debug("Login branch=patient fallbackAllCount=" + fallbackUsers.size()
                    + ", idTail=" + maskTail(deUserOp.getIdNumber())
                    + ", hashPrefix=" + hashPrefix(hashCode));
            return matchesPatient(user, fallbackUsers, hashCode);
        } catch (Exception e) {
            logger.warn("Login failed while reading existing encrypted user data, type="
                    + deUserOp.getType() + ", idTail=" + maskTail(deUserOp.getIdNumber()), e);
            return false;
        }
    }

    private boolean matchesDoctor(DocLog user, List<DocLog> encryptedUsers, String repairHashCode) {
        for (DocLog encryptedUser : encryptedUsers) {
            try {
                DocLog deUser = JavaBeanEnc.decDoc(encryptedUser, smServerKey.getSm4Key());
                if (deUser.equals(user)) {
                    repairDoctorHash(encryptedUser, repairHashCode);
                    return true;
                }
            } catch (RuntimeException e) {
                logger.debug("Skip unreadable encrypted doctor login candidate id=" + encryptedUser.getId());
            }
        }
        return false;
    }

    private boolean matchesPatient(PatLog user, List<PatLog> encryptedUsers, String repairHashCode) {
        for (PatLog encryptedUser : encryptedUsers) {
            try {
                PatLog deUser = JavaBeanEnc.decPat(encryptedUser, smServerKey.getSm4Key());
                if (deUser.equals(user)) {
                    repairPatientHash(encryptedUser, repairHashCode);
                    return true;
                }
            } catch (RuntimeException e) {
                logger.debug("Skip unreadable encrypted patient login candidate id=" + encryptedUser.getId());
            }
        }
        return false;
    }

    private void repairDoctorHash(DocLog encryptedUser, String repairHashCode) {
        if (repairHashCode != null && encryptedUser.getId() != null) {
            userOpMapper.updateDocHashById(encryptedUser.getId(), repairHashCode);
        }
    }

    private void repairPatientHash(PatLog encryptedUser, String repairHashCode) {
        if (repairHashCode != null && encryptedUser.getId() != null) {
            userOpMapper.updatePatHashById(encryptedUser.getId(), repairHashCode);
        }
    }

    public boolean logoutUserWithType(UserLog deUserOp) {
        deUserOp.setIdNumber(normalizeIdNumber(deUserOp.getIdNumber()));
        String hashCode = buildIdHash(deUserOp.getIdNumber());

        if (isDoctorType(deUserOp.getType())) {
            DocLog user = new DocLog(null, deUserOp.getIdNumber(), null,
                    deUserOp.getDepartment(), deUserOp.getPassword(), hashCode);
            List<DocLog> enUser = userOpMapper.getDocByHash(hashCode);
            for (DocLog encryptedUser : enUser) {
                DocLog deUser = JavaBeanEnc.decDoc(encryptedUser, smServerKey.getSm4Key());
                if (user.equals(deUser)) {
                    return userOpMapper.deleteUserByHash(hashCode, "dt");
                }
            }
            return false;
        }

        PatLog user = new PatLog(null, deUserOp.getIdNumber(), null, deUserOp.getPassword(), hashCode);
        List<PatLog> enUser = userOpMapper.getPatByHash(hashCode);
        for (PatLog encryptedUser : enUser) {
            PatLog deUser = JavaBeanEnc.decPat(encryptedUser, smServerKey.getSm4Key());
            if (user.equals(deUser)) {
                return userOpMapper.deleteUserByHash(hashCode, "pt");
            }
        }
        return false;
    }

    private boolean isDoctorType(String type) {
        return "dt".equals(type) || "dr".equals(type);
    }

    public String buildIdHash(String idNumber) {
        return SM3.hash(normalizeIdNumber(idNumber));
    }

    public String normalizeIdNumber(String idNumber) {
        if (idNumber == null) {
            return null;
        }
        return idNumber.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String maskTail(String idNumber) {
        if (idNumber == null || idNumber.length() <= 4) {
            return "****";
        }
        return "****" + idNumber.substring(idNumber.length() - 4);
    }

    private String hashPrefix(String hashCode) {
        if (hashCode == null || hashCode.length() <= 8) {
            return hashCode;
        }
        return hashCode.substring(0, 8);
    }
}
