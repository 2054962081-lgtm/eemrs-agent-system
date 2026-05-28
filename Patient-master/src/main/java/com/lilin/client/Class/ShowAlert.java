package com.lilin.client.Class;

import javafx.scene.control.Alert;

import java.io.IOException;

/**
 * @author lilin
 * @date 2019/10/7  -  4:12 下午
 */
public class ShowAlert {
    public ShowAlert(String string)throws IOException {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(string);
        alert.setTitle(null);
        alert.showAndWait();

    }
}
