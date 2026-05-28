package com.lilin.client.DoctorControllers;


import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.*;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.Pair;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DoctorSearchController {
    @FXML
    private TextField beginAge;

    @FXML
    private DatePicker beginDate;

    @FXML
    private TextField patientName;

    @FXML
    private Button search;

    @FXML
    private TextField endAge;

    @FXML
    private DatePicker endDate;

    @FXML
    private Button back;


    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    void handleClickSearch(ActionEvent event) throws Exception {


             MyUtils.getParam().put("beginDate", beginDate.getValue());
             MyUtils.getParam().put("endDate", endDate.getValue());

             DoctorInfo doctorInfo = (DoctorInfo) MyUtils.getParam().get("doctorInfo");
             int queryOpCode = 11, queryAnswerCode = 21;
             QueryConditions queryConditions = new QueryConditions();//初始化一个条件类
             //开始加入查询条件
             queryConditions.setDoctorIdNumber(doctorInfo.getIdNumber());
             queryConditions.setDoctorName(doctorInfo.getUserName());
             queryConditions.setDepartment(doctorInfo.getDepartment());
             if(!patientName.getText().equals("")){
                 queryConditions.setPatientName(patientName.getText());
             }
             if(!beginAge.getText().equals("")&&!endAge.getText().equals("")){
                 queryConditions.setAgeInterval(new Pair<>(BigInteger.valueOf(Integer.parseInt(beginAge.getText())), BigInteger.valueOf(Integer.parseInt(endAge.getText()))));

             }
             if(beginDate.getValue()!=null&&endDate.getValue()!=null){
                 SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                 Date min = dateFormat.parse(beginDate.getValue().toString());
                 Date max = dateFormat.parse(endDate.getValue().toString());
                 queryConditions.setTimeInterval(new Pair<>(BigInteger.valueOf(min.getTime()), BigInteger.valueOf(max.getTime())));

             }

             String opData = JSON.toJSONString(queryConditions);
             ClientData clientData = new ClientData(queryOpCode, opData);
             AnswerData answerData_S = tdws.trans(clientData, queryAnswerCode);
             List<VisitInfo> patientInfos = JSON.parseArray(answerData_S.getAnswerInfo(), VisitInfo.class);
             patientInfos.forEach(System.out::println);
             MyUtils.getParam().put("patientInfos", patientInfos);
             MyUtils.getParam().put("success1", answerData_S.isSuccess());
             if ((boolean) MyUtils.getParam().get("success1")) {
                 if (patientInfos.isEmpty()) {
                     ShowAlert showAlert = new ShowAlert("没有满足查询条件的记录");


                 } else {
                     ChangeView changeView = new ChangeView("DoctorViews/DoctorSearchResult.fxml", "查询结果", event);
                 }
             }



    }
@FXML
public void initialize(){




}



    @FXML
    void handleClickBack(ActionEvent event) throws IOException {
        ChangeView changeView =new ChangeView("DoctorViews/DoctorIndex.fxml","主界面",event);

    }


}
