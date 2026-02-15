package com.example.payroll_project.controller;

import com.example.payroll_project.dao.EmployeeDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dashboard Home Controller
 * Displays system overview and statistics
 */
public class DashboardHomeController {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardHomeController.class);
    private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    
    @FXML
    private Label totalEmployeesLabel;
    
    @FXML
    private Label employeesChangeLabel;
    
    @FXML
    private Label todayAttendanceLabel;
    
    @FXML
    private Label attendanceRateLabel;
    
    @FXML
    private Label pendingPayrollLabel;
    
    @FXML
    private Label payrollAmountLabel;
    
    @FXML
    private Label systemStatusLabel;
    
    @FXML
    private TableView<?> recentAttendanceTable;
    
    private EmployeeDAO employeeDAO;
    
    @FXML
    public void initialize() {
        employeeDAO = new EmployeeDAO();
        
        // Load stats in background
        loadStatistics();
    }
    
    /**
     * Load dashboard statistics
     */
    private void loadStatistics() {
        new Thread(() -> {
            try {
                // Get total employees
                long totalEmployees = employeeDAO.count(true);
                
                Platform.runLater(() -> {
                    totalEmployeesLabel.setText(String.valueOf(totalEmployees));
                    employeesChangeLabel.setText("+0 this month");
                    
                    // Mock data for other stats
                    todayAttendanceLabel.setText(String.valueOf(totalEmployees));
                    attendanceRateLabel.setText("100% attendance rate");
                    
                    pendingPayrollLabel.setText("0");
                    payrollAmountLabel.setText(currencyFormat.format(0));
                    
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
}
