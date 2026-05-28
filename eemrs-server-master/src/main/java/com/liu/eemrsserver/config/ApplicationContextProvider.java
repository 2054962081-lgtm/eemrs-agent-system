package com.liu.eemrsserver.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author L
 * @date 2019-09-22 20:21
 * @desc
 **/
@Component
public class ApplicationContextProvider implements ApplicationContextAware {
    private static ApplicationContext context;
    private ApplicationContextProvider(){}
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }
    public  static <T> T getBean(Class<T> clazz){
        return context.getBean(clazz);
    }
}
