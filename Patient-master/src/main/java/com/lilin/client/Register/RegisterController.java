package com.lilin.client.Register;

import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.ConnectionWithServer;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.ClientData;
import com.lilin.client.pojo_contr.UserLog;
import com.lilin.client.utils.ConnectionWithServerFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;


public class RegisterController {

    @FXML
    private PasswordField password;

    @FXML
    private Button Register;

    @FXML
    private ComboBox<?> combobox;

    @FXML
    private TextField userName;

    @FXML
    private TextField userID;
    @FXML
    private Button back;



    private ConnectionWithServer cws = ConnectionWithServerFactory.getConnectionWithServer();



    @FXML
    void handleClickRegister(ActionEvent event) throws Exception {
        BufferedWriter bw = cws.getBw();
        BufferedReader br = cws.getBr();
        byte[] key = cws.getSessionKey();
        TransDataWithServer tdws = new TransDataWithServer(br, bw, key);

        if (userName.getText().isEmpty() || userID.getText().isEmpty() || password.getText().isEmpty()) {
            ShowAlert showAlert = new ShowAlert("请输入完整的注册信息");
        } else {


                int opCode = 14, answerCode = 24;
                UserLog userOP = new UserLog("pt", userID.getText(), userName.getText(), password.getText(), null,null,null);
                String userJSON = JSON.toJSONString(userOP);
                ClientData clientData = new ClientData(opCode, userJSON);
                AnswerData answerData = tdws.trans(clientData, answerCode);
                if (answerData.isSuccess()) {
                    ShowAlert showAlert = new ShowAlert("注册成功");

                } else {

                    ShowAlert showAlert = new ShowAlert("注册失败\n请重新注册");
                }
            }

        }

        public void backToLogIn(ActionEvent event)throws IOException {
            ChangeView changeView = new ChangeView("MainView/Main.fxml","病人客户端", event);
        }

    }

