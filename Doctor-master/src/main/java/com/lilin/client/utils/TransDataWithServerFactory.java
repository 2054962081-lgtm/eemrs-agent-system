package com.lilin.client.utils;

import com.lilin.client.connection.TransDataWithServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * @author lilin
 * @date 2019/10/14  -  11:59 上午
 */
public class TransDataWithServerFactory {
    private static TransDataWithServer transDataWithServer=new TransDataWithServer();
    private TransDataWithServerFactory(){};
    public static void init(BufferedWriter bw, BufferedReader br,byte[] key){
        if (transDataWithServer.getBr()==null) {
            transDataWithServer.setBr(br);
            transDataWithServer.setBw(bw);
            transDataWithServer.setSessionKey(key);

        }else {
            throw new RuntimeException("不能修改的配置");
        }
    }

    public static TransDataWithServer getTransDataWithServer(){
        if (transDataWithServer==null) {
            throw new RuntimeException("没有初始化");
        }else {
            return transDataWithServer;
        }
    }
}

