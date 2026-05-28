package com.liu.eemrsserver;

import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.BoldyrevaCipher;
import com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva.Range;
import org.junit.Test;

import javax.sound.midi.Soundbank;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class TestBoldyreva {

    @Test
    public void test1() throws Exception {

        BigInteger num = BigInteger.valueOf(12345);
        BigInteger num0 = BigInteger.valueOf(12456);
        BigInteger num1 = BigInteger.valueOf(23456);
        BoldyrevaCipher boldyrevaCipher = new BoldyrevaCipher();
        byte[] key = boldyrevaCipher.generateKey();
        System.out.println(Arrays.toString(key));

        BigInteger en_num = boldyrevaCipher.encrypt(num, key);
        System.out.println(en_num);

        BigInteger en_num0 = boldyrevaCipher.encrypt(num0,key);
        System.out.println(en_num0);

        BigInteger en_num1 = boldyrevaCipher.encrypt(num1, key);
        System.out.println(en_num1);


        System.out.println(boldyrevaCipher.decrypted(en_num,key));
        System.out.println(boldyrevaCipher.decrypted(en_num0,key));
        System.out.println(boldyrevaCipher.decrypted(en_num1,key));



    }

    @Test
        public void testOPE() throws Exception {


        System.out.println(
                BigInteger.valueOf(
                        new Date().getTime()
        ).bitLength()
    );

        Range inRange = new Range(BigInteger.ZERO,BigInteger.valueOf(2).pow(41).subtract(BigInteger.ONE));
        Range outRange = new Range(BigInteger.ZERO,BigInteger.valueOf(2).pow(90).subtract(BigInteger.ONE));
        BoldyrevaCipher boldyrevaCipher = new BoldyrevaCipher(inRange,outRange);
        byte[] key = boldyrevaCipher.generateKey();

        SimpleDateFormat dateFormat = new SimpleDateFormat("YYYYMMDD");

        BigInteger en = boldyrevaCipher.encrypt(BigInteger.valueOf(
                dateFormat.parse("20251212").getTime()
        ),key);
        System.out.println(dateFormat.parse("20251212").getTime());
        System.out.println(en);
        System.out.println(en.bitLength());
        System.out.println(boldyrevaCipher.decrypted(en,key));


    }

}
