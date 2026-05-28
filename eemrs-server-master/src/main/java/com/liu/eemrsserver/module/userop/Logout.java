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
 * @date 2019-09-27 13:35
 * @desc
 **/
public class Logout {
    private UserOpService userOpService;
    private byte[] key;
    @Getter
    private boolean success;

    public Logout(UserOpService userOpService, byte[] key) {
        this.userOpService = userOpService;
        this.key = key;
    }
    @Transactional
    public String delete(UserLog deUserOp, Integer answerCode) {
        success = userOpService.logoutUser(deUserOp);
        String answerInfo = null;
        if (success) {
            answerInfo = "用户已注销";
        } else {
            answerInfo = "注销失败，请检查用户名和密码";
        }
        AnswerData answerData = new AnswerData(answerCode, success, answerInfo);//封装信息
        String JSONData = JSON.toJSONString(answerData);
        String enData = SM4_String.encWithIV(JSONData, key);
        return enData;
    }
}
