package com.lilin.client.Main;


import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.ClientData;
import com.lilin.client.pojo_contr.DoctorInfo;
import com.lilin.client.pojo_contr.UserLog;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML
    private TextField idNumber;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ComboBox<String> department;
    @FXML
    private Button login;


    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    public void handleClickLogIn(ActionEvent event) throws Exception {

        if (idNumber.getText().isEmpty() && passwordField.getText().isEmpty()) {

            ShowAlert showAlert = new ShowAlert("请输入用户名和密码");

        } else if (idNumber.getText().isEmpty()) {

            ShowAlert showAlert = new ShowAlert("请输入用户名");

        } else if (passwordField.getText().isEmpty()) {

            ShowAlert showAlert = new ShowAlert("请输入密码");
        } else if (department.getValue().equals("请选择科室")) {
            ShowAlert showAlert = new ShowAlert("请选择科室");

        } else {
            int opCode = 13, answerCode = 23;

            UserLog userOP = new UserLog("dt", idNumber.getText(), null, passwordField.getText(), null, department.getValue());
            String userJSON = JSON.toJSONString(userOP);
            ClientData clientData = new ClientData(opCode, userJSON);
            AnswerData answerData = tdws.trans(clientData, answerCode);
            if (answerData.isSuccess()) {
                int code = 3, anCode = 4;
                String id = idNumber.getText();
                ClientData clientData1 = new ClientData(code, id);
                AnswerData answerData1 = tdws.trans(clientData1, anCode);
                System.out.println(answerData1.getAnswerInfo());
                MyUtils.getParam().put("doctorInfo", JSON.parseObject(answerData1.getAnswerInfo(), DoctorInfo.class));
                ChangeView changeView = new ChangeView("DoctorViews/DoctorIndex.fxml", "主界面", event);

            } else {
                ShowAlert showAlert = new ShowAlert("登录失败\n 请检查用户名和密码是否正确");
            }


        }

    }


    @FXML
    public void handleClickSubmitRegister(ActionEvent event) throws IOException {

        ChangeView changeView = new ChangeView("Register/Register.fxml", "注册", event);
    }

    @FXML
    public void handleClickLogOut(ActionEvent event) throws IOException {

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {


    }
}

