package com.lilin.client.utils.crypto;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.zz.gmhelper.SM2Util;

import java.util.Base64;

public class SM2 {
    public static String encrypt(String data, BCECPublicKey publicKey) {
        try {
            return Base64.getEncoder().encodeToString(
                    SM2Util.encrypt(publicKey, data.getBytes())
            );
        } catch (InvalidCipherTextException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String decrypt(String data, BCECPrivateKey privateKey) {
        try {
            return new String(
                    SM2Util.decrypt(privateKey, Base64.getDecoder().decode(data))
            );
        } catch (InvalidCipherTextException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String sign(String data, BCECPrivateKey privateKey) {
        try {

            return Base64.getEncoder().encodeToString(
                    SM2Util.sign(privateKey, data.getBytes())
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean verify(String data, String sign, BCECPublicKey publicKey) {
        try {
            return SM2Util.verify(
                    publicKey,
                    data.getBytes(),
                    Base64.getDecoder().decode(sign)
                    );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
