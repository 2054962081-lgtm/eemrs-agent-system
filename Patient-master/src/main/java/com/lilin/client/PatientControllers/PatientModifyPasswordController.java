package com.lilin.client.PatientControllers;

import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.ConnectionWithServer;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.PasswordModify;
import com.lilin.client.pojo_contr.PatientInfo;
import com.lilin.client.utils.ConnectionWithServerFactory;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.io.IOException;

public class PatientModifyPasswordController {

    @FXML
    private Button submit;

    @FXML
    private TextField prePassword;

    @FXML
    private TextField newPassword;

    @FXML
    private TextField confirmPassword;

    @FXML
    private Button back;
    private ConnectionWithServer cws = ConnectionWithServerFactory.getConnectionWithServer();
    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    public  void handleClickSubmit(ActionEvent event)throws IOException {
        if(prePassword.getText().isEmpty()||newPassword.getText().isEmpty()||confirmPassword.getText().isEmpty()){
            ShowAlert showAlert = new ShowAlert("请输入完整的修改信息");
        }else if(prePassword.getText().equals(newPassword.getText())){
            ShowAlert showAlert =new ShowAlert("新密码不能与原密码相同！");

        }else if(!newPassword.getText().equals(confirmPassword.getText())){
            ShowAlert showAlert = new ShowAlert("确认密码与新密码不一致！");

        }else{
            PatientInfo data = (PatientInfo) MyUtils.getParam().get("patientInfo");


            int opCode =18,answerCode=28;
            PasswordModify modify = new PasswordModify("pt",data.getIdNumber(),prePassword.getText(),newPassword.getText(),null);
            AnswerData answerData = MyUtils.transData(tdws, modify, opCode, answerCode);
            System.out.println(answerData.getAnswerInfo());
            if(answerData.isSuccess()){
                ShowAlert showAlert = new ShowAlert("修改成功");
            }


        }


    }

    @FXML
    public  void handleClickBack(ActionEvent event) throws IOException {
        ChangeView changeView = new ChangeView("PatientViews/PatientIndex.fxml","主界面", event);
    }

}
