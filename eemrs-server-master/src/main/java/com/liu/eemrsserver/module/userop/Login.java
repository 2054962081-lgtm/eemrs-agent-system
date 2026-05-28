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
 * @date 2019-09-27 11:34
 * @desc 用户登录模块
 **/
public class Login {
    private UserOpService userOpService;
    private byte[] key;
    @Getter
    private boolean login;

    public Login(UserOpService userOpService, byte[] key) {
        this.userOpService = userOpService;
        this.key = key;
    }
    @Transactional
    public String login(UserLog deUserOp, Integer answerCode) {
        login = userOpService.loginUser(deUserOp);
        String answerInfo = null;
        if (login) {
            answerInfo = "用户已登录";
        } else {
            answerInfo = "登录失败，请检查用户名和密码";
        }
        AnswerData answerData = new AnswerData(answerCode, login, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }
}
