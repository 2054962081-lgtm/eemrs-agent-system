package com.lilin.client.PatientControllers;

import com.lilin.client.Class.ChangeView;
import com.lilin.client.pojo_contr.VisitInfo;
import com.lilin.client.utils.MyUtils;
import com.lilin.client.utils.crypto.OperateKey;
import com.lilin.client.utils.crypto.SM2;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lilin
 * @date 2019/10/16  -  9:57 上午
 */
public class PatientSearchResultController {
//result

    @FXML
    private Button back2;

    @FXML
    private TableColumn<VisitInfo, String> orderCol;

    @FXML
    private TableColumn<VisitInfo, String> departmentCol;

    @FXML
    private TableColumn<VisitInfo, String> dateCol;

    @FXML
    private TableView<VisitInfo> patientTable;

    @FXML
    private TableColumn<VisitInfo, String> medicineCol;

    @FXML
    private TableColumn<VisitInfo, String> doctorCol;

    @FXML
    private TableColumn<VisitInfo, String> descriptionCol;
    @FXML
    private TableColumn<VisitInfo, String> verifyCol;


    @FXML
    private TextField beginDate2;
    @FXML
    private TextField endDate2;
    @FXML
    private TextField department2;


    @FXML
    public void gotoBack2(ActionEvent event) throws IOException {

        ChangeView changeView = new ChangeView("PatientViews/PatientSearch.fxml", "查询", event);

    }

    @FXML
    public void initialize() throws IOException {


        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        List<VisitInfo> patientInfo = (List<VisitInfo>) MyUtils.getParam().get("patientInfos");


        List<VisitInfo> collect = patientInfo.stream()
                .map(p -> {
                    Date date = new Date(p.getVisitTime().longValue());
                    p.setTime(dateFormat.format(date));

                    String sign = p.getSignature();
                    byte[] DPKey = Base64.getDecoder().decode(p.getDPk());
                    p.setVerify(SM2.verify(p.getConditionDescription(), sign, OperateKey.toSM2PublicKey(DPKey)));
                    return p;
                })
                .collect(Collectors.toList());

        ObservableList<VisitInfo> patientInfos1 = FXCollections.observableArrayList(collect);
        departmentCol.setCellValueFactory(new PropertyValueFactory<>("department"));
        dateCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        doctorCol.setCellValueFactory(new PropertyValueFactory<>("doctorName"));
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("conditionDescription"));
        medicineCol.setCellValueFactory(new PropertyValueFactory<>("medication"));
        verifyCol.setCellValueFactory(new PropertyValueFactory<>("verify"));


        verifyCol.setCellValueFactory(new PropertyValueFactory<>("verify"));

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

        departmentCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String department = this.getTableView().getItems().get(this.getIndex()).getDepartment();
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
                        String time = this.getTableView().getItems().get(this.getIndex()).getTime();
                        this.setText(time);
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
        descriptionCol.setCellFactory((col) -> {
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
        doctorCol.setCellFactory((col) -> {
            TableCell<VisitInfo, String> cell = new TableCell<VisitInfo, String>() {
                @Override
                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    this.setText(null);
                    this.setGraphic(null);

                    if (!empty) {
                        String doctor = this.getTableView().getItems().get(this.getIndex()).getDoctorName();
                        this.setText(String.valueOf(doctor));
                    }
                }
            };
            return cell;
        });

        patientTable.setItems(patientInfos1);
        // 设置查询结果页面的自动获取值

                    beginDate2.setText((String) MyUtils.getParam().get("beginDate"));
                    beginDate2.setDisable(true);
                    endDate2.setText((String) MyUtils.getParam().get("endDate"));
                    endDate2.setDisable(true);

        department2.setText((String) MyUtils.getParam().get("department"));
        department2.setDisable(true);


    }
}
