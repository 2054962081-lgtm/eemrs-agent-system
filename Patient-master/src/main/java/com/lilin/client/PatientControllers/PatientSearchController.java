package com.lilin.client.PatientControllers;

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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;

import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class PatientSearchController {

    @FXML
    private DatePicker beginDate;

    @FXML
    private Button search;

    @FXML
    private DatePicker endDate;

    @FXML
    private Button back;

    @FXML
    private ComboBox<String> department;
    @FXML
    private ComboBox<String> docName;
    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    void handleSearch(ActionEvent event) throws Exception {

        int queryOpCode = 11, queryAnswerCode = 21;
        QueryConditions queryConditions = new QueryConditions();//初始化一个条件类


        //开始加入查询条件
        PatientInfo data = (PatientInfo) MyUtils.getParam().get("patientInfo");

        queryConditions.setPatientIdNumber(data.getIdNumber());

        if (beginDate.getValue() != null && endDate.getValue() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date min = dateFormat.parse(beginDate.getValue().toString());
            Date max = dateFormat.parse(endDate.getValue().toString());
            queryConditions.setTimeInterval(new Pair<>(BigInteger.valueOf(min.getTime()), BigInteger.valueOf(max.getTime())));
        }


        if (department.getValue()!=null) {
            queryConditions.setDepartment(department.getValue());
        }
        if (docName.getValue()!=null){
            queryConditions.setDoctorName(docName.getValue());
        }


        String opData = JSON.toJSONString(queryConditions);
        ClientData clientData = new ClientData(queryOpCode, opData);
        AnswerData answerData_S = tdws.trans(clientData, queryAnswerCode);
        List<VisitInfo> patientInfos = JSON.parseArray(answerData_S.getAnswerInfo(), VisitInfo.class);
        patientInfos.forEach(System.out::println);
        MyUtils.getParam().put("patientInfos", patientInfos);
        MyUtils.getParam().put("isSuccess", answerData_S.getAnswerInfo().isEmpty());

        if(beginDate.getValue()!=null&&endDate.getValue()!=null){
            MyUtils.getParam().put("beginDate", beginDate.getValue().toString());
            MyUtils.getParam().put("endDate", endDate.getValue().toString());

        }else{
            MyUtils.getParam().put("beginDate","");
            MyUtils.getParam().put("endDate","");
        }

        MyUtils.getParam().put("department", department.getValue());
        if ((boolean) MyUtils.getParam().get("isSuccess")) {
            ShowAlert showAlert = new ShowAlert("没有满足查询条件的记录");
        } else {
            ChangeView changeView = new ChangeView("PatientViews/PatientSearchResult.fxml", "查询结果", event);
        }
    }


    @FXML
    void handleBack(ActionEvent event) throws IOException {
        ChangeView changeView = new ChangeView("PatientViews/PatientIndex.fxml", "主界面", event);

    }

    @FXML
    public void initialize() {
        department.valueProperty().addListener(r -> {
            docName.getItems().remove(0, docName.getItems().size());
            if (!department.getValue().isEmpty()) {

                if (!MyUtils.getParam().containsKey(department.getValue())) {
                    int opCode = 5, answer = 6;
                    ClientData clientData = new ClientData(opCode, department.getValue());
                    try {
                        AnswerData trans = tdws.trans(clientData, answer);
                        List<DoctorInfo> doctorInfos = JSON.parseArray(trans.getAnswerInfo(), DoctorInfo.class);
//
                        MyUtils.getParam().put(department.getValue(), doctorInfos);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            List<DoctorInfo> doctorInfos = (List<DoctorInfo>) MyUtils.getParam().get(department.getValue());
            for (int i = 0; i < doctorInfos.size(); i++) {
                docName.getItems().add(doctorInfos.get(i).getUserName());
            }
        });

    }

}







