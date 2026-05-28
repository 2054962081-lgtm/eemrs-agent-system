package com.liu.eemrsserver;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author L
 * @date 2019-09-22 21:05
 * @desc
 **/
@Component
@Scope("prototype")
public class ControlThread extends Thread {

    @Autowired
    @Getter
    private ControlRun controlRun;
    public ControlThread(){

    }

    @Override
    public void run() {
        controlRun.run();
    }
}
