package com.lilin.client.PatientControllers;


import com.lilin.client.Class.ChangeView;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

public class PatientIndexController {

    @FXML
    private Button gotoSearch;

    @FXML
    private Button gotoModify;

    @FXML
    private Button gotoComplete;








    @FXML
    public  void handleClickGoToSearch(ActionEvent event)throws IOException{

        ChangeView changeView = new ChangeView("PatientViews/PatientSearch.fxml","查询",event);


    }
    @FXML
    public  void handleClickGoToModify(ActionEvent event)throws IOException{
        ChangeView changeView = new ChangeView("PatientViews/PatientModifyPassword.fxml","修改密码",event);


    }
    @FXML
    public  void handleClickGoToComplete(ActionEvent event)throws IOException{

        ChangeView changeView = new ChangeView("PatientViews/PatientComplete .fxml","完善信息",event);



    }
    @FXML
    public void  handleClickLogOut(ActionEvent event)throws IOException{
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.close();
    }
    @FXML
    public void handleClickGuahao(ActionEvent event) throws Exception {

        ChangeView changeView = new ChangeView("PatientViews/PatientGuahao.fxml","挂号",event);

    }
}

