package com.liu.eemrsserver.utils.crypto.sm4;

import com.liu.eemrsserver.utils.crypto.OperateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.security.Security;

/**
 * @author L
 * @date 2019-09-20 21:57
 * @desc
 **/
public class SM4 {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    /**
     * encrypt data without v
     * @param data
     * @return
     * @throws Exception
     */
    public static byte[] encWithoutIV(byte[] data,byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding","BC");
            cipher.init(Cipher.ENCRYPT_MODE, OperateKey.toSM4Key(key));
            return cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * decrypt data without v
     * @param data
     * @return
     * @throws Exception
     */
    public static byte[] decWithoutIV(byte[] data,byte[] key){
        try {
            Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding","BC");
            cipher.init(Cipher.DECRYPT_MODE,OperateKey.toSM4Key(key));
            return cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encWithIV(byte[] data,byte[] key){
        try {
            Cipher cipherNoIV = Cipher.getInstance("SM4/ECB/NoPadding","BC");
            cipherNoIV.init(Cipher.ENCRYPT_MODE,OperateKey.toSM4Key(key));

            Cipher cipherIV = Cipher.getInstance("SM4/CBC/PKCS5Padding","BC");

            byte[] iv = new byte[cipherIV.getBlockSize()];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);
            byte[] en_iv = cipherNoIV.doFinal(iv);
            cipherIV.init(Cipher.ENCRYPT_MODE,OperateKey.toSM4Key(key), new IvParameterSpec(iv));
            byte[] endata = cipherIV.doFinal(data);
            byte[] out = new byte[en_iv.length+endata.length];
            System.arraycopy(en_iv, 0, out, 0, en_iv.length);
            System.arraycopy(endata, 0, out, en_iv.length, endata.length);
            return out;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decWithIV(byte[] data,byte[] key){
        try {
            Cipher cipherNoIV = Cipher.getInstance("SM4/ECB/NoPadding","BC");
            cipherNoIV.init(Cipher.DECRYPT_MODE,OperateKey.toSM4Key(key));

            Cipher cipherIV = Cipher.getInstance("SM4/CBC/PKCS5Padding","BC");

            byte[] en_iv = new byte[cipherIV.getBlockSize()];

            System.arraycopy(data, 0, en_iv, 0, en_iv.length);
            byte[] iv = cipherNoIV.doFinal(en_iv);

            byte[] src = new byte[data.length-en_iv.length];
            System.arraycopy(data, en_iv.length, src, 0, src.length);
            cipherIV.init(Cipher.DECRYPT_MODE,OperateKey.toSM4Key(key), new IvParameterSpec(iv));
            return cipherIV.doFinal(src);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
