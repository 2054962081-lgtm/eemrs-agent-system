package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.jsontrans.PasswordModify;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.mapper.PIMMapper;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import com.liu.eemrsserver.utils.crypto.SM3;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author L
 * @date 2019-10-14 18:41
 * @desc
 **/

@Component
@Scope("prototype")
public class PIMCrypto {
    @Autowired
    private UserLogCrypto userOpCrypto;
    @Autowired
    private PIMMapper pimMapper;
    @Autowired
    private SMServerKey smServerKey;
    public boolean modifyPassword(PasswordModify modify) {
        boolean bool=false;
        System.out.println(modify);
        UserLog userLog = new UserLog(modify.getType(),modify.getIdNumber(),null, modify.getOldPassword(), null, null,null);
        if (userOpCrypto.loginUserWithType(userLog)){
            String hash = SM3.hash(modify.getIdNumber());
            System.out.println(hash);
            String pass = SM4_String.encWithIV(modify.getNewPassword(),smServerKey.getSm4Key());
           bool =  pimMapper.updatePatientPassword(modify.getType(),hash,pass);
        }

        return bool;
    }

    public boolean modifyInfo(PatientInfo patientInfo) {
        System.out.println(patientInfo);
        PatientInfo info = JavaBeanEnc.encVisitInfo(patientInfo, smServerKey.getSm4Key());
        return pimMapper.updatePatient(info);
    }

}
