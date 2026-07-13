package com.liu.eemrsserver;

import com.liu.eemrsserver.config.ApplicationContextProvider;
import com.liu.eemrsserver.utils.GetExceptionMessage;
import org.apache.log4j.Logger;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;

@EnableTransactionManagement
@MapperScan(basePackages = "com.liu.eemrsserver.mapper")
@SpringBootApplication
public class EemrsServerApplication {
    private static final Logger logger = Logger.getLogger(EemrsServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EemrsServerApplication.class, args);
    }

    @Bean
    @Order(1)
    public ApplicationRunner checkDbConnection(DataSource dataSource) {
        return args -> {
            System.out.println("===== start database connection check =====");
            try (Connection connection = dataSource.getConnection()) {
                System.out.println("\n===== database connection success =====");
                System.out.println("URL: " + connection.getMetaData().getURL());
                System.out.println("User: " + connection.getMetaData().getUserName());
                System.out.println("=========================\n");
            } catch (Exception e) {
                System.err.println("\n===== database connection failed =====");
                System.err.println("Reason: " + e.getMessage());
                System.err.println("=========================\n");
                e.printStackTrace();
            }
        };
    }

    @Bean
    @Order(3)
    public ApplicationRunner legacySocketServerRunner() {
        return args -> {
            Thread legacySocketServer = new Thread(this::startLegacySocketServer, "legacy-socket-server");
            legacySocketServer.setDaemon(false);
            legacySocketServer.start();
        };
    }

    private void startLegacySocketServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(8887);
            while (true) {
                logger.info("等待连接");
                Socket socket = serverSocket.accept();
                String user = socket.getInetAddress().getHostName();
                String ip = socket.getInetAddress().getHostAddress();
                logger.debug("用户" + user + "建立连接");
                logger.info("已为用户[" + user + ":" + ip + "]生成会话密钥");
                ControlThread thread = ApplicationContextProvider.getBean(ControlThread.class);
                thread.setName("用户:" + socket.getInetAddress().getHostName());
                thread.getControlRun().setSocket(socket);
                logger.debug("线程开启");
                thread.start();
            }
        } catch (Exception e) {
            logger.info("\n" + GetExceptionMessage.getMessage(e));
        }
    }
}
