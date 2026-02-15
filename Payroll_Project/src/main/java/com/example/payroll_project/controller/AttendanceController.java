package com.example.payroll_project.controller;

import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.util.FA2000CSVParser;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Attendance Controller (CR1: FA2000 Biometric Attendance Integration)
 * Handles attendance import, validation, and management
 */
public class AttendanceController {
    
    private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);
    
    @FXML
    private Label lastImportLabel;
    
    @FXML
    private Label lastImportDateLabel;
    
    @FXML
    private Label validRecordsLabel;
    
    @FXML
    private Label anomaliesLabel;
    
    @FXML
    private Label attendanceRateLabel;
    
    @FXML
    private DatePicker startDatePicker;
    
    @FXML
    private DatePicker endDatePicker;
    
    @FXML
    private CheckBox showAnomaliesOnly;
    
    @FXML
    private Label recordCountLabel;
    
    @FXML
    private TableView<AttendanceRecord> attendanceTable;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> employeeCodeColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> employeeNameColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> dateColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> timeIn1Column;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> timeOut1Column;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> timeIn2Column;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> timeOut2Column;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> regularHoursColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> overtimeColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> statusColumn;
    
    @FXML
    private TableColumn<AttendanceRecord, ?> actionsColumn;
    
    @FXML
    private Label pageLabel;
    
    @FXML
    public void initialize() {
        // Setup table columns
        setupTableColumns();
        
        // Set default date range (current month)
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
    }
    
    private void setupTableColumns() {
        // Table column setup would go here
        //TODO
    }
    
    @FXML
    private void handleImportCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select FA2000 CSV File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        File file = fileChooser.showOpenDialog(attendanceTable.getScene().getWindow());
        
        if (file != null) {
            importCSVFile(file);
        }
    }
    
    private void importCSVFile(File file) {
        // Show loading indicator
        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Importing");
        loadingAlert.setHeaderText("Importing FA2000 CSV...");
        loadingAlert.setContentText("Please wait while we process the attendance records.");
        loadingAlert.show();
        
        new Thread(() -> {
            try {
                // Parse CSV using FA2000CSVParser
                List<AttendanceRecord> records = FA2000CSVParser.parseCSV(file.getAbsolutePath());
                
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    
                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Import Successful");
                    successAlert.setHeaderText("CSV Import Complete");
                    successAlert.setContentText(
                        String.format("Successfully imported %d attendance records.", records.size())
                    );
                    successAlert.showAndWait();
                    
                    // Update stats
                    updateStats(records);
                    
                    // Refresh table
                    handleRefresh();
                });
                
            } catch (Exception e) {
                logger.error("Failed to import CSV", e);
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Import Failed");
                    errorAlert.setHeaderText("Failed to import CSV");
                    errorAlert.setContentText(e.getMessage());
                    errorAlert.showAndWait();
                });
            }
        }).start();
    }
    
    private void updateStats(List<AttendanceRecord> records) {
        lastImportLabel.setText(records.size() + " records");
        lastImportDateLabel.setText(LocalDate.now().toString());
        
        long validRecords = records.stream().filter(r -> !r.isHasAnomaly()).count();
        long anomalies = records.stream().filter(AttendanceRecord::isHasAnomaly).count();
        
        validRecordsLabel.setText(String.valueOf(validRecords));
        anomaliesLabel.setText(String.valueOf(anomalies));
        
        if (!records.isEmpty()) {
            double rate = (validRecords * 100.0) / records.size();
            attendanceRateLabel.setText(String.format("%.1f%%", rate));
        }
    }
    
    @FXML
    private void handleExport() {
        showInfoAlert("Export", "Export functionality will be implemented");
    }
    
    @FXML
    private void handleApplyDateFilter() {
        // Apply date range filter
    }
    
    @FXML
    private void handleFilterToday() {
        startDatePicker.setValue(LocalDate.now());
        endDatePicker.setValue(LocalDate.now());
        handleApplyDateFilter();
    }
    
    @FXML
    private void handleFilterThisWeek() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.minusDays(now.getDayOfWeek().getValue() - 1));
        endDatePicker.setValue(now);
        handleApplyDateFilter();
    }
    
    @FXML
    private void handleFilterThisMonth() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now);
        handleApplyDateFilter();
    }
    
    @FXML
    private void handleFilterAnomalies() {
        // Filter to show only records with anomalies
    }
    
    @FXML
    private void handleValidateAll() {
        showInfoAlert("Validate", "Validation functionality will be implemented");
    }
    
    @FXML
    private void handleRefresh() {
        // Refresh table data
    }
    
    @FXML
    private void handlePreviousPage() {
        // Pagination
    }
    
    @FXML
    private void handleNextPage() {
        // Pagination
    }
    
    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
