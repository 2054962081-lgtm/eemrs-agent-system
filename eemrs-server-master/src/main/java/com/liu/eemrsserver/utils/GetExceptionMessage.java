package com.liu.eemrsserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class GetExceptionMessage {
    public static String getMessage(Exception e){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(bos));
        String message = bos.toString();
        return message;
    }
}
