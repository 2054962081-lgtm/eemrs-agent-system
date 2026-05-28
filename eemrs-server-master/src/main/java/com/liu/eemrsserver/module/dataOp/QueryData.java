package com.liu.eemrsserver.module.dataOp;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.AnswerData;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
public class QueryData {
    private DataOpService dataOpService;
    private byte[] sessionKey;
    @Transactional
    public String query(QueryConditions queryConditions, Integer answerCode) {
        List<VisitInfo> patientInfos = dataOpService.query(queryConditions);
        String answer = JSON.toJSONString(patientInfos);

        AnswerData answerData = new AnswerData(answerCode, true, answer);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, sessionKey);
        return enData;
    }

    public String sendDoctorInfo(String id, int answerCode) {
        String answer;
        boolean bool;
        DoctorInfo doctorInfo = dataOpService.sendDocInfo(id);
        if (doctorInfo!=null){
            answer=JSON.toJSONString(doctorInfo);
            bool=true;
        }else {
            doctorInfo=new DoctorInfo();
            answer = JSON.toJSONString(doctorInfo);
            bool=false;
        }

        AnswerData answerData = new AnswerData(answerCode, bool, answer);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, sessionKey);
        return enData;
    }
}
