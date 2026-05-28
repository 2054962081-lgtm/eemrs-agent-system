package com.lilin.client.DoctorControllers;

/**
 * @author lilin
 * @date 2019/10/16  -  2:47 下午
 */

import com.alibaba.fastjson.JSON;
import com.lilin.client.Class.ChangeView;
import com.lilin.client.Class.ShowAlert;
import com.lilin.client.connection.TransDataWithServer;
import com.lilin.client.crypto.SM2ClientKey;
import com.lilin.client.pojo_contr.*;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.TransDataWithServerFactory;
import com.lilin.client.utils.crypto.OperateKey;
import com.lilin.client.utils.crypto.SM2;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class DoctorInsertController {

    @FXML
    private TextField date;

    @FXML
    private Label gender;

    @FXML
    private TextField patientID;

    @FXML
    private TextField sex;

    @FXML
    private TextArea description;

    @FXML
    private TextArea medicine;

    @FXML
    private TextField userName;

    @FXML
    private TextField idNumber;

    @FXML
    private TextField doctor;

    @FXML
    private Button submit2;

    @FXML
    private Button back2;

    @FXML
    private TextField department;

    @FXML
    private TextField age;


    private SM2ClientKey sm2ClientKey= OperateKey.getSM2ClientKeyFromFile();
    private Map<String,Object> param = MyUtils.getParam();
    private TransDataWithServer tdws = TransDataWithServerFactory.getTransDataWithServer();

    @FXML
    void handleClickBack2(ActionEvent event)throws IOException {
        ChangeView changeView = new ChangeView("DoctorViews/DoctorIndex.fxml","主界面",event);
    }
@FXML
 void initialize(){
        PatientInfo patientInfo = (PatientInfo) MyUtils.getParam().get("patientInfo");
        // 设置病历信息表的自动填充字段
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        date.setText(dateFormat.format(new Date()));
        DoctorInfo doctorInfo = (DoctorInfo) MyUtils.getParam().get("doctorInfo");
        doctor.setText(doctorInfo.getUserName());
        sex.setText(patientInfo.getGender());
        idNumber.setText(patientInfo.getIdNumber());
        userName.setText(patientInfo.getUserName());
        department.setText(doctorInfo.getDepartment());
        age.setText(patientInfo.getAge());
        patientID.setText(patientInfo.getMedicareCard());

        date.setDisable(true);
        doctor.setDisable(true);
        idNumber.setDisable(true);
        userName.setDisable(true);
        department.setDisable(true);
        if(patientInfo.getAge()!=null){
            age.setDisable(true);
        }
        if(patientInfo.getGender()!=null){
            sex.setDisable(true);
        }
        if(patientInfo.getMedicareCard()!=null){
            patientID.setDisable(true);
        }



}
    @FXML
    void handleClickSubmit2(ActionEvent event) throws Exception {


        if ((boolean) MyUtils.getParam().get("success2")) {//如果成功接诊一位病人
            if(date.getText().equals("")||doctor.getText().equals("")||idNumber.getText().equals("")||userName.getText().equals("")
                    ||medicine.getText().equals("")||description.getText().equals("")||sex.getText().equals("")||patientID.getText().equals("")
                    ||gender.getText().equals("")||age.getText().equals("")){
                ShowAlert showAlert = new ShowAlert("请填写完整病历记录");

            }else{
                PatientInfo patientInfo = (PatientInfo) MyUtils.getParam().get("patientInfo");
                DoctorInfo doctorInfo = (DoctorInfo) param.get("doctorInfo");
                System.out.println(doctorInfo);
                //实例化一个病人信息表
                VisitInfo visitInfo = new VisitInfo();
                //为信息表自动添加一些信息
                visitInfo.setDepartment(doctorInfo.getDepartment());
                visitInfo.setPatientIdNumber(patientInfo.getIdNumber());
                visitInfo.setPatientName(patientInfo.getUserName());
                //插入日期
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                visitInfo.setVisitTime(BigInteger.valueOf(dateFormat.parse(date.getText()).getTime()));
                visitInfo.setDoctorName(doctorInfo.getUserName());
                visitInfo.setDoctorIdNumber(doctorInfo.getIdNumber());
                visitInfo.setDPk(Base64.toBase64String(sm2ClientKey.getPublicKey().getEncoded()));
                visitInfo.setAge(new BigInteger(age.getText()));
                //医生录入病情信息
                visitInfo.setConditionDescription(description.getText());
                visitInfo.setMedication(medicine.getText());
                visitInfo.setGender(sex.getText());
                visitInfo.setSignature(
                        SM2.sign(visitInfo.getConditionDescription(),sm2ClientKey.getPrivateKey())
                );

                System.out.println(visitInfo);

                int opCodeInsert = 12, answerCodeInsert = 22;
                ClientData clientDataInsert = new ClientData(opCodeInsert, JSON.toJSONString(visitInfo));
                AnswerData answerDataInsert = tdws.trans(clientDataInsert, answerCodeInsert);
                System.out.println(answerDataInsert.getAnswerInfo());

                ShowAlert showAlert = new ShowAlert("病人就诊记录已提交");
                ChangeView changeView =new ChangeView("DoctorViews/DoctorIndex.fxml","主界面",event);
            }

        }



    }

}

