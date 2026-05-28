package com.lilin.client.DoctorControllers;

/**
 * @author lilin
 * @date 2019/10/9  -  7:32 下午
 */

import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.ConnectionWithServer;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.crypto.SM2ClientKey;
import com.lilin.client.pojo_contr.*;
import com.lilin.client.utils.*;
import com.lilin.client.utils.crypto.OperateKey;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class DoctorJiezhenController {

    @FXML
    private Button jiezhen;


    @FXML
    private ComboBox<String> patientList;

    @FXML
    private Button back;
    @FXML
    private Button submit;
    @FXML
    private Button submit2;

    @FXML
    private Button back2;

     @FXML
    private  TextArea medicine;
       @FXML
        private TextField date;

        @FXML
        private TextField doctor;

        @FXML
        private TextField sex;

        @FXML
        private TextField patientID;


        @FXML
        private TextArea description;

        @FXML
        private TextField userName;

        @FXML
        private TextField idNumber;

        @FXML
        private TextField department;

        @FXML
        private TextField age;


    private ConnectionWithServer cws = ConnectionWithServerFactory.getConnectionWithServer();
    private  TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();
    private SM2ClientKey sm2ClientKey= OperateKey.getSM2ClientKeyFromFile();
    private Map<String,Object> param = MyUtils.getParam();


    @FXML
    void handleClickJiezhen(ActionEvent event) throws Exception {
        if(patientList.getValue().equals("请选择待接诊病人")){
            ShowAlert showAlert= new ShowAlert("请选择要接诊的病人");
        }else {

            int code1 = 33, answer1 = 43;
            String name = patientList.getValue();
            String currentId =((List<Waiting>)MyUtils.getParam().get("waitings")).stream()
                    .filter(w->{
                        return w.getUserName().equals(name);
                    })
                    .map(w->{
                        return w.getIdNumber();
                    })
                    .collect(Collectors.toList()).get(0);
            ClientData clientData1 = new ClientData(code1, currentId);
            AnswerData trans = tdws.trans(clientData1, answer1);
            PatientInfo patientInfo = JSON.parseObject(trans.getAnswerInfo(),PatientInfo.class);
            MyUtils.getParam().put("patientInfo",patientInfo);
            MyUtils.getParam().put("success2",trans.isSuccess());
            System.out.println(patientInfo);
            System.out.println(trans.getAnswerInfo());

            ShowAlert showAlert = new ShowAlert("接诊成功");
            ChangeView changeView = new ChangeView("DoctorViews/DoctorInsert.fxml","病历信息完善", event);
        }

    }


    @FXML
    public void handleClickBack(ActionEvent event) throws IOException {
        ChangeView changeView = new ChangeView("DoctorViews/DoctorIndex.fxml", "主界面",event);
    }


    public  void initialize() throws Exception {
        DoctorInfo doctorInfo = (DoctorInfo) param.get("doctorInfo");
        Integer opCode = 32, answerCode = 42;
        NewPair pair = new NewPair(doctorInfo.getDepartment(),doctorInfo.getIdNumber());
        ClientData clientData = new ClientData(opCode, JSON.toJSONString(pair));
        AnswerData answerData = tdws.trans(clientData, answerCode);
        String usersJSON = answerData.getAnswerInfo();
         List<Waiting> users = JSON.parseArray(usersJSON, Waiting.class);
         MyUtils.getParam().put("waitings",users);
        if (!users.isEmpty()) {
            for (int i = 0; i < users.size(); i++) {
                patientList.getItems().add(users.get(i).getUserName());
            }
        }

    }





}
