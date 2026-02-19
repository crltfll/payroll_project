package com.example.payroll_project.controller;

import com.example.payroll_project.model.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main Dashboard Controller
 * Handles navigation and content area management.
 *
 * Stores itself as userData on the root BorderPane so that child controllers
 * (e.g. DashboardHomeController) can reach it for Quick-Action navigation.
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private StackPane contentArea;
    @FXML private VBox navigationMenu;
    @FXML private Button dashboardButton;
    @FXML private Button employeesButton;
    @FXML private Button attendanceButton;
    @FXML private Button payrollButton;
    @FXML private Button reportsButton;
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;
    @FXML private BorderPane rootBorderPane;   // fx:id="rootBorderPane" in dashboard.fxml

    private Button currentActiveButton;

    @FXML
    public void initialize() {
        User currentUser = LoginController.getCurrentUser();
        if (currentUser != null) {
            userNameLabel.setText(currentUser.getFullName());
            userRoleLabel.setText(currentUser.getRole().name());
        }

        // Register self so child views can navigate back up the scene graph
        if (rootBorderPane != null) {
            rootBorderPane.setUserData(this);
        }

        showDashboardHome();
    }

    // -----------------------------------------------------------------------
    // Navigation — private (called by FXML sidebar buttons)
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Public navigation — used by DashboardHomeController Quick Actions
    // -----------------------------------------------------------------------

    public void showEmployeesPublic()  { showEmployees();  }
    public void showAttendancePublic() { showAttendance(); }
    public void showPayrollPublic()    { showPayroll();    }
    public void showReportsPublic()    { showReports();    }

    // -----------------------------------------------------------------------
    // Logout
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void loadView(String fxmlPath, Button button) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // Re-register self after each child load (scene graph changes)
            if (rootBorderPane != null) {
                rootBorderPane.setUserData(this);
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);

            if (currentActiveButton != null) {
                currentActiveButton.getStyleClass().remove("nav-button-active");
            }
            if (button != null) {
                button.getStyleClass().add("nav-button-active");
            }
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
