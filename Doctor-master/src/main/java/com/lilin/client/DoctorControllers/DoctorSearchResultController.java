package com.lilin.client.DoctorControllers;

import com.lilin.client.Class.ChangeView;
import com.lilin.client.pojo_contr.DoctorInfo;
import com.lilin.client.pojo_contr.VisitInfo;
import com.lilin.client.utils.MyUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lilin
 * @date 2019/10/16  -  11:06 上午
 */
public class DoctorSearchResultController {
    @FXML
    private Button back2;
    @FXML
    private TableColumn<VisitInfo, BigInteger> ageCol;

    @FXML
    private TableColumn<VisitInfo, String> orderCol;

    @FXML
    private TableColumn<VisitInfo, String> conditionDescriptionCol;

    @FXML
    private TableColumn<VisitInfo, String> dateCol;

    @FXML
    private TableColumn<VisitInfo, String> medicineCol;

    @FXML
    private TableColumn<VisitInfo, String> nameCol;
    @FXML
    private TableView<VisitInfo> patientTable;

    @FXML
    private TableColumn<VisitInfo, String> sexCol;
    @FXML
    private TableColumn<VisitInfo, String> idNumberCol;
    @FXML
    private Button show;
    @FXML
    private TextField beginDate;
    @FXML
    private TextField endDate;
    @FXML
    private TextField department;


    @FXML
    public void initialize() throws IOException {

        //设置自动填充
        if(MyUtils.getParam().get("beginDate")!=null&&MyUtils.getParam().get("endDate")!=null){
            beginDate.setText( MyUtils.getParam().get("beginDate").toString());
            endDate.setText( MyUtils.getParam().get("endDate").toString());
        }else{
            beginDate.setText("");
            endDate.setText("");
        }


        DoctorInfo doctorInfo = (DoctorInfo) MyUtils.getParam().get("doctorInfo");
        department.setText(doctorInfo.getDepartment());
        beginDate.setDisable(true);
        endDate.setDisable(true);
        department.setDisable(true);

        List<VisitInfo> patientInfo = (List<VisitInfo>) MyUtils.getParam().get("patientInfos");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        List<VisitInfo> collect = patientInfo.stream()
                .map(m -> {
                    m.setTime(dateFormat.format(new Date(m.getVisitTime().longValue())));
                    return m;
                })
                .collect(Collectors.toList());

        ObservableList<VisitInfo> patientInfos1 = FXCollections.observableArrayList(collect);
        nameCol.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        idNumberCol.setCellValueFactory(new PropertyValueFactory<>("patientIdNumber"));
        sexCol.setCellValueFactory(new PropertyValueFactory<>("gender"));
        conditionDescriptionCol.setCellValueFactory(new PropertyValueFactory<>("conditionDescription"));
        medicineCol.setCellValueFactory(new PropertyValueFactory<>("medication"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        ageCol.setCellValueFactory(new PropertyValueFactory<>("age"));
        orderCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        int rowIndex = this.getIndex() + 1;
                        this.setText(String.valueOf(rowIndex));
                    }
                }
            };
            return cell;
        });

        nameCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String department = this.getTableView().getItems().get(this.getIndex()).getPatientName();
                        this.setText(String.valueOf(department));
                    }
                }
            };
            return cell;
        });
        dateCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String visitTime = this.getTableView().getItems().get(this.getIndex()).getTime();
                        this.setText(visitTime);
                    }
                }
            };
            return cell;
        });
        medicineCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String medicine = this.getTableView().getItems().get(this.getIndex()).getMedication();
                        this.setText(String.valueOf(medicine));
                    }
                }
            };
            return cell;
        });
        conditionDescriptionCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String description = this.getTableView().getItems().get(this.getIndex()).getConditionDescription();
                        this.setText(String.valueOf(description));
                    }
                }
            };
            return cell;
        });
        ageCol.setCellFactory((col) -> {
            TableCell<VisitInfo, BigInteger> cell = new TableCell<VisitInfo, BigInteger>() {
                @Override
                public void updateItem(BigInteger item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        BigInteger visitTime = this.getTableView().getItems().get(this.getIndex()).getAge();
                        this.setText(String.valueOf(visitTime));
                    }
                }
            };
            return cell;
        });
        sexCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String doctor = this.getTableView().getItems().get(this.getIndex()).getGender();
                        this.setText(String.valueOf(doctor));
                    }
                }
            };
            return cell;
        });
        idNumberCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String doctor = this.getTableView().getItems().get(this.getIndex()).getPatientIdNumber();
                        this.setText(String.valueOf(doctor));
                    }
                }
            };
            return cell;
        });


        patientTable.setItems(patientInfos1);


    }



    @FXML
    public void handleClickBack2(ActionEvent event)throws  IOException{

        ChangeView changeView =new ChangeView("DoctorViews/DoctorSearch.fxml","查询", event);
    }
}
