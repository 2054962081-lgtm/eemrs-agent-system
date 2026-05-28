package com.liu.eemrsserver.module.dataOp;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.jsontrans.AnswerData;
import com.liu.eemrsserver.jsontrans.PasswordModify;
import com.liu.eemrsserver.service.PatientInfoService;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author L
 * @date 2019-10-14 18:30
 * @desc
 **/
public class DoPatientInfo {


    private PatientInfoService patientInfoService;
    private byte[] key;

    public DoPatientInfo(PatientInfoService patientInfoService, byte[] key) {
        this.key = key;
        this.patientInfoService = patientInfoService;
    }

    @Transactional
    public String modifyPassword(PasswordModify modify, Integer answerCode) {
        boolean bool = patientInfoService.modifyPassword(modify);
        String answerInfo = null;
        if (bool) {
            answerInfo = "密码修改成功";
        } else {
            answerInfo = "密码修改失败";
        }
        AnswerData answerData = new AnswerData(answerCode, bool, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }

    @Transactional
    public String modifyInfo(PatientInfo patientInfo, Integer answerCode) {
        boolean bool = patientInfoService.modifyInfo(patientInfo);
        String answerInfo = null;
        if (bool) {
            answerInfo = "修改成功";
        } else {
            answerInfo = "修改失败";
        }
        AnswerData answerData = new AnswerData(answerCode, bool, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }


}
