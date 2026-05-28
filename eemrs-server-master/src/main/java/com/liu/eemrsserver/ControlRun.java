package com.liu.eemrsserver;

import com.alibaba.fastjson.JSON;
import com.liu.eemrsserver.config.SMServerKey;
import com.liu.eemrsserver.crypto.SessionKey;
import com.liu.eemrsserver.domain.DoctorInfo;
import com.liu.eemrsserver.domain.GuahaoInfo;
import com.liu.eemrsserver.domain.PatientInfo;
import com.liu.eemrsserver.domain.VisitInfo;
import com.liu.eemrsserver.jsontrans.*;
import com.liu.eemrsserver.module.dataOp.DoPatientInfo;
import com.liu.eemrsserver.module.dataOp.InsertData;
import com.liu.eemrsserver.module.dataOp.QueryData;
import com.liu.eemrsserver.module.guahao.Guahao;
import com.liu.eemrsserver.module.session.BuildSession;
import com.liu.eemrsserver.module.userop.Login;
import com.liu.eemrsserver.module.userop.Logout;
import com.liu.eemrsserver.module.userop.Register;
import com.liu.eemrsserver.service.DataOpService;
import com.liu.eemrsserver.service.GuahaoService;
import com.liu.eemrsserver.service.PatientInfoService;
import com.liu.eemrsserver.service.UserOpService;
import com.liu.eemrsserver.utils.GetExceptionMessage;
import com.liu.eemrsserver.utils.NewPair;
import com.liu.eemrsserver.utils.Pair;
import com.liu.eemrsserver.utils.ReadClientData;
import com.liu.eemrsserver.utils.crypto.sm4.SM4_String;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * @author L
 * @date 2019-09-21 11:32
 * @desc
 **/
@Component
@Scope("prototype")
public class ControlRun implements Runnable {
    private static Logger logger = Logger.getLogger(ControlRun.class);

    private boolean isLog=false;//标识当前用户是否登录
    private String userType;//用户类型

    @Setter
    @Getter
    private Socket socket;
    private SessionKey sessionKey;
    @Autowired
    private SMServerKey smServerKey;
    @Autowired
    private Map<Integer, Integer> codeMap;
    @Autowired
    private UserOpService userOpService;
    @Autowired
    private GuahaoService guahaoService;
    @Autowired
    private DataOpService dataOpService;
    @Autowired
    private PatientInfoService patientInfoService;

