package com.liu.eemrsserver.module.userop;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.jsontrans.AnswerData;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.service.UserOpService;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import lombok.Getter;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author L
 * @date 2019-09-27 11:09
 * @desc 注册用户的模块
 **/
public class Register {
    private UserOpService userOpService;
    private byte[] key;
    @Getter
    private boolean success;

    public Register(UserOpService userOpService, byte[] key) {
        this.userOpService = userOpService;
        this.key = key;
    }
    @Transactional
    public String regist(UserLog deUserOp, int answerCode) throws Exception {
        boolean bool = userOpService.insertUser(deUserOp);//执行操作并返回密文响应值
        String answerInfo = null;
        if (bool) {
            answerInfo = "注册成功，用户已登录";
        } else {
            answerInfo = "注册失败，账号已存在";
        }
        AnswerData answerData = new AnswerData(answerCode, bool, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }
}
