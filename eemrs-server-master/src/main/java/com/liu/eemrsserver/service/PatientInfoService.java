package com.liu.eemrsserver.service;

import com.liu.eemrsserver.crypto.PIMCrypto;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.jsontrans.PasswordModify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * @author L
 * @date 2019-10-14 18:28
 * @desc 修改并病人信息的服务
 **/
@Service
@Scope("prototype")
public class PatientInfoService {
    @Autowired
    private PIMCrypto pimCrypto;
    public boolean modifyPassword(PasswordModify modify) {
        return pimCrypto.modifyPassword(modify);
    }

    public boolean modifyInfo(PatientInfo patientInfo) {
        return pimCrypto.modifyInfo(patientInfo);
    }


}
