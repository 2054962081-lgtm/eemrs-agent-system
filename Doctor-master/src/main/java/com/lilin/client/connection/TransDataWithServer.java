package com.lilin.client.connection;

import com.alibaba.fastjson.JSON;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.ClientData;
import com.lilin.client.utils.crypto.SM4_String;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Arrays;

@AllArgsConstructor
@NoArgsConstructor
public class TransDataWithServer {


    @Getter@Setter
    public BufferedReader br;
    @Getter@Setter
    public BufferedWriter bw;
    @Getter@Setter
    public byte[] sessionKey;

    public AnswerData trans(ClientData clientData,Integer answerCode) throws Exception {
        String data = JSON.toJSONString(clientData);
        System.out.println(Arrays.toString(sessionKey));
        String enData = SM4_String.encWithIV(data, sessionKey);
        bw.write(enData);
        bw.newLine();
        bw.flush();

        String enAnswerData = br.readLine();
        String answerData = SM4_String.decWithIV(enAnswerData, sessionKey);
        AnswerData answerData1 = JSON.parseObject(answerData, AnswerData.class);
        System.out.println(answerData1);
        if (answerCode==answerData1.getAnswerCode()){
            return answerData1;
        }else {
            return null;
        }
    }
}