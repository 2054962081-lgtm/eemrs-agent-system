package com.lilin.client.connection;

import com.lilin.client.crypto.SM2ClientKey;
import com.lilin.client.utils.crypto.OperateKey;
import com.lilin.client.utils.crypto.SM2;
import lombok.Getter;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.zz.gmhelper.BCECUtil;
import org.zz.gmhelper.SM2Util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Base64;

/**
 * @author L
 * @date 2019-09-25 6:15
 * @desc 与服务器建立连接的模块
 **/
public class ConnectionWithServer {

    private boolean connect;



    @Getter
    private SM2ClientKey sm2ClientKey= OperateKey.getSM2ClientKeyFromFile();
    @Getter
    private byte[] sessionKey;
    private byte[] SPKey;
    @Getter
    private Socket socket;
    @Getter
    private BufferedWriter bw;
    @Getter
    private BufferedReader br;

    public boolean connect() throws Exception {
        socket = new Socket("127.0.0.1", 8887);

        bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        boolean an = false;
        int sum = 0;

        do {
            System.out.println(sm2ClientKey);
            String PbKey = Base64.getEncoder().encodeToString(sm2ClientKey.getPublicKey().getEncoded());

            bw.write(PbKey);//发送公钥
            bw.newLine();
            bw.flush();

            byte[] enSessionKey = Base64.getDecoder().decode(br.readLine());//读取会话密钥

            byte[] sig = Base64.getDecoder().decode(br.readLine());//读取签名

            SPKey = Base64.getDecoder().decode(br.readLine());//读取明文的服务器公钥
            BCECPublicKey publicKey = BCECUtil.convertX509ToECPublicKey(SPKey);//转换公钥
            if (SM2Util.verify(publicKey,enSessionKey,sig)){

                String answer = SM2.encrypt("true", publicKey);
                bw.write(answer);//写入响应数据
                bw.newLine();
                bw.flush();
                sessionKey = SM2Util.decrypt(sm2ClientKey.getPrivateKey(), enSessionKey);//解密会话密钥
                an=true;
            }else {

                String answer = SM2.encrypt("false", publicKey);
                bw.write(answer);//写入响应数据
                bw.newLine();
                bw.flush();
            }
            sum++;
        } while (!an && sum <= 5);
        connect=an;
        return an;
    }

}
