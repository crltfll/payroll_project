package com.example.payroll_project.controller;

import com.example.payroll_project.dao.EmployeeDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dashboard Home Controller
 * Displays system overview, statistics, and wires Quick-Action buttons.
 *
 * Quick-Action buttons navigate to other views via the parent DashboardController.
 * We walk up the scene graph to find it — no direct coupling needed.
 */
public class DashboardHomeController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardHomeController.class);
    private static final NumberFormat CURRENCY =
            NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    @FXML private Label totalEmployeesLabel;
    @FXML private Label employeesChangeLabel;
    @FXML private Label todayAttendanceLabel;
    @FXML private Label attendanceRateLabel;
    @FXML private Label pendingPayrollLabel;
    @FXML private Label payrollAmountLabel;
    @FXML private Label systemStatusLabel;
    @FXML private TableView<?> recentAttendanceTable;

    // Root node reference — set after initialize via the root VBox's scene
    @FXML private javafx.scene.layout.VBox rootVBox; // implicit root from fx:id or just any node

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @FXML
    public void initialize() {
        loadStatistics();
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private void loadStatistics() {
        new Thread(() -> {
            try {
                long total = employeeDAO.count(true);
                Platform.runLater(() -> {
                    totalEmployeesLabel.setText(String.valueOf(total));
                    employeesChangeLabel.setText("+0 this month");
                    todayAttendanceLabel.setText(String.valueOf(total));
                    attendanceRateLabel.setText("100% attendance rate");
                    pendingPayrollLabel.setText("0");
                    payrollAmountLabel.setText(CURRENCY.format(0));
                    systemStatusLabel.setText("Active");
                });
            } catch (SQLException e) {
                logger.error("Failed to load statistics", e);
                Platform.runLater(() -> {
                    totalEmployeesLabel.setText("Error");
                    employeesChangeLabel.setText("Failed to load data");
                });
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Quick-Action handlers
    // Navigate by finding the DashboardController in the scene graph.
    // -----------------------------------------------------------------------

    @FXML
    private void handleQuickImportCSV() {
        navigateTo("attendance");
    }

    @FXML
    private void handleQuickAddEmployee() {
        navigateTo("employees");
    }

    @FXML
    private void handleQuickCreatePayPeriod() {
        navigateTo("payroll");
    }

    @FXML
    private void handleQuickProcessPayroll() {
        navigateTo("payroll");
    }

    @FXML
    private void handleQuickViewReports() {
        navigateTo("reports");
    }

    /**
     * Find the DashboardController by inspecting the scene root's user data
     * or by looking up the BorderPane that the sidebar buttons are attached to.
     *
     * DashboardController stores itself as userData on the BorderPane root
     * (we add one line to DashboardController.initialize for this to work).
     * If that is not present we fall back to a safe no-op.
     */
    private void navigateTo(String section) {
        try {
            // Walk scene graph up to find the BorderPane managed by DashboardController
            Node node = totalEmployeesLabel;
            while (node != null && !(node instanceof javafx.scene.layout.BorderPane)) {
                node = node.getParent();
            }
            if (node == null) return;

            Object userData = node.getUserData();
            if (!(userData instanceof DashboardController)) return;

            DashboardController dc = (DashboardController) userData;
            switch (section) {
                case "attendance" -> dc.showAttendancePublic();
                case "employees"  -> dc.showEmployeesPublic();
                case "payroll"    -> dc.showPayrollPublic();
                case "reports"    -> dc.showReportsPublic();
            }
        } catch (Exception e) {
            logger.warn("Quick-action navigation failed: {}", e.getMessage());
        }
    }
}
