package com.liu.eemrsserver.module.session;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.crypto.SessionKey;
import com.liu.eemrsserver.utils.crypto.SM2;
import com.liu.eemrsserver.utils.Pair;
import org.apache.log4j.Logger;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.zz.gmhelper.BCECUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Base64;

/**
 * @author L
 * @date 2019-09-21 11:11
 * @desc
 **/

public class BuildSession {
    private static Logger logger = Logger.getLogger(BuildSession.class);

    /**
     * send the encrypted session key and key`s signature.
     * then receive the encrypted answer from user. and return the decrypted the answer
     *
     * @param socket
     * @param sessionKey
     * @return
     * @throws Exception
     */
    public static boolean build(Socket socket, SessionKey sessionKey, SMServerKey keyPair) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        boolean success=false;
        int sum=0;
        do {//当密钥协商失败且协商次数小于5，重新协商
            byte[] uPublickey = Base64.getDecoder().decode(br.readLine());//从网络流中读取用户的公钥
            BCECPublicKey publicKey = BCECUtil.convertX509ToECPublicKey(uPublickey);
            logger.info("已读取读取用户的公钥");

            Pair<String, String> sig = sessionKey.sign(keyPair.getPrivateKey(), publicKey);//签名形式为先加密后签名
            bw.write(sig.getKey());//向用户写入密文会话密钥
            bw.newLine();
            bw.write(sig.getValue());//向用户写入密文密钥的签名
            bw.newLine();
            bw.write(Base64.getEncoder().encodeToString(keyPair.getPublicKey().getEncoded()));//发送公钥
            bw.newLine();
            bw.flush();
            logger.info("已向用户发送会话密钥");
            String answer = SM2.decrypt(br.readLine(),keyPair.getPrivateKey());
            success = Boolean.parseBoolean(answer);
            logger.info("用户是否收到密钥："+success);
            sum++;
        }while (!success&&sum<=5);
        return success;
    }
}
