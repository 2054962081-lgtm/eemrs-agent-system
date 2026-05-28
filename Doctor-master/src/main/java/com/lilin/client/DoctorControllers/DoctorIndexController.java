package com.lilin.client.DoctorControllers;

import com.lilin.client.Class.ChangeView;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

public class DoctorIndexController {

    @FXML
    private Button modify;

    @FXML
    private Button search;

    @FXML
    private Button jiezhen;


    @FXML
    private Button back;



    @FXML
    void handleClickJiezhen(ActionEvent event) throws Exception {

        ChangeView changeView = new ChangeView("DoctorViews/DoctorJiezhen.fxml","接诊",event);

    }

    @FXML
    void handleClickSearch(ActionEvent event)throws IOException {
        ChangeView changeView = new ChangeView("DoctorViews/DoctorSearch.fxml","查询",event);

    }

    @FXML
    void handleClickModify(ActionEvent event)throws IOException {
        ChangeView changeView = new ChangeView("DoctorViews/DoctorModifyPassword.fxml","修改密码",event);

    }




    @FXML
    void gotoBack(ActionEvent event) throws IOException{
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.close();

    }

}

