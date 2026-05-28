package com.liu.eemrsserver.utils.crypto;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.utils.crypto.sm4.SM4;

import java.util.Base64;

/**
 * 将密文POJO解密为对应的javabean
 * @author L
 * @date 2019-09-21 12:08
 * @desc
 **/
public class POJOTrans {
    public static<T> T transDecPOJO(String enc_POJO,byte[] key, Class<T> clazz) throws Exception {
        byte[] data = Base64.getDecoder().decode(enc_POJO);//解码密文JSON数据
        String pojo =  new String(SM4.decWithIV(data, key));//解密密文JSON数据
        return JSON.parseObject(pojo,clazz);//转换为POJO对象并返回
    }

//    /**
//     * 将一个javabean的json对象加密成密文字符串
//     * @param JSON
//     * @param key
//     * @return
//     * @throws Exception
//     */
//    public static String transEncPOJO(String JSON,byte[] key) throws Exception {
//       return new String(SM4.encWithIV(JSON.getBytes(), key));//加密JSON数据
//    }
}
