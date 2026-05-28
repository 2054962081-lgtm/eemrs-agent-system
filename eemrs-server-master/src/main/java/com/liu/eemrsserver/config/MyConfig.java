package com.liu.eemrsserver.config;

import com.liu.eemrsserver.utils.crypto.OperateKey;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.Range;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * @author L
 * @date 2019-09-21 19:48
 * @desc
 **/
@Configuration
public class MyConfig {

    @Bean
    public BoldyrevaCipher boldyrevaCipher(){
        Range inRange = new Range(BigInteger.ZERO,BigInteger.valueOf(2).pow(48).subtract(BigInteger.ONE));
        Range outRange = new Range(BigInteger.ZERO,BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE));
        return new BoldyrevaCipher(inRange,outRange);
    }

    @Bean
    public SMServerKey smServerKey(){
        return OperateKey.getSMServerKeyFromFile();
    }

    @Bean("codeMap")
    public Map<Integer,Integer> codeMap(){
        return new HashMap<Integer, Integer>(){
            {
                put(11, 21);
                put(12, 22);
                put(13, 23);
                put(14, 24);
                put(15, 25);
                put(31,41);
                put(32,42);
                put(33,43);
                put(18,28);
                put(19,29);
                put(1, 2);
                put(3,4);
                put(5,6);
            }
        };
    }
}
