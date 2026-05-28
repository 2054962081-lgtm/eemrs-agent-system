package com.liu.eemrsserver.utils.crypto.sm4;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.Base64;

/**
 * @author L
 * @date 2019-09-20 21:57
 * @desc
 **/
public class SM4_String {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    /**
     * encrypt data without v
     * @param data
     * @return
     * @throws Exception
     */
    public static String encWithoutIV(String data,byte[] key){

        return Base64.getEncoder().encodeToString(
                SM4.encWithoutIV(data.getBytes(), key)
        );
    }

    /**
     * decrypt data without v
     * @param data
     * @return
     * @throws Exception
     */
    public static String decWithoutIV(String data,byte[] key){

        return new String(
                SM4.decWithoutIV(Base64.getDecoder().decode(data), key)
        );
    }

    public static String encWithIV(String data,byte[] key){

        return Base64.getEncoder().encodeToString(
                SM4.encWithIV(data.getBytes(), key)
        );
    }

    public static String decWithIV(String data,byte[] key){
        return new String(
                SM4.decWithIV(Base64.getDecoder().decode(data), key)
        );
    }
}
