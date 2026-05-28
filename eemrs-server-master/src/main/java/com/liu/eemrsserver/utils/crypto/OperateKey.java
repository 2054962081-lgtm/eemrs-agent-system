package com.liu.eemrsserver.utils.crypto;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.utils.FileUtil;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.zz.gmhelper.BCECUtil;
import org.zz.gmhelper.SM2Util;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author L
 * @date 2019-09-20 21:37
 * @desc
 **/
public class OperateKey {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static SMServerKey getSMServerKeyFromFile(){
        try {
            byte[] sm4Key = FileUtil.readFile("SM2KeyPair/sm4.key");
            byte[] opeKey = FileUtil.readFile("SM2KeyPair/ope.key");
            byte[] sm2PubKeyByte =  FileUtil.readFile("SM2KeyPair/ec.x509.pub.der");
            BCECPublicKey sm2PubKey = BCECUtil.convertX509ToECPublicKey(sm2PubKeyByte);

            byte[] sm2PriKeyByte = FileUtil.readFile("SM2KeyPair/ec.pkcs8.pri.der");
            BCECPrivateKey sm2PriKey = BCECUtil.convertPKCS8ToECPrivateKey(sm2PriKeyByte);

            return new SMServerKey(sm2PubKey,sm2PriKey,sm4Key,opeKey);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return new SMServerKey();
    }

    public static byte[] getSM4Key(){

        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance("SM4", "BC");
            return keyGenerator.generateKey().getEncoded();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }
        return new byte[16];
    }

    public static byte[] getOPEKey(){
        BoldyrevaCipher boldyrevaCipher = new BoldyrevaCipher();
        return boldyrevaCipher.generateKey();
    }

    public static SecretKey toSM4Key(byte[] key) {
        SecretKey secretKey = new SecretKeySpec(key, "SM4");
        return secretKey;
    }

    public static  Map<String, byte[]> getSM2Key(){
        Map<String, byte[]> out = new HashMap<>();
        AsymmetricCipherKeyPair keyPair = SM2Util.generateKeyPairParameter();
        ECPrivateKeyParameters priKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        ECPublicKeyParameters pubKey = (ECPublicKeyParameters) keyPair.getPublic();
        byte[] priKeyPkcs8Der = BCECUtil.convertECPrivateKeyToPKCS8(priKey, pubKey);
        out.put("private",priKeyPkcs8Der);
        byte[] pubKeyX509Der = BCECUtil.convertECPublicKeyToX509(pubKey);
        out.put("public",pubKeyX509Der);
        return out;
    }

    /**
     * 转化SM2的公钥
     * @param publicKey
     * @return
     */
    @NotNull
    public static BCECPublicKey toSM2PublicKey(byte[] publicKey){
        if(publicKey==null){

            return null;
        }
        try {
            BCECPublicKey sm2PubKey = BCECUtil.convertX509ToECPublicKey(publicKey);
            return sm2PubKey;
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 转换SM2的私钥
     * @param privateKey
     * @return
     */
    @NotNull
    public static BCECPrivateKey toSM2PrivateKey(byte[] privateKey){
        if(privateKey==null){
            return null;
        }
        try {
            BCECPrivateKey sm2PriKey = BCECUtil.convertPKCS8ToECPrivateKey(privateKey);
            return sm2PriKey;
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void genSMServerKeyToFile() {
        try {
            Map<String, byte[]> keyPair = OperateKey.getSM2Key();
            byte[] sm4Key = OperateKey.getSM4Key();
            byte[] opeKey = OperateKey.getOPEKey();
            FileUtil.writeFile("SM2KeyPair/ec.pkcs8.pri.der", keyPair.get("private"));
            FileUtil.writeFile("SM2KeyPair/ec.x509.pub.der", keyPair.get("public"));
            FileUtil.writeFile("SM2KeyPair/sm4.key", sm4Key);
            FileUtil.writeFile("SM2KeyPair/ope.key", opeKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
