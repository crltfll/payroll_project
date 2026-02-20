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
 * FIXED: loadView() now wraps every child view in a ScrollPane so that
 *        content is always reachable even on smaller / lower-resolution screens.
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
    @FXML private BorderPane rootBorderPane;

    private Button currentActiveButton;

    @FXML
    public void initialize() {
        User currentUser = LoginController.getCurrentUser();
        if (currentUser != null) {
            userNameLabel.setText(currentUser.getFullName());
            userRoleLabel.setText(currentUser.getRole().name());
        }

        if (rootBorderPane != null) {
            rootBorderPane.setUserData(this);
        }

        showDashboardHome();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    @FXML private void showDashboardHome() { loadView("/fxml/dashboard-home.fxml", dashboardButton); }
    @FXML private void showEmployees()     { loadView("/fxml/employees.fxml",       employeesButton); }
    @FXML private void showAttendance()    { loadView("/fxml/attendance.fxml",      attendanceButton); }
    @FXML private void showPayroll()       { loadView("/fxml/payroll.fxml",         payrollButton); }
    @FXML private void showReports()       { loadView("/fxml/reports.fxml",         reportsButton); }
    @FXML private void showSettings()      { loadView("/fxml/settings.fxml",        settingsButton); }

    // Public aliases for DashboardHomeController quick-actions
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
    // Core view loader
    // -----------------------------------------------------------------------

    private void loadView(String fxmlPath, Button button) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // Re-register self so child views can still navigate upward
            if (rootBorderPane != null) {
                rootBorderPane.setUserData(this);
            }

            /*
             * RESIZABILITY FIX
             * ─────────────────
             * If the loaded view is NOT already a ScrollPane (attendance.fxml wraps
             * itself), we wrap it here so every panel scrolls on small screens.
             */
            javafx.scene.Node nodeToPlace;
            if (view instanceof javafx.scene.control.ScrollPane) {
                // Already a ScrollPane – use as-is
                nodeToPlace = view;
            } else {
                javafx.scene.control.ScrollPane scroll =
                        new javafx.scene.control.ScrollPane(view);
                scroll.setFitToWidth(true);
                scroll.setFitToHeight(false);   // let height grow naturally → enables vertical scroll
                scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
                scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
                // Transparent background so the existing card/dashboard styles show through
                scroll.setStyle(
                    "-fx-background-color: transparent;" +
                    "-fx-background: transparent;" +
                    "-fx-border-color: transparent;"
                );
                nodeToPlace = scroll;
            }

            contentArea.getChildren().setAll(nodeToPlace);

            // Update active nav-button highlight
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
