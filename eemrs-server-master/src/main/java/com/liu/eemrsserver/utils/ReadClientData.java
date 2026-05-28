package com.liu.eemrsserver.utils;

import com.liu.eemrsserver.jsontrans.ClientData;
import com.liu.eemrsserver.utils.crypto.POJOTrans;
import org.apache.log4j.Logger;

import java.io.BufferedReader;

/**
 * @author L
 * @date 2019-09-25 6:56
 * @desc 读取用户发来的数据，包括操作码和操作数据
 **/
public class ReadClientData {
    private static Logger logger = Logger.getLogger(ReadClientData.class);
    public static ClientData read(BufferedReader br, byte[] sessionKey){
        try {
            ClientData  data = POJOTrans.transDecPOJO(br.readLine(),sessionKey,ClientData.class);
            return data;
            //read operation code from inputstream and decrypt it
        } catch (Exception e) {
            logger.info("\n"+GetExceptionMessage.getMessage(e));



        }
        return new ClientData(17,null);
    }

}
