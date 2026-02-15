package com.example.payroll_project;

import com.example.payroll_project.util.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main JavaFX Application
 * KC-02N Biometric Scanner-based Automated Payroll System
 *
 */
public class PayrollApplication extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(PayrollApplication.class);
    private static Stage primaryStage;
    
    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        try {
            DatabaseManager.getInstance().initialize();
            logger.info("Database initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            showErrorDialog("Database Error", "Failed to initialize database: " + e.getMessage());
            System.exit(1);
        }

        showLoginScreen();

        primaryStage.setTitle("KC-02N Payroll System");
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(768);
        primaryStage.show();
    }
    

    public static void showLoginScreen() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
            PayrollApplication.class.getResource("/fxml/login.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load());
        scene.getStylesheets().add(
            PayrollApplication.class.getResource("/css/styles.css").toExternalForm()
        );
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
    }

    public static void showDashboard() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
            PayrollApplication.class.getResource("/fxml/dashboard.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load(), 1280, 800);
        scene.getStylesheets().add(
            PayrollApplication.class.getResource("/css/styles.css").toExternalForm()
        );
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    private void showErrorDialog(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @Override
    public void stop() throws Exception {
        // Cleanup
        DatabaseManager.getInstance().close();
        logger.info("Application shutdown");
        super.stop();
    }
    
    public static void main(String[] args) {
        launch();
    }
}
