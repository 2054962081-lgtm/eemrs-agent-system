package com.liu.eemrsserver.service;

import com.liu.eemrsserver.crypto.UserLogCrypto;
import com.liu.eemrsserver.jsontrans.UserLog;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * @author L
 * @date 2019-09-21 12:46
 * @desc
 **/
@Service
@Scope("prototype")
public class UserOpService {
    @Autowired
    private UserLogCrypto userOpCrypto;
    /**
     * 根据传经来的密文UserOp，解密后插入数据
     *
     * @param userOP
     * @return
     */
    public boolean insertUser(@NonNull UserLog userOP) {
        return userOpCrypto.insertUserWithType(userOP);
    }
    public boolean loginUser(UserLog userOP) {
        return userOpCrypto.loginUserWithType(userOP);
    }
    public boolean logoutUser(UserLog userOP) {
        return userOpCrypto.logoutUserWithType(userOP);
    }
}
