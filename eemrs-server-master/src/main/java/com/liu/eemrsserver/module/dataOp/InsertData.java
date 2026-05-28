package com.liu.eemrsserver.module.dataOp;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.AnswerData;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.utils.Pair;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
public class InsertData {
    private DataOpService dataOpService;
    private byte[] sessionKey;

    /**
     * 插入就诊数据
     * @param patientInfo
     * @param answerCode
     * @return
     */
    @Transactional
    public Pair<String,Boolean> insertInto(VisitInfo patientInfo, Integer answerCode){
        boolean bool = dataOpService.insertInto(patientInfo);
        String answerInfo = null;
        if (bool) {
            answerInfo = "提交成功";
        } else {
            answerInfo = "提交失败";
        }
        AnswerData answerData = new AnswerData(answerCode, bool, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, sessionKey);
        return new Pair<>(enData,bool);
    }
}
