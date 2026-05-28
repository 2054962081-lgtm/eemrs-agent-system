package com.lilin.client.PatientControllers;

import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.*;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/**
 * @author lilin
 * @date 2019/10/8  -  6:35 下午
 */
public class PatientGuahaoController {
    @FXML
    private Button guahao;

    @FXML
    private Button back;

    @FXML
    private ComboBox<String> department;
    @FXML
    private ComboBox<String> doctorList;

    private Logger logger;


    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    public void initialize() {
        department.valueProperty().addListener(r -> {
            doctorList.getItems().remove(0, doctorList.getItems().size());

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
                doctorList.getItems().add(doctorInfos.get(i).getUserName());
            }
        });

    }

    @FXML
    void handleClickGuahao(ActionEvent event) throws Exception {


        List<DoctorInfo> doctorInfos = ((List<DoctorInfo>) (MyUtils.getParam().get(department.getValue())));

        DoctorInfo doctorInfo = doctorInfos.stream()
                .filter(d -> {
                    return d.getUserName().equals(doctorList.getValue());
                })
                .collect(Collectors.toList()).get(0);

        PatientInfo data = (PatientInfo) MyUtils.getParam().get("patientInfo");

        Integer opCode = 31, answerCode = 41;
        GuahaoInfo guahaoInfo = new GuahaoInfo(department.getValue(), data.getIdNumber(), data.getUserName(), null, doctorInfo.getIdNumber());
        String guahaoJSON = JSON.toJSONString(guahaoInfo);

        ClientData clientData = new ClientData(opCode, guahaoJSON);
        AnswerData answerData = tdws.trans(clientData, answerCode);

        if (answerData.isSuccess()) {
            if (answerData.getAnswerInfo().isEmpty()) {
                ShowAlert showAlert = new ShowAlert("挂号失败");

            } else {
                ShowAlert showAlert = new ShowAlert("挂号成功");
            }


        }

    }


    @FXML
    void handleClickBack(ActionEvent event) throws IOException {
        ChangeView changeView = new ChangeView("PatientViews/PatientIndex.fxml", "主界面", event);

    }

}
