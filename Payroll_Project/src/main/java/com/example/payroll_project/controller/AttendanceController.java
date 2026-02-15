package com.example.payroll_project.controller;

import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.util.FA2000CSVParser;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Attendance Controller (CR1: FA2000 Biometric Attendance Integration)
 * Handles attendance import, validation, and management with F3: Intelligent Validation
 */
public class AttendanceController {
    
    private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    @FXML private Label lastImportLabel;
    @FXML private Label lastImportDateLabel;
    @FXML private Label validRecordsLabel;
    @FXML private Label anomaliesLabel;
    @FXML private Label attendanceRateLabel;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private CheckBox showAnomaliesOnly;
    @FXML private Label recordCountLabel;
    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, String> employeeCodeColumn;
    @FXML private TableColumn<AttendanceRecord, String> employeeNameColumn;
    @FXML private TableColumn<AttendanceRecord, String> dateColumn;
    @FXML private TableColumn<AttendanceRecord, String> timeIn1Column;
    @FXML private TableColumn<AttendanceRecord, String> timeOut1Column;
    @FXML private TableColumn<AttendanceRecord, String> timeIn2Column;
    @FXML private TableColumn<AttendanceRecord, String> timeOut2Column;
    @FXML private TableColumn<AttendanceRecord, String> regularHoursColumn;
    @FXML private TableColumn<AttendanceRecord, String> overtimeColumn;
    @FXML private TableColumn<AttendanceRecord, String> statusColumn;
    @FXML private TableColumn<AttendanceRecord, Void> actionsColumn;
    @FXML private Label pageLabel;
    
    private ObservableList<AttendanceRecord> allRecords;
    private ObservableList<AttendanceRecord> filteredRecords;
    
    @FXML
    public void initialize() {
        allRecords = FXCollections.observableArrayList();
        filteredRecords = FXCollections.observableArrayList();
        
        setupTableColumns();
        
        // Set default date range (current month)
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now);
        
