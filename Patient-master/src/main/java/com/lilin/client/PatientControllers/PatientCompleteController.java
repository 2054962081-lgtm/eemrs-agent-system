package com.lilin.client.PatientControllers;

/**
 * @author lilin
 * @date 2019/10/7  -  2:21 下午
 */


import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.pojo_contr.AnswerData;
import com.lilin.client.pojo_contr.PatientInfo;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class PatientCompleteController {


        @FXML
        private TextField birthdate;

        @FXML
        private TextField address;

        @FXML
        private Button submit;

        @FXML
        private TextField mail;

        @FXML
        private TextField nation;

        @FXML
        private TextField patientID;

        @FXML
        private TextField sex;

        @FXML
        private TextField name;

        @FXML
        private Button back;

        @FXML
        private TextField telephone;

        @FXML
        private TextField idNumber;

        @FXML
        private TextField age;

private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    public class StageChangeListener implements ChangeListener<String> {

        Stage stage;

        public StageChangeListener(Stage stage){
            this.stage = stage;
        }

        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            System.out.println(newValue);
//            stage.getIcons().add(ImageUtil.getImage("/image/tools.png"));
        }
    }


    @FXML
    void handleClickBack(ActionEvent event) throws IOException {
        ChangeView changeView = new ChangeView("PatientViews/PatientIndex.fxml","主界面",event);
    }



    @FXML
    void handleClickSubmit(ActionEvent event)throws IOException {
        System.out.println(name.getText());
        System.out.println(sex.getText());


            int opCode =19,answerCode=29;
            PatientInfo data = (PatientInfo) MyUtils.getParam().get("patientInfo");

            PatientInfo patientInfo = new PatientInfo();
            patientInfo.setIdNumber(data.getIdNumber());
            patientInfo.setAge(age.getText());
            patientInfo.setUserName(data.getUserName());
            patientInfo.setBirthDay(birthdate.getText());
            patientInfo.setGender(sex.getText());
            patientInfo.setNation(nation.getText());
            patientInfo.setTelephone(telephone.getText());
            patientInfo.setAddress(address.getText());
            patientInfo.setMail(mail.getText());
            patientInfo.setMedicareCard(patientID.getText());
            AnswerData answerData = MyUtils.transData(tdws, patientInfo, opCode, answerCode);
            System.out.println(answerData.getAnswerInfo());

            ShowAlert showAlert = new ShowAlert("信息完善表已提交");
        }



        @FXML
    public  void initialize(){
        PatientInfo data = (PatientInfo) MyUtils.getParam().get("patientInfo");
        name.setText(data.getUserName());
        name.setDisable(true);
        idNumber.setText(data.getIdNumber());
        idNumber.setDisable(true);

            age.setText(data.getAge());
            birthdate.setText(data.getBirthDay());
            sex.setText(data.getGender());
            nation.setText(data.getNation());
            telephone.setText(data.getTelephone());
            address.setText(data.getAddress());
            mail.setText(data.getMail());
            patientID.setText(data.getMedicareCard());



        }

}
