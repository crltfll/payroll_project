package com.example.payroll_project.controller;

import com.example.payroll_project.model.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main Dashboard Controller
 * Handles navigation and content area management
 */
public class DashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    
    @FXML
    private Label userNameLabel;
    
    @FXML
    private Label userRoleLabel;
    
    @FXML
    private StackPane contentArea;
    
    @FXML
    private VBox navigationMenu;
    
    @FXML
    private Button dashboardButton;
    
    @FXML
    private Button employeesButton;
    
    @FXML
    private Button attendanceButton;
    
    @FXML
    private Button payrollButton;
    
    @FXML
    private Button reportsButton;
    
    @FXML
    private Button settingsButton;
    
    @FXML
    private Button logoutButton;
    
    private Button currentActiveButton;
    
    @FXML
    public void initialize() {
        User currentUser = LoginController.getCurrentUser();
        if (currentUser != null) {
            userNameLabel.setText(currentUser.getFullName());
            userRoleLabel.setText(currentUser.getRole().name());
        }
        
        // Load home view by default
        showDashboardHome();
    }
    
    @FXML
    private void showDashboardHome() {
        loadView("/fxml/dashboard-home.fxml", dashboardButton);
    }
    
    @FXML
    private void showEmployees() {
        loadView("/fxml/employees.fxml", employeesButton);
    }
    
    @FXML
    private void showAttendance() {
        loadView("/fxml/attendance.fxml", attendanceButton);
    }
    
    @FXML
    private void showPayroll() {
        loadView("/fxml/payroll.fxml", payrollButton);
    }
    
    @FXML
    private void showReports() {
        loadView("/fxml/reports.fxml", reportsButton);
    }
    
    @FXML
    private void showSettings() {
        loadView("/fxml/settings.fxml", settingsButton);
    }
    
    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Logout");
        alert.setHeaderText("Are you sure you want to logout?");
        alert.setContentText("Any unsaved changes will be lost.");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                LoginController.logout();
            }
        });
    }
    
    /**
     * Load view into content area
     */
    private void loadView(String fxmlPath, Button button) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            
            // Update active button styling
            if (currentActiveButton != null) {
                currentActiveButton.getStyleClass().remove("nav-button-active");
            }
            button.getStyleClass().add("nav-button-active");
            currentActiveButton = button;
            
            logger.info("Loaded view: {}", fxmlPath);
            
        } catch (IOException e) {
            logger.error("Failed to load view: {}", fxmlPath, e);
            showErrorAlert("Failed to load view", e.getMessage());
        }
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