        attendanceTable.setItems(filteredRecords);
    }
    
    private void setupTableColumns() {
        // Employee Code - for now show "EMPLOYEE" as placeholder
        employeeCodeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty("EMP-" + cellData.getValue().getAttendanceDate().getDayOfMonth())
        );
        
        // Employee Name - placeholder
        employeeNameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty("Employee " + cellData.getValue().getAttendanceDate().getDayOfMonth())
        );
        
        // Date
        dateColumn.setCellValueFactory(cellData -> {
            LocalDate date = cellData.getValue().getAttendanceDate();
            return new SimpleStringProperty(date != null ? date.format(DATE_FORMATTER) : "-");
        });
        
        // Time In 1
        timeIn1Column.setCellValueFactory(cellData -> {
            var time = cellData.getValue().getTimeIn1();
            return new SimpleStringProperty(time != null ? time.format(TIME_FORMATTER) : "Missed");
        });
        
        // Time Out 1
        timeOut1Column.setCellValueFactory(cellData -> {
            var time = cellData.getValue().getTimeOut1();
            return new SimpleStringProperty(time != null ? time.format(TIME_FORMATTER) : "Missed");
        });
        
        // Time In 2
        timeIn2Column.setCellValueFactory(cellData -> {
            var time = cellData.getValue().getTimeIn2();
            return new SimpleStringProperty(time != null ? time.format(TIME_FORMATTER) : "Missed");
        });
        
        // Time Out 2
        timeOut2Column.setCellValueFactory(cellData -> {
            var time = cellData.getValue().getTimeOut2();
            return new SimpleStringProperty(time != null ? time.format(TIME_FORMATTER) : "Missed");
        });
        
        // Regular Hours - calculated
        regularHoursColumn.setCellValueFactory(cellData -> {
            AttendanceRecord record = cellData.getValue();
            if (record.isAbsent()) {
                return new SimpleStringProperty("0.0");
            }
            
            // Calculate hours between timeIn1 and timeOut2
            if (record.getTimeIn1() != null && record.getTimeOut2() != null) {
                long minutes = java.time.Duration.between(
                    record.getTimeIn1(), 
                    record.getTimeOut2()
                ).toMinutes();
                
                // Subtract lunch break if present (assume 1 hour)
                if (record.getTimeOut1() != null && record.getTimeIn2() != null) {
                    long lunchMinutes = java.time.Duration.between(
                        record.getTimeOut1(),
                        record.getTimeIn2()
                    ).toMinutes();
                    minutes -= lunchMinutes;
                }
                
                double hours = minutes / 60.0;
                return new SimpleStringProperty(String.format("%.1f", hours));
            }
            
            return new SimpleStringProperty("-");
        });
        
        // Overtime - placeholder
        overtimeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty("0.0")
        );
        
        // Status with styling
        statusColumn.setCellValueFactory(cellData -> {
            AttendanceRecord record = cellData.getValue();
            if (record.isAbsent()) {
                return new SimpleStringProperty("ABSENT");
            } else if (record.isHasAnomaly()) {
                return new SimpleStringProperty("ANOMALY");
            } else {
                return new SimpleStringProperty("PRESENT");
            }
        });
        
        statusColumn.setCellFactory(column -> new TableCell<AttendanceRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    
                    switch (item) {
                        case "PRESENT":
                            badge.getStyleClass().add("badge-success");
                            break;
                        case "ABSENT":
                            badge.getStyleClass().add("badge-error");
                            break;
                        case "ANOMALY":
                            badge.getStyleClass().add("badge-warning");
                            break;
                    }
                    
                    setGraphic(badge);
                }
            }
        });
        
        // Actions column
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button viewButton = new Button("View");
            private final Button editButton = new Button("Edit");
            private final HBox pane = new HBox(5, viewButton, editButton);
            
            {
                viewButton.getStyleClass().add("button-secondary");
                viewButton.setStyle("-fx-padding: 4px 12px; -fx-font-size: 11px;");
                editButton.getStyleClass().add("button-secondary");
                editButton.setStyle("-fx-padding: 4px 12px; -fx-font-size: 11px;");
                
                viewButton.setOnAction(event -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    handleViewRecord(record);
                });
                
                editButton.setOnAction(event -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    handleEditRecord(record);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
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
        loadingAlert.setContentText("Please wait while we process the attendance records.\n\nThis may take a moment for large files.");
        loadingAlert.show();
        
        new Thread(() -> {
            try {
                // Parse CSV using updated parser
                List<AttendanceRecord> records = FA2000CSVParser.parseCSV(file.getAbsolutePath());
                
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    
                    if (records.isEmpty()) {
                        Alert warningAlert = new Alert(Alert.AlertType.WARNING);
                        warningAlert.setTitle("Import Warning");
                        warningAlert.setHeaderText("No Records Found");
                        warningAlert.setContentText("The CSV file did not contain any valid attendance records.\n\nPlease check the file format and try again.");
                        warningAlert.showAndWait();
                        return;
                    }
                    
                    // Add records to the list
                    allRecords.addAll(records);
                    applyFilters();
                    
                    // Update stats
                    updateStats();
                    
                    // Show success message
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Import Successful");
                    successAlert.setHeaderText("CSV Import Complete");
                    successAlert.setContentText(
                        String.format("Successfully imported %d attendance records.\n\n" +
                                     "File: %s\n" +
                                     "Valid Records: %d\n" +
                                     "Records with Anomalies: %d",
                                     records.size(),
                                     file.getName(),
                                     records.stream().filter(r -> !r.isHasAnomaly()).count(),
                                     records.stream().filter(AttendanceRecord::isHasAnomaly).count())
                    );
                    successAlert.showAndWait();
                });
                
            } catch (Exception e) {
                logger.error("Failed to import CSV", e);
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Import Failed");
                    errorAlert.setHeaderText("Failed to import CSV");
                    errorAlert.setContentText(
                        "An error occurred while processing the file:\n\n" +
                        e.getMessage() + "\n\n" +
                        "Please check that the file is a valid FA2000 attendance export."
                    );
                    errorAlert.showAndWait();
                });
            }
        }).start();
    }
    
    private void updateStats() {
        long total = allRecords.size();
        long valid = allRecords.stream().filter(r -> !r.isHasAnomaly()).count();
        long anomalies = allRecords.stream().filter(AttendanceRecord::isHasAnomaly).count();
        long absent = allRecords.stream().filter(AttendanceRecord::isAbsent).count();
        
        lastImportLabel.setText(total + " records");
        lastImportDateLabel.setText(LocalDate.now().format(DATE_FORMATTER));
        validRecordsLabel.setText(String.valueOf(valid));
        anomaliesLabel.setText(String.valueOf(anomalies));
        
        if (total > 0) {
            double presentRate = ((total - absent) * 100.0) / total;
            attendanceRateLabel.setText(String.format("%.1f%%", presentRate));
        }
    }
    
    private void applyFilters() {
        filteredRecords.clear();
        
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        boolean anomaliesOnly = showAnomaliesOnly.isSelected();
        
        for (AttendanceRecord record : allRecords) {
            // Date filter
            if (startDate != null && record.getAttendanceDate().isBefore(startDate)) {
                continue;
            }
            if (endDate != null && record.getAttendanceDate().isAfter(endDate)) {
                continue;
            }
            
            // Anomaly filter
            if (anomaliesOnly && !record.isHasAnomaly()) {
                continue;
            }
            
            filteredRecords.add(record);
        }
        
        recordCountLabel.setText(filteredRecords.size() + " records");
    }
    
    @FXML
    private void handleExport() {
        showInfoAlert("Export", "Export functionality will be implemented in a future update.");
    }
    
    @FXML
    private void handleApplyDateFilter() {
        applyFilters();
    }
    
    @FXML
    private void handleFilterToday() {
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today);
        endDatePicker.setValue(today);
        applyFilters();
    }
    
    @FXML
    private void handleFilterThisWeek() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.minusDays(now.getDayOfWeek().getValue() - 1));
        endDatePicker.setValue(now);
        applyFilters();
    }
    
    @FXML
    private void handleFilterThisMonth() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now);
        applyFilters();
    }
    
    @FXML
    private void handleFilterAnomalies() {
        applyFilters();
    }
    
    @FXML
    private void handleValidateAll() {
        showInfoAlert("Validate All", 
            "All records have been validated during import.\n\n" +
            "Records with anomalies are marked with WARNING status.\n" +
            "Use the filter to show only anomalies.");
    }
    
    @FXML
    private void handleRefresh() {
        applyFilters();
    }
    
    @FXML
    private void handlePreviousPage() {
        // Pagination implementation - TODO
    }
    
    @FXML
    private void handleNextPage() {
        // Pagination implementation - TODO
    }
    
    private void handleViewRecord(AttendanceRecord record) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Attendance Details");
        alert.setHeaderText("Attendance for " + record.getAttendanceDate().format(DATE_FORMATTER));
        
        StringBuilder content = new StringBuilder();
        content.append("Date: ").append(record.getAttendanceDate().format(DATE_FORMATTER)).append("\n\n");
        content.append("Morning In: ").append(record.getTimeIn1() != null ? record.getTimeIn1().format(TIME_FORMATTER) : "Missed").append("\n");
        content.append("Lunch Out: ").append(record.getTimeOut1() != null ? record.getTimeOut1().format(TIME_FORMATTER) : "Missed").append("\n");
        content.append("Lunch In: ").append(record.getTimeIn2() != null ? record.getTimeIn2().format(TIME_FORMATTER) : "Missed").append("\n");
        content.append("Evening Out: ").append(record.getTimeOut2() != null ? record.getTimeOut2().format(TIME_FORMATTER) : "Missed").append("\n\n");
        
        if (record.isAbsent()) {
            content.append("Status: ABSENT\n");
        } else if (record.isHasAnomaly()) {
            content.append("Status: ANOMALY DETECTED\n");
            content.append("Details: ").append(record.getAnomalyDescription()).append("\n");
        } else {
            content.append("Status: PRESENT\n");
        }
        
        alert.setContentText(content.toString());
        alert.showAndWait();
    }
    
    private void handleEditRecord(AttendanceRecord record) {
        showInfoAlert("Edit Record", 
            "Manual editing will be available in a future update.\n\n" +
            "This feature allows authorized users to correct attendance anomalies.");
    }
    
    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
