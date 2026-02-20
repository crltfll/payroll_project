package com.example.payroll_project.controller;

import com.example.payroll_project.dao.AttendanceDAO;
import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.model.Employee;
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
import java.util.*;

/**
 * Attendance Controller (CR1: FA2000 Biometric Attendance Integration)
 *
 * FIX: Employee-code matching now tries multiple formats so that a device
 *      ID of "7" will match database codes "EMP007", "007", "07", or "7".
 */
public class AttendanceController {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
    @FXML private TableColumn<AttendanceRecord, Void>   actionsColumn;
    @FXML private Label pageLabel;

    private final AttendanceDAO attDAO  = new AttendanceDAO();
    private final EmployeeDAO   empDAO  = new EmployeeDAO();

    private final ObservableList<AttendanceRecord> allRecords      = FXCollections.observableArrayList();
    private final ObservableList<AttendanceRecord> filteredRecords = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now);
        attendanceTable.setItems(filteredRecords);
        loadFromDatabase();
    }

    // -----------------------------------------------------------------------
    // Import
    // -----------------------------------------------------------------------

    @FXML
    private void handleImportCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select FA2000 All_Report CSV File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showOpenDialog(attendanceTable.getScene().getWindow());
        if (file != null) importCSVFile(file);
    }

    private void importCSVFile(File file) {
        Alert loading = new Alert(Alert.AlertType.INFORMATION);
        loading.setTitle("Importing");
        loading.setHeaderText("Processing FA2000 CSV…");
        loading.setContentText("Parsing attendance records and detecting anomalies (F3). Please wait.");
        loading.show();

        new Thread(() -> {
            try {
                Map<String, List<AttendanceRecord>> byEmployee =
                        FA2000CSVParser.parseAllEmployees(file.getAbsolutePath());

                int total = 0, saved = 0, anomalyCount = 0;
                // Track unmatched so we can tell the user exactly what codes to use
                List<String> unmatchedDetails = new ArrayList<>();

                for (Map.Entry<String, List<AttendanceRecord>> entry : byEmployee.entrySet()) {
                    String parsedCode = entry.getKey();          // e.g. "EMP007"
                    List<AttendanceRecord> records = entry.getValue();

                    // --- FIX: Try several code variants ----------------------------
                    Optional<Employee> empOpt = resolveEmployee(parsedCode);
                    // ---------------------------------------------------------------

                    Integer empId = empOpt.map(Employee::getEmployeeId).orElse(null);

                    if (empId == null) {
                        // Build a helpful message listing all codes we tried
                        String tried = String.join(", ", buildCodeCandidates(parsedCode));
                        unmatchedDetails.add(
                                "  • CSV device ID → " + parsedCode
                                + "\n    Tried codes: " + tried
                                + "\n    → Register the employee with one of these codes, then re-import.");
                        logger.warn("No employee matched for parsed code '{}'. Tried: {}", parsedCode, tried);
                    }

                    for (AttendanceRecord rec : records) {
                        total++;
                        if (rec.isHasAnomaly()) anomalyCount++;

                        if (empId != null) {
                            rec.setEmployeeId(empId);
                            try {
                                attDAO.upsert(rec);
                                saved++;
                            } catch (Exception ex) {
                                logger.warn("Upsert failed for {}: {}", parsedCode, ex.getMessage());
                            }
                        }
                    }
                }

                final int totalF = total, savedF = saved, anomF = anomalyCount;
                final Map<String, List<AttendanceRecord>> byEmpF = byEmployee;
                final List<String> unmatchedF = unmatchedDetails;

                javafx.application.Platform.runLater(() -> {
                    loading.close();

                    allRecords.clear();
                    byEmpF.values().forEach(allRecords::addAll);
                    applyFilters();
                    updateStats();

                    StringBuilder msg = new StringBuilder();
                    msg.append(String.format(
                            "Employees found : %d\n"
                          + "Total records   : %d\n"
                          + "Saved to DB     : %d\n"
                          + "Anomalies (F3)  : %d\n",
                            byEmpF.size(), totalF, savedF, anomF));

                    if (!unmatchedF.isEmpty()) {
                        msg.append("\n⚠️  UNMATCHED EMPLOYEES (records NOT saved):\n");
                        unmatchedF.forEach(msg::append);
                        msg.append("\n\nQuick fix: open the employee record and set the\n"
                                 + "Employee Code field to the value shown above, then re-import.");
                    } else {
                        msg.append("\n✅ All employees matched successfully.");
                    }

                    Alert result = new Alert(
                            unmatchedF.isEmpty()
                                ? Alert.AlertType.INFORMATION
                                : Alert.AlertType.WARNING);
                    result.setTitle("Import Complete");
                    result.setHeaderText("FA2000 CSV Import " + (unmatchedF.isEmpty() ? "Successful" : "— Action Required"));
                    result.setContentText(msg.toString());
                    // Make dialog wide enough to read the details
                    result.getDialogPane().setMinWidth(560);
                    result.showAndWait();
                });

            } catch (Exception e) {
                logger.error("Import failed", e);
                javafx.application.Platform.runLater(() -> {
                    loading.close();
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Import Failed");
                    err.setHeaderText("Error processing CSV");
                    err.setContentText(e.getMessage());
                    err.showAndWait();
                });
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Employee code resolution  (THE FIX)
    // -----------------------------------------------------------------------

    /**
     * The FA2000 parser always generates codes like "EMP007" from device ID "7".
     * Users may have registered the employee with any of several formats.
     * This method tries them all in order of likelihood.
     *
     * Candidates tried for device ID "7" / parsed code "EMP007":
     *   1. EMP007   (what the parser generates)
     *   2. 7        (raw numeric – most common manual entry)
     *   3. 07       (zero-padded 2-digit)
     *   4. 007      (zero-padded 3-digit)
     *   5. EMP7     (no padding variant)
     *   6. EMP07    (2-digit padded variant)
     */
    private Optional<Employee> resolveEmployee(String parsedCode) throws java.sql.SQLException {
        for (String candidate : buildCodeCandidates(parsedCode)) {
            Optional<Employee> found = empDAO.findByEmployeeCode(candidate);
            if (found.isPresent()) {
                logger.info("Matched '{}' via candidate code '{}'", parsedCode, candidate);
                return found;
            }
        }
        return Optional.empty();
    }

    private List<String> buildCodeCandidates(String parsedCode) {
        List<String> candidates = new ArrayList<>();
        candidates.add(parsedCode);                            // "EMP007"

        // Extract the trailing digits
        String digits = parsedCode.replaceAll("[^0-9]", "");   // "007"
        if (!digits.isEmpty()) {
            // Strip leading zeros → raw number
            String raw = digits.replaceFirst("^0+", "");       // "7"
            if (raw.isEmpty()) raw = "0";

            candidates.add(raw);                               // "7"
            // 2-digit zero-padded
            candidates.add(String.format("%02d", Integer.parseInt(raw)));  // "07"
            // 3-digit zero-padded
            candidates.add(String.format("%03d", Integer.parseInt(raw)));  // "007"
            // EMP + raw
            candidates.add("EMP" + raw);                       // "EMP7"
            // EMP + 2-digit
            candidates.add("EMP" + String.format("%02d", Integer.parseInt(raw))); // "EMP07"
        }

        // De-duplicate while preserving order
        return new ArrayList<>(new LinkedHashSet<>(candidates));
    }

    // -----------------------------------------------------------------------
    // Load from DB on initialize
    // -----------------------------------------------------------------------

    private void loadFromDatabase() {
        new Thread(() -> {
            try {
                LocalDate s = startDatePicker.getValue();
                LocalDate e = endDatePicker.getValue();
                List<AttendanceRecord> records = attDAO.findByDateRange(s, e);
                javafx.application.Platform.runLater(() -> {
                    allRecords.setAll(records);
                    applyFilters();
                    updateStats();
                });
            } catch (Exception ex) {
                logger.error("Load from DB failed", ex);
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Table columns
    // -----------------------------------------------------------------------

    private void setupTableColumns() {
        employeeCodeColumn.setCellValueFactory(c ->
                new SimpleStringProperty("EMP-" + c.getValue().getEmployeeId()));

        employeeNameColumn.setCellValueFactory(c -> {
            Integer id = c.getValue().getEmployeeId();
            if (id == null) return new SimpleStringProperty("Unknown");
            try {
                Optional<Employee> e = empDAO.findById(id);
                return new SimpleStringProperty(e.map(Employee::getFullName).orElse("ID:" + id));
            } catch (Exception ex) {
                return new SimpleStringProperty("ID:" + id);
            }
        });

        dateColumn.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getAttendanceDate();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "-");
        });

        timeIn1Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeIn1();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeOut1Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeOut1();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeIn2Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeIn2();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeOut2Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeOut2();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "Missed");
        });

        regularHoursColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isAbsent()) return new SimpleStringProperty("0.0");
            if (r.getTimeIn1() != null && r.getTimeOut2() != null) {
                long mins = java.time.Duration.between(r.getTimeIn1(), r.getTimeOut2()).toMinutes();
                if (r.getTimeOut1() != null && r.getTimeIn2() != null) {
                    mins -= java.time.Duration.between(r.getTimeOut1(), r.getTimeIn2()).toMinutes();
                } else if (mins > 300) {
                    mins -= 60;
                }
                double reg = Math.min(mins / 60.0, 8.0);
                return new SimpleStringProperty(String.format("%.1f", reg));
            }
            return new SimpleStringProperty("-");
        });

        overtimeColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isAbsent()) return new SimpleStringProperty("0.0");
            if (r.getTimeIn1() != null && r.getTimeOut2() != null) {
                long mins = java.time.Duration.between(r.getTimeIn1(), r.getTimeOut2()).toMinutes();
                if (r.getTimeOut1() != null && r.getTimeIn2() != null) {
                    mins -= java.time.Duration.between(r.getTimeOut1(), r.getTimeIn2()).toMinutes();
                } else if (mins > 300) {
                    mins -= 60;
                }
                double ot = Math.max(0, mins / 60.0 - 8.0);
                return new SimpleStringProperty(ot > 0 ? String.format("%.1f", ot) : "0.0");
            }
            return new SimpleStringProperty("0.0");
        });

        statusColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isAbsent()) return new SimpleStringProperty("ABSENT");
            if (r.isHasAnomaly()) return new SimpleStringProperty("ANOMALY");
            return new SimpleStringProperty("PRESENT");
        });
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label(item);
                b.getStyleClass().add("badge");
                b.getStyleClass().add(switch (item) {
                    case "PRESENT" -> "badge-success";
                    case "ABSENT"  -> "badge-error";
                    default        -> "badge-warning";
                });
                setGraphic(b);
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                viewBtn.getStyleClass().add("button-secondary");
                viewBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                editBtn.getStyleClass().add("button-secondary");
                editBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                AttendanceRecord r = getTableView().getItems().get(getIndex());
                viewBtn.setOnAction(e -> showDetail(r));
                editBtn.setOnAction(e -> showInfoAlert("Edit",
                        "Manual editing of attendance records (F10) will be available in a future release."));
                setGraphic(new HBox(5, viewBtn, editBtn));
            }
        });
    }

    // -----------------------------------------------------------------------
    // Filters & stats
    // -----------------------------------------------------------------------

    @FXML private void handleApplyDateFilter() { loadFromDatabase(); }
    @FXML private void handleFilterToday()     { LocalDate t = LocalDate.now(); startDatePicker.setValue(t); endDatePicker.setValue(t); loadFromDatabase(); }
    @FXML private void handleFilterThisWeek()  { LocalDate n = LocalDate.now(); startDatePicker.setValue(n.minusDays(n.getDayOfWeek().getValue()-1)); endDatePicker.setValue(n); loadFromDatabase(); }
    @FXML private void handleFilterThisMonth() { LocalDate n = LocalDate.now(); startDatePicker.setValue(n.withDayOfMonth(1)); endDatePicker.setValue(n); loadFromDatabase(); }
    @FXML private void handleFilterAnomalies() { applyFilters(); }
    @FXML private void handleRefresh()         { loadFromDatabase(); }
    @FXML private void handleValidateAll()     { showInfoAlert("Validate","Anomaly detection runs automatically during import (F3). Check yellow badges for flagged records."); }
    @FXML private void handleExport()          { showInfoAlert("Export","Report export will be implemented in a future update."); }
    @FXML private void handlePreviousPage()    { /* TODO pagination */ }
    @FXML private void handleNextPage()        { /* TODO pagination */ }

    private void applyFilters() {
        filteredRecords.clear();
        LocalDate s  = startDatePicker.getValue();
        LocalDate e  = endDatePicker.getValue();
        boolean ano  = showAnomaliesOnly.isSelected();
        for (AttendanceRecord r : allRecords) {
            if (s != null && r.getAttendanceDate().isBefore(s)) continue;
            if (e != null && r.getAttendanceDate().isAfter(e))  continue;
            if (ano && !r.isHasAnomaly()) continue;
            filteredRecords.add(r);
        }
        recordCountLabel.setText(filteredRecords.size() + " records");
    }

    private void updateStats() {
        long total     = allRecords.size();
        long valid     = allRecords.stream().filter(r -> !r.isHasAnomaly()).count();
        long anomalies = allRecords.stream().filter(AttendanceRecord::isHasAnomaly).count();
        long absent    = allRecords.stream().filter(AttendanceRecord::isAbsent).count();

        lastImportLabel.setText(total + " records");
        lastImportDateLabel.setText(LocalDate.now().format(DATE_FMT));
        validRecordsLabel.setText(String.valueOf(valid));
        anomaliesLabel.setText(String.valueOf(anomalies));
        attendanceRateLabel.setText(total > 0
                ? String.format("%.1f%%", (total - absent) * 100.0 / total) : "0%");
    }

    private void showDetail(AttendanceRecord r) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Attendance Details");
        a.setHeaderText("Record for " + r.getAttendanceDate().format(DATE_FMT));
        StringBuilder sb = new StringBuilder();
        sb.append("Date    : ").append(r.getAttendanceDate().format(DATE_FMT)).append("\n\n");
        sb.append("In      : ").append(r.getTimeIn1()  != null ? r.getTimeIn1().format(TIME_FMT)  : "Missed").append("\n");
        sb.append("Lunch ✕ : ").append(r.getTimeOut1() != null ? r.getTimeOut1().format(TIME_FMT) : "Missed").append("\n");
        sb.append("Lunch → : ").append(r.getTimeIn2()  != null ? r.getTimeIn2().format(TIME_FMT)  : "Missed").append("\n");
        sb.append("Out     : ").append(r.getTimeOut2() != null ? r.getTimeOut2().format(TIME_FMT) : "Missed").append("\n\n");
        if (r.isAbsent())          sb.append("Status  : ABSENT\n");
        else if (r.isHasAnomaly()) sb.append("Status  : ANOMALY – ").append(r.getAnomalyDescription()).append("\n");
        else                       sb.append("Status  : PRESENT\n");
        a.setContentText(sb.toString());
        a.showAndWait();
    }

    private void showInfoAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
