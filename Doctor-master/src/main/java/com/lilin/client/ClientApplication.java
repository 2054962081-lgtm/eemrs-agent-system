package com.lilin.client;

import com.lilin.client.connection.ConnectionWithServer;
import com.lilin.client.utils.ConnectionWithServerFactory;
import com.lilin.client.utils.TransDataWithServerFactory;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ClientApplication extends Application {





    public static void main(String[] args)
    {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) throws Exception {

        try {
            Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("MainView/Main.fxml"));



            Scene scene = new Scene(root, 900, 500);

            primaryStage.setTitle("");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.setTitle("医生客户端");



            primaryStage.show();
            ConnectionWithServer connectionWithServer = ConnectionWithServerFactory.getConnectionWithServer();
            connectionWithServer.connect();
            TransDataWithServerFactory.init(connectionWithServer.getBw(),connectionWithServer.getBr(),connectionWithServer.getSessionKey());


        } catch (IOException e) {
                e.printStackTrace();
            }

    }


}
