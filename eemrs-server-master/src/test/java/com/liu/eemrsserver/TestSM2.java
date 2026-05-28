package com.liu.eemrsserver;

import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.utils.crypto.OperateKey;
import com.liu.eemrsserver.utils.crypto.SM2;
import org.junit.Assert;
import org.junit.Test;

public class TestSM2 {
    @Test
    public void testSM2() {
        SMServerKey smServerKey = OperateKey.getSMServerKeyFromFile();
        String data = "hello word";
        String sign = SM2.sign(data,smServerKey.getPrivateKey());
        Assert.assertFalse(!SM2.verify(data,sign,smServerKey.getPublicKey()));

        String enData = SM2.encrypt(data,smServerKey.getPublicKey());
        Assert.assertEquals(SM2.decrypt(enData,smServerKey.getPrivateKey()),data);
    }
}
