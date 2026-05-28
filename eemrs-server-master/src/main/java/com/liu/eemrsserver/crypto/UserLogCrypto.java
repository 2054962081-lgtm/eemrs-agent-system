package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.domain.DocLog;
import com.liu.eemrsserver.domain.PatLog;
import com.liu.eemrsserver.mapper.UserLogMapper;
import com.liu.eemrsserver.jsontrans.UserLog;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import com.liu.eemrsserver.utils.crypto.SM3;
//import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.bind.v2.model.core.TypeInfo;
import lombok.NonNull;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author L
 * @date 2019-09-22 9:29
 * @desc
 **/
@Component("userOpCrypto")
@Scope("prototype")
public class UserLogCrypto {
    private static Logger logger = Logger.getLogger(UserLogCrypto.class);

    @Autowired
    private SMServerKey smServerKey;

    @Autowired
    private UserLogMapper userOpMapper;

    /**
     * 根据传来的密文UserOp，解密后插入数据,并返回密文响应值
     *
     * @param deUserOp
     * @return
     */
    public boolean insertUserWithType(@NonNull UserLog deUserOp) {
        try {

            deUserOp.setHashCode(//计算hash值
                    SM3.hash(deUserOp.getIdNumber())
            );

            UserLog userOPDo = JavaBeanEnc.encUserOp(deUserOp, smServerKey.getSm4Key());//加密POJO

            boolean out = userOpMapper.insertUserWithType(userOPDo);//插入并返回

            logger.info("插入结果：" + out);
            return out;
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
        return false;
    }

    public boolean loginUserWithType(UserLog deUserOp) {
        try {
            String hashCode = SM3.hash(deUserOp.getIdNumber());
            if (deUserOp.getType().equals("dt")) {
                DocLog user = new DocLog(null, deUserOp.getIdNumber(), null, deUserOp.getDepartment(), deUserOp.getPassword(), hashCode);
                List<DocLog> enUser = userOpMapper.getDocByHash(hashCode);
                if (enUser.size() == 0) {
                    return false;
                }
                DocLog deUser = JavaBeanEnc.decDoc(enUser.get(0), smServerKey.getSm4Key());
                boolean bool = deUser.equals(user);
                return bool;
            } else {
                PatLog user = new PatLog(null, deUserOp.getIdNumber(), null, deUserOp.getPassword(), hashCode);
                List<PatLog> enUser = userOpMapper.getPatByHash(hashCode);
                if (enUser.size() == 0) {
                    return false;
                }
                PatLog deUser = JavaBeanEnc.decPat(enUser.get(0), smServerKey.getSm4Key());
                boolean bool = deUser.equals(user);
                return bool;
            }
        } catch (Exception e) {
            logger.warn("Login failed while reading existing encrypted user data", e);
            return false;
        }

    }

    public boolean logoutUserWithType(UserLog deUserOp) {
        String hashCode = SM3.hash(deUserOp.getIdNumber());

        if (deUserOp.getType().equals("dt")) {
            DocLog user = new DocLog(null, deUserOp.getIdNumber(), null, deUserOp.getDepartment(), deUserOp.getPassword(), hashCode);
            List<DocLog> enUser = userOpMapper.getDocByHash(hashCode);
            if (enUser.size() == 0) {
                return false;
            }
            DocLog deUser = JavaBeanEnc.decDoc(enUser.get(0), smServerKey.getSm4Key());
            if (user.equals(deUser)) {
                return userOpMapper.deleteUserByHash(hashCode, deUserOp.getType());
            } else return false;
        } else {
            PatLog user = new PatLog(null, deUserOp.getIdNumber(), null, deUserOp.getPassword(), hashCode);
            List<PatLog> enUser = userOpMapper.getPatByHash(hashCode);
            if (enUser.size() == 0) {
                return false;
            }
            PatLog deUser = JavaBeanEnc.decPat(enUser.get(0), smServerKey.getSm4Key());
            if (user.equals(deUser)) {
                return userOpMapper.deleteUserByHash(hashCode, deUserOp.getType());
            } else return false;
        }

    }
}
