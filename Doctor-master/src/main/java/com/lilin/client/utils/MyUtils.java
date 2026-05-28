package com.lilin.client.utils;

import com.alibaba.fastjson.JSON;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.ClientData;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lilin
 * @date 2019/10/14  -  9:24 下午
 */
public class MyUtils {
    private static Map<String,Object> param=new HashMap<>();
    public static Map<String, Object> getParam(){
        return param;
    }
    public static <T> AnswerData transData(TransDataWithServer tdws, T t, int opCode, int answerCode){
        String userJSON = JSON.toJSONString(t);
        ClientData clientData = new ClientData(opCode, userJSON);
        AnswerData answerData = null;
        try {
            answerData = tdws.trans(clientData, answerCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return answerData;
    }
}
