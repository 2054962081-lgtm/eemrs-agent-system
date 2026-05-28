package com.liu.eemrsserver.utils.crypto;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.jcajce.provider.symmetric.ARC4;
import org.zz.gmhelper.SM3Util;

import java.util.Arrays;
import java.util.Base64;

/**
 * @author L
 * @date 2019-09-22 10:32
 * @desc
 **/
public class SM3 {

    public static String hash(String data){
        return Base64.getEncoder().encodeToString(SM3Util.hash(data.getBytes()));
    }
}
