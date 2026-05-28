package com.liu.eemrsserver.module.guahao;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.jsontrans.AnswerData;
import com.liu.eemrsserver.domain.Waiting;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.utils.NewPair;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author L
 * @date 2019-10-06 21:11
 * @desc 挂号模块
 **/
@AllArgsConstructor
@NoArgsConstructor
public class Guahao {
    private GuahaoService guahaoService;
    private byte[] key;



    /**
     * 根据挂号信息进行挂号，并返回挂号是否成功
     * @param guahaoInfo
     * @return
     */
    @Transactional
    public String add(GuahaoInfo guahaoInfo,Integer answerCode) {
       boolean answer= guahaoService.add(guahaoInfo);
        System.out.println(answer);
        String answerInfo;
        if (answer) {
            answerInfo="挂号成功";
        }else {
            answerInfo="挂号失败";
        }
        AnswerData answerData = new AnswerData(answerCode, answer, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }

    /**
     * 查询当前科室的挂号信息
     * @param newPair
     * @param answerCode
     * @return
     */
    @Transactional
    public String query(NewPair newPair, int answerCode) {
        List<Waiting> users = guahaoService.query(newPair);
        String answer = JSON.toJSONString(users);

        AnswerData answerData = new AnswerData(answerCode, true, answer);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }

    /**
     * 接诊一位病人，并返回该病人的详细信息
     * @param idNumber
     * @param answerCode
     * @return
     */
    @Transactional
    public String accept(String idNumber, Integer answerCode) {
        PatientInfo patientInfo=guahaoService.getPatientInfo(idNumber);
        boolean bool=false;
        if(patientInfo.getIdNumber()!=null){
            bool=true;
        }
        AnswerData answerData = new AnswerData(answerCode,bool,JSON.toJSONString(patientInfo));//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }

    /**
     * 返回一个病人的详细信息
     * @param id
     * @param answerCode
     * @return
     */
    @Transactional
    public String sendPatientInfo(String id, int answerCode) {
        String answer;
        boolean bool;
        PatientInfo patientInfo = guahaoService.sendInfo(id);
        if (patientInfo!=null){
            answer=JSON.toJSONString(patientInfo);
            bool=true;
        }else {
            patientInfo=new PatientInfo();
            answer = JSON.toJSONString(patientInfo);
            bool=false;
        }

        AnswerData answerData = new AnswerData(answerCode, bool, answer);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;

    }
}
