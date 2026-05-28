package com.liu.eemrsserver;

import com.liu.eemrsserver.config.ApplicationContextProvider;
import com.liu.eemrsserver.config.SMServerKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * @author L
 * @date 2019-09-19 14:14
 * @desc testdatasource
 **/
@RunWith(SpringRunner.class)
@SpringBootTest
public class TestDataSource {
    @Autowired
    private DataSource dataSource;
    @Autowired
    private SMServerKey smServerKey;
    @Test
    public void test() throws SQLException {
    }

    @Test
    public void test2(){
        ControlThread thread= ApplicationContextProvider.getBean(ControlThread.class);
        ControlThread thread1=ApplicationContextProvider.getBean(ControlThread.class);
        System.out.println(thread.getControlRun()==thread1.getControlRun());

    }
}
