package com.liu.eemrsserver;

import com.liu.eemrsserver.crypto.DataOpCrypto;
import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.domain.DocLog;
import com.liu.eemrsserver.mapper.DataOpMappper;
import com.liu.eemrsserver.mapper.GuahaoMapper;
import com.liu.eemrsserver.mapper.UserLogMapper;
import com.liu.eemrsserver.jsontrans.QueryConditions;
import com.liu.eemrsserver.utils.Pair;
import com.liu.eemrsserver.utils.crypto.JavaBeanEnc;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author L
 * @date 2019-09-27 12:21
 * @desc
 **/
@RunWith(SpringRunner.class)
@SpringBootTest
public class TestMapper {
    @Autowired
    private UserLogMapper userOpMapper;

    @Autowired
    private GuahaoMapper guahaoMapper;
    @Autowired
    private SMServerKey smServerKey;
    @Autowired
    private DataOpMappper dataOpMappper;
    @Autowired
    private DataOpCrypto dataOpCrypto;







    @Test
    public void testDataOpMapper1() {
        VisitInfo patientInfo = new VisitInfo("dawda", "dawda", "dawda", "dawda", BigInteger.valueOf(132121351), "dawda", "dawda", "dawda", BigInteger.valueOf(61), "dawda", "dawda", "dawda", "dawda", "dawda", "dawda");
        System.out.println(dataOpMappper.insert(patientInfo));
    }
    @Test
    public void testDataOpMapper2() throws ParseException {
        QueryConditions queryConditions = new QueryConditions();//初始化一个条件类
        //开始加入查询条件
        queryConditions.setDoctorIdNumber("741528744584125478");
        queryConditions.setPatientIdNumber("16545316813213");
        queryConditions.setAgeInterval(new Pair<>(BigInteger.valueOf(35), BigInteger.valueOf(55)));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date min = dateFormat.parse("2019-05-16");
        System.out.println(min.getTime());
        Date max = dateFormat.parse("2019-11-15");
        System.out.println(max.getTime());
        queryConditions.setTimeInterval(new Pair<>(BigInteger.valueOf(min.getTime()), BigInteger.valueOf(max.getTime())));
        queryConditions.setDepartment("外科");


        System.out.println(dataOpCrypto.query(queryConditions));
    }
}
