package com.liu.eemrsserver.crypto;

import com.liu.eemrsserver.utils.crypto.OperateKey;
import com.liu.eemrsserver.utils.Pair;
import lombok.Getter;
import lombok.NonNull;
import org.apache.log4j.Logger;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.zz.gmhelper.SM2Util;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * @author L
 * @date 2019-09-21 9:39
 * @desc session key for a user
 **/
public class SessionKey {
    private static Logger logger = Logger.getLogger(SessionKey.class);
    @Getter
    private byte[] key;
    public SessionKey(){
        this.key=OperateKey.getSM4Key();
    }
    public SecretKey toKey(){
        return OperateKey.toSM4Key(this.key);
    }

    public String toBase64String(){
        return Base64.getEncoder().encodeToString(this.key);
    }

    /**
     * 返回密文回话秘钥和签名对
     * @param sPrivateKey
     * @param uPublicKey
     * @return
     */
    public Pair<String,String> sign(@NonNull BCECPrivateKey sPrivateKey, @NonNull BCECPublicKey uPublicKey){
        try {
            byte[] en = SM2Util.encrypt(uPublicKey,this.key);
            logger.debug("[会话密钥已加密！]");
            String enKey = Base64.getEncoder().encodeToString(en);
            String signForEncKey = Base64.getEncoder().encodeToString(SM2Util.sign(sPrivateKey,en));
            logger.debug("[会话密钥已签名！]");
            return new Pair<>(enKey,signForEncKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(null,null);
    }
}
