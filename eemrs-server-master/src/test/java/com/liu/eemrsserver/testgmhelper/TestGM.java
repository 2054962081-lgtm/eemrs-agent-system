package com.liu.eemrsserver.testgmhelper;


import com.liu.eemrsserver.utils.FileUtil;
import com.liu.eemrsserver.utils.crypto.OperateKey;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.junit.Assert;
import org.junit.Test;
import org.zz.gmhelper.BCECUtil;
import org.zz.gmhelper.SM2Util;


import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

public class TestGM {
    public static final byte[] SRC_DATA = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
    public static final byte[] SRC_DATA_16B = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final byte[] SRC_DATA_24B = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final byte[] SRC_DATA_32B = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8};
    public static final byte[] WITH_ID = new byte[]{1, 2, 3, 4};

    @Test
    public void testGenSM2KeyToFile() {
        Map<String, byte[]> keyPair = OperateKey.getSM2Key();
        try {
            FileUtil.writeFile("SM2KeyPair/ec.pkcs8.pri.der", keyPair.get("private"));
            FileUtil.writeFile("SM2KeyPair/ec.x509.pub.der", keyPair.get("public"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Test
        public void testGetKeyFromFile() throws Exception {
            byte[] sm4Key = FileUtil.readFile("SM2KeyPair/sm4.key");
        System.out.println(Arrays.toString(sm4Key));
        }

    @Test
    public void testGenServerKey() {
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

    @Test
    public void testKeyPairEncoding() {
        try {
            AsymmetricCipherKeyPair keyPair = SM2Util.generateKeyPairParameter();
            ECPrivateKeyParameters priKey = (ECPrivateKeyParameters) keyPair.getPrivate();
            ECPublicKeyParameters pubKey = (ECPublicKeyParameters) keyPair.getPublic();

            byte[] priKeyPkcs8Der = BCECUtil.convertECPrivateKeyToPKCS8(priKey, pubKey);
            System.out.println("private key pkcs8 der length:" + priKeyPkcs8Der.length);
            System.out.println("private key pkcs8 der:" + ByteUtils.toHexString(priKeyPkcs8Der));
            FileUtil.writeFile("D:/ec.pkcs8.pri.der", priKeyPkcs8Der);
            BCECPrivateKey newPriKey = BCECUtil.convertPKCS8ToECPrivateKey(priKeyPkcs8Der);

            String priKeyPkcs8Pem = BCECUtil.convertECPrivateKeyPKCS8ToPEM(priKeyPkcs8Der);
            FileUtil.writeFile("D:/ec.pkcs8.pri.pem", priKeyPkcs8Pem.getBytes("UTF-8"));
            byte[] priKeyFromPem = BCECUtil.convertECPrivateKeyPEMToPKCS8(priKeyPkcs8Pem);
            if (!Arrays.equals(priKeyFromPem, priKeyPkcs8Der)) {
                throw new Exception("priKeyFromPem != priKeyPkcs8Der");
            }


            byte[] priKeyPkcs1Der = BCECUtil.convertECPrivateKeyToSEC1(priKey, pubKey);
            System.out.println("private key pkcs1 der length:" + priKeyPkcs1Der.length);
            System.out.println("private key pkcs1 der:" + ByteUtils.toHexString(priKeyPkcs1Der));
            FileUtil.writeFile("D:/ec.pkcs1.pri", priKeyPkcs1Der);

            byte[] pubKeyX509Der = BCECUtil.convertECPublicKeyToX509(pubKey);
            System.out.println("public key der length:" + pubKeyX509Der.length);
            System.out.println("public key der:" + ByteUtils.toHexString(pubKeyX509Der));
            FileUtil.writeFile("D:/ec.x509.pub.der", pubKeyX509Der);

            String pubKeyX509Pem = BCECUtil.convertECPublicKeyX509ToPEM(pubKeyX509Der);
            FileUtil.writeFile("D:/ec.x509.pub.pem", pubKeyX509Pem.getBytes("UTF-8"));
            byte[] pubKeyFromPem = BCECUtil.convertECPublicKeyPEMToX509(pubKeyX509Pem);
            if (!Arrays.equals(pubKeyFromPem, pubKeyX509Der)) {
                throw new Exception("pubKeyFromPem != pubKeyX509Der");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testSM2KeyRecovery() {
        try {
            AsymmetricCipherKeyPair keyPair = SM2Util.generateKeyPairParameter();
            ECPrivateKeyParameters priKey = (ECPrivateKeyParameters) keyPair.getPrivate();
            ECPublicKeyParameters pubKey = (ECPublicKeyParameters) keyPair.getPublic();

            System.out.println("Pri Hex:"
                    + ByteUtils.toHexString(priKey.getD().toByteArray()).toUpperCase());
            System.out.println("Pub X Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getAffineXCoord().getEncoded()).toUpperCase());
            System.out.println("Pub Y Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getAffineYCoord().getEncoded()).toUpperCase());
            System.out.println("Pub Point Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getEncoded(false)).toUpperCase());
            String priHex = ByteUtils.toHexString(priKey.getD().toByteArray()).toUpperCase();
            String xHex = ByteUtils.toHexString(pubKey.getQ().getAffineXCoord().getEncoded()).toUpperCase();
            String yHex = ByteUtils.toHexString(pubKey.getQ().getAffineYCoord().getEncoded()).toUpperCase();
            String encodedPubHex = ByteUtils.toHexString(pubKey.getQ().getEncoded(false)).toUpperCase();

            byte[] src = ByteUtils.fromHexString("0102030405060708010203040506070801020304050607080102030405060708");
            byte[] withId = ByteUtils.fromHexString("31323334353637383132333435363738");


            ECPrivateKeyParameters priKey1 = new ECPrivateKeyParameters(
                    new BigInteger(ByteUtils.fromHexString(priHex)), SM2Util.DOMAIN_PARAMS);
            ECPublicKeyParameters pubKey1 = BCECUtil.createECPublicKeyParameters(xHex, yHex, SM2Util.CURVE, SM2Util.DOMAIN_PARAMS);
            byte[] signBytes = SM2Util.sign(priKey1, withId, src);
            if (!SM2Util.verify(pubKey1, src, signBytes)) {
                Assert.fail("verify failed");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testEncryptAndDecrypt() {
        try {
            AsymmetricCipherKeyPair keyPair = SM2Util.generateKeyPairParameter();
            ECPrivateKeyParameters priKey = (ECPrivateKeyParameters) keyPair.getPrivate();
            ECPublicKeyParameters pubKey = (ECPublicKeyParameters) keyPair.getPublic();

            System.out.println("Pri Hex:"
                    + ByteUtils.toHexString(priKey.getD().toByteArray()).toUpperCase());
            System.out.println("Pub X Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getAffineXCoord().getEncoded()).toUpperCase());
            System.out.println("Pub X Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getAffineYCoord().getEncoded()).toUpperCase());
            System.out.println("Pub Point Hex:"
                    + ByteUtils.toHexString(pubKey.getQ().getEncoded(false)).toUpperCase());

            byte[] encryptedData = SM2Util.encrypt(pubKey, "南京理工大学".getBytes());
            System.out.println("SM2 encrypt result:\n" + ByteUtils.toHexString(encryptedData));
            byte[] decryptedData = SM2Util.decrypt(priKey, encryptedData);
            System.out.println("SM2 decrypt result:\n" + new String(decryptedData));
            if (!"南京理工大学".equals(new String(decryptedData))) {
                Assert.fail();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail();
        }
    }
}