    @Override
    public void run() {
        try {
            //生成会话密钥
            sessionKey =new SessionKey();
            if (!BuildSession.build(socket,sessionKey,smServerKey)) {
                Thread.currentThread().stop();
            }
            logger.debug("密钥协商成功");
            logger.info("连接成功，等待操作数据");
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            ClientData data = null;
            while (true) {
                data = ReadClientData.read(br, sessionKey.getKey());//读取数据

                if (16 != data.getOpCode()) {
                    logger.info("操作码：[" + data.getOpCode() + "]：  即将进入功能区");
                } else {
                    logger.info("操作码：[16]  退出系统");
                }
                switch (data.getOpCode()) {//Determine the type of opcode
                    case 11: {//查询病人信息
                        if (!isLog) {
                            break;
                        }
                        logger.info("开始查询信息");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        QueryConditions queryConditions = JSON.parseObject(data.getUserOP(), QueryConditions.class);
                        System.out.println(queryConditions);

                        QueryData queryData = new QueryData(dataOpService, sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String enAnswer = queryData.query(queryConditions, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("查询完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(enAnswer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");

                        break;
                    }

                    case 12: {//插入病人信息
                        if (!isLog) {
                            break;
                        }
                        logger.info("插入就诊信息");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        VisitInfo patientInfo = JSON.parseObject(data.getUserOP(), VisitInfo.class);
                        InsertData insertData = new InsertData(dataOpService, sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        Pair<String,Boolean> pair = insertData.insertInto(patientInfo, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("插入完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(pair.getKey());
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        logger.info("删除挂号信息");
                        if(pair.getValue()){
                            int sum=0;
                            while (sum<5){
                                if(guahaoService.delectGuaHao(patientInfo.getPatientIdNumber())){
                                    sum++;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                    case 13: {//用户登录
                        logger.info("用户登录");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码
                        UserLog deUserOp = JSON.parseObject(data.getUserOP(), UserLog.class);//解密JSON转POJO
                        this.userType = deUserOp.getType();
                        Login login = new Login(userOpService, sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String enData = login.login(deUserOp, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("登录完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        isLog = login.isLogin();//设置登录的状态
                        bw.write(enData);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    case 14: {//用户注册
                        logger.info("用户创建");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        UserLog deUserOp = JSON.parseObject(data.getUserOP(), UserLog.class);//解密JSON转POJO
                        this.userType = deUserOp.getType();
                        Register register = new Register(userOpService, sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String enData = register.regist(deUserOp, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("创建完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(enData);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;//退出当前功能区
                    }
                    case 15: {//用户注销
                        if (!isLog) {
                            break;
                        }
                        logger.info("用户注销");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        UserLog deUserOp = JSON.parseObject(data.getUserOP(), UserLog.class);//解密JSON转POJO
                        this.userType = deUserOp.getType();
                        Logout logout = new Logout(userOpService, sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String enData = logout.delete(deUserOp, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("注销完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(enData);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        isLog = !logout.isSuccess();//设置登录的状态
                        break;
                    }
                    case 16: {//用户退出系统
                        logger.info("用户正常退出，当前线程关闭");
                        Thread.currentThread().stop();
                        break;
                    }
                    case 17: {
                        logger.info("用户异常退出，当前线程关闭");
                        Thread.currentThread().stop();
                        break;
                    }

                    case 5:{//返回科室的医生姓名
                        if (!isLog) {
                            break;
                        }
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码
                        String department = data.getUserOP();
                        List<DoctorInfo> docName = dataOpService.getDocName(department);
                        AnswerData answerData = new AnswerData(answerCode, true, JSON.toJSONString(docName));//封装信息
                        String JSONData = JSON.toJSONString(answerData);
                        String enData = SM4_String.encWithIV(JSONData, sessionKey.getKey());
                        bw.write(enData);
                        bw.newLine();
                        bw.flush();
                        break;
                    }

                    case 31: {//病人挂号
                        if (!isLog) {
                            break;
                        }
                        logger.info("病人挂号中。。。");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码
                        GuahaoInfo guahaoInfo = JSON.parseObject(data.getUserOP(), GuahaoInfo.class);//获得挂号数据
                        Guahao guahao = new Guahao(guahaoService, sessionKey.getKey());//获取一个挂号类的示例
                        long start = System.currentTimeMillis();
                        String answer = guahao.add(guahaoInfo, answerCode);//执行挂号操作，并得到挂号结果
                        long end = System.currentTimeMillis();
                        logger.info("挂号完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }


                    case 32: {//获取当前科室的挂号信息
                        if (!isLog) {
                            break;
                        }
                        logger.info("传输挂号信息中。。。");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        NewPair newPair = JSON.parseObject(data.getUserOP(), NewPair.class);
                        Guahao guahao = new Guahao(guahaoService, sessionKey.getKey());//获取一个挂号类的示例

                        long start = System.currentTimeMillis();
                        String answer = guahao.query(newPair, answerCode);

                        long end = System.currentTimeMillis();
                        logger.info("获取完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }

                    case 33: {
                        if (!isLog) {
                            break;
                        }
                        logger.info("接诊一位病人。。。");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码
                        String idNumber = data.getUserOP();
                        Guahao guahao = new Guahao(guahaoService, sessionKey.getKey());//获取一个挂号类的示例
                        long start = System.currentTimeMillis();
                        String answer = guahao.accept(idNumber, answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("接诊完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    case 18:{
                        if (!isLog) {
                            break;
                        }
                        logger.info("修改密码。。。");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码
                        PasswordModify modify = JSON.parseObject(data.getUserOP(),PasswordModify.class);
                        DoPatientInfo doPatientInfo = new DoPatientInfo(patientInfoService,sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String answer= doPatientInfo.modifyPassword(modify,answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("修改密码完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    case 19:{
                        if (!isLog) {
                            break;
                        }
                        logger.info("病人修改信息。。。");
                        Integer answerCode = codeMap.get(data.getOpCode());//获取响应码

                        PatientInfo patientInfo = JSON.parseObject(data.getUserOP(), PatientInfo.class);
                        DoPatientInfo doPatientInfo = new DoPatientInfo(patientInfoService,sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String answer= doPatientInfo.modifyInfo(patientInfo,answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("信息修改完毕，准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    case 1:{
                        if (!isLog) {
                            break;
                        }
                        logger.info("发送病人信息。。。");
                        int answerCode = codeMap.get(data.getOpCode());//获取响应码
                        String id = data.getUserOP();
                        Guahao guahao = new Guahao(guahaoService,sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String answer= guahao.sendPatientInfo(id,answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    case 3:{
                        if (!isLog) {
                            break;
                        }
                        logger.info("发送医生信息。。。");
                        int answerCode = codeMap.get(data.getOpCode());//获取响应码
                        String id = data.getUserOP();
                        QueryData queryData = new QueryData(dataOpService,sessionKey.getKey());
                        long start = System.currentTimeMillis();
                        String answer= queryData.sendDoctorInfo(id,answerCode);
                        long end = System.currentTimeMillis();
                        logger.info("准备返回数据。过程用时"+(end-start)+"ms");
                        bw.write(answer);
                        bw.newLine();
                        bw.flush();
                        logger.info("结果已返回");
                        break;
                    }
                    default: {
                        logger.info("操作码不合法，当前线程关闭");
                        Thread.currentThread().stop();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.info("\n"+ GetExceptionMessage.getMessage(e));
        }
    }
}
