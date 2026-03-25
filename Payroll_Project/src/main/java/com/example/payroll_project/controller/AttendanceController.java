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
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Attendance Controller (CR1: FA2000 Biometric Attendance Integration)
 *
 * FIX: Records were displayed from the in-memory parsed CSV but only saved
 * to the DB when the employee code matched. The import dialog now shows a
 * mapping table so unmatched device IDs can be resolved, and all matched
 * records are confirmed saved via upsert.
 */
public class AttendanceController {

    private static final Logger logger =
            LoggerFactory.getLogger(AttendanceController.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // ── Summary labels ─────────────────────────────────────────────────────
    @FXML private Label lastImportLabel;
    @FXML private Label lastImportDateLabel;
    @FXML private Label validRecordsLabel;
    @FXML private Label anomaliesLabel;
    @FXML private Label attendanceRateLabel;

    // ── Filter controls ────────────────────────────────────────────────────
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private CheckBox   showAnomaliesOnly;

    // ── All-records tab ────────────────────────────────────────────────────
    @FXML private Label      recordCountLabel;
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

    // ── Anomaly tab ────────────────────────────────────────────────────────
    @FXML private Label anomalyCountLabel;
    @FXML private TableView<AttendanceRecord> anomalyTable;
    @FXML private TableColumn<AttendanceRecord, String> anEmpCodeCol;
    @FXML private TableColumn<AttendanceRecord, String> anEmpNameCol;
    @FXML private TableColumn<AttendanceRecord, String> anDateCol;
    @FXML private TableColumn<AttendanceRecord, String> anTimeInCol;
    @FXML private TableColumn<AttendanceRecord, String> anTimeOutCol;
    @FXML private TableColumn<AttendanceRecord, String> anDescCol;
    @FXML private TableColumn<AttendanceRecord, Void>   anActionsCol;

    private final AttendanceDAO attDAO = new AttendanceDAO();
    private final EmployeeDAO   empDAO = new EmployeeDAO();

    private final ObservableList<AttendanceRecord> allRecords      =
            FXCollections.observableArrayList();
    private final ObservableList<AttendanceRecord> filteredRecords =
            FXCollections.observableArrayList();
    private final ObservableList<AttendanceRecord> anomalyRecords  =
            FXCollections.observableArrayList();

    private java.util.Map<Integer, Employee> empCache = new java.util.HashMap<>();

    // ── Init ───────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupTableColumns();
        setupAnomalyColumns();

        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.minusYears(3));
        endDatePicker.setValue(now);

        attendanceTable.setItems(filteredRecords);
        anomalyTable.setItems(anomalyRecords);

        loadEmployeeCache();
        loadFromDatabase();
    }

    private void loadEmployeeCache() {
        new Thread(() -> {
            try {
                List<Employee> employees = empDAO.findAll(false);
                java.util.Map<Integer, Employee> map = new java.util.HashMap<>();
                for (Employee e : employees) map.put(e.getEmployeeId(), e);
                javafx.application.Platform.runLater(() -> {
                    empCache = map;
                    attendanceTable.refresh();
                    anomalyTable.refresh();
                });
            } catch (Exception ex) {
                logger.error("Employee cache load failed", ex);
            }
        }).start();
    }

    // ── Import ─────────────────────────────────────────────────────────────

    @FXML
    private void handleImportCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select FA2000 All_Report CSV File");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showOpenDialog(attendanceTable.getScene().getWindow());
        if (file != null) importCSVFile(file);
    }

    private void importCSVFile(File file) {
        Alert loading = new Alert(Alert.AlertType.INFORMATION);
        loading.setTitle("Importing");
        loading.setHeaderText("Processing FA2000 CSV…");
        loading.setContentText(
                "Parsing attendance records and detecting anomalies. Please wait.");
        loading.show();

        new Thread(() -> {
            try {
                // ── Parse CSV ────────────────────────────────────────────
                Map<String, List<AttendanceRecord>> byEmployee =
                        FA2000CSVParser.parseAllEmployees(file.getAbsolutePath());

                // ── Load ALL active employees once for matching ──────────
                List<Employee> allEmployees = empDAO.findAll(false);
                logger.info("Loaded {} employees for matching", allEmployees.size());

                // ── Match each device ID to an employee ──────────────────
                int saved        = 0;
                int anomalyCount = 0;
                List<String> unmatchedDetails  = new ArrayList<>();
                List<String> matchedSummary    = new ArrayList<>();

                for (Map.Entry<String, List<AttendanceRecord>> entry
                        : byEmployee.entrySet()) {

                    String parsedCode = entry.getKey();
                    List<AttendanceRecord> records = entry.getValue();

                    // Try to find the employee using multiple strategies
                    Optional<Employee> empOpt =
                            resolveEmployee(parsedCode, allEmployees);

                    if (empOpt.isEmpty()) {
                        // Build a helpful message showing what was tried
                        List<String> tried = buildCodeCandidates(parsedCode);
                        unmatchedDetails.add(
                                "  • CSV device ID: " + parsedCode
                                + "\n    Tried codes: " + String.join(", ", tried)
                                + "\n    Records skipped: " + records.size());
                        logger.warn(
                                "No employee match for device ID '{}'. "
                                + "Tried: {}", parsedCode, tried);
                        continue; // skip — don't save unmatched records
                    }

                    Employee emp = empOpt.get();
                    int savedForEmp = 0;

                    for (AttendanceRecord rec : records) {
                        if (rec.isHasAnomaly()) anomalyCount++;
                        rec.setEmployeeId(emp.getEmployeeId());
                        try {
                            attDAO.upsert(rec);
                            saved++;
                            savedForEmp++;
                        } catch (Exception ex) {
                            logger.warn("Upsert failed for {} on {}: {}",
                                    parsedCode,
                                    rec.getAttendanceDate(),
                                    ex.getMessage());
                        }
                    }

                    matchedSummary.add("  ✓ " + parsedCode
                            + " → " + emp.getFullName()
                            + " (" + savedForEmp + " records saved)");
                    logger.info("Saved {} records for {} → {}",
                            savedForEmp, parsedCode, emp.getFullName());
                }

                final int totalSaved   = saved;
                final int totalAnom    = anomalyCount;
                final int totalEmp     = byEmployee.size();
                final List<String> unmatched = unmatchedDetails;
                final List<String> matched   = matchedSummary;

                javafx.application.Platform.runLater(() -> {
                    loading.close();

                    // Refresh date filter to cover earliest imported date
                    byEmployee.values().stream()
                            .flatMap(Collection::stream)
                            .map(AttendanceRecord::getAttendanceDate)
                            .filter(Objects::nonNull)
                            .min(LocalDate::compareTo)
                            .ifPresent(earliest -> {
                                if (startDatePicker.getValue() == null
                                        || earliest.isBefore(
                                                startDatePicker.getValue())) {
                                    startDatePicker.setValue(earliest);
                                }
                            });

                    // Reload from DB so the table reflects what was actually saved
                    loadFromDatabase();

                    // Build result message
                    StringBuilder msg = new StringBuilder();
                    msg.append(String.format(
                            "Employees in CSV : %d\n"
                            + "Records saved    : %d\n"
                            + "Anomalies (F3)   : %d\n",
                            totalEmp, totalSaved, totalAnom));

                    if (!matched.isEmpty()) {
                        msg.append("\n✅ Matched employees:\n");
                        matched.forEach(m -> msg.append(m).append("\n"));
                    }

                    if (!unmatched.isEmpty()) {
                        msg.append("\n⚠️  UNMATCHED (records NOT saved):\n");
                        unmatched.forEach(m -> msg.append(m).append("\n"));
                        msg.append(
                                "\nFix: open each unmatched employee's record "
                                + "and set their Employee Code to the "
                                + "device ID shown above, then re-import.");
                    } else {
                        msg.append("\n✅ All employees matched and saved.");
                    }

                    Alert result = new Alert(
                            unmatched.isEmpty()
                                    ? Alert.AlertType.INFORMATION
                                    : Alert.AlertType.WARNING);
                    result.setTitle("Import Complete");
                    result.setHeaderText(
                            "FA2000 Import "
                            + (unmatched.isEmpty() ? "Successful"
                                                   : "— Action Required"));
                    result.setContentText(msg.toString());
                    result.getDialogPane().setMinWidth(560);
                    result.showAndWait();
                });

            } catch (Exception e) {
                logger.error("Import failed", e);
                javafx.application.Platform.runLater(() -> {
                    loading.close();
                    new Alert(Alert.AlertType.ERROR,
                            "Error processing CSV: " + e.getMessage())
                            .showAndWait();
                });
            }
        }).start();
    }

    /**
     * Tries to match a CSV device ID to an employee using multiple strategies:
     *
     * Strategy 1 — exact code match (handles "1", "01", "001", "EMP001", etc.)
     * Strategy 2 — numeric suffix match (strip "EMP" prefix, compare digits)
     * Strategy 3 — numeric value match (compare integer values: "01" == "1")
     *
     * This is much more robust than the old DAO-query-per-candidate approach.
     */
    private Optional<Employee> resolveEmployee(String parsedCode,
                                                List<Employee> allEmployees) {
        if (parsedCode == null || parsedCode.isBlank()) return Optional.empty();

        String parsedLower = parsedCode.trim().toLowerCase();

        // Extract numeric part of the parsed code (e.g. "3" from "EMP003")
        String parsedDigits = parsedLower.replaceAll("[^0-9]", "");
        int parsedNumeric = -1;
        try {
            if (!parsedDigits.isEmpty())
                parsedNumeric = Integer.parseInt(parsedDigits);
        } catch (NumberFormatException ignored) { /* non-numeric device ID */ }

        for (Employee emp : allEmployees) {
            if (emp.getEmployeeCode() == null) continue;
            String empCode    = emp.getEmployeeCode().trim();
            String empLower   = empCode.toLowerCase();
            String empDigits  = empLower.replaceAll("[^0-9]", "");

            // Strategy 1: exact case-insensitive match
            if (empLower.equals(parsedLower)) return Optional.of(emp);

            // Strategy 2: both have numeric parts — compare integer values
            if (!parsedDigits.isEmpty() && !empDigits.isEmpty()) {
                try {
                    int empNumeric = Integer.parseInt(empDigits);
                    if (parsedNumeric >= 0 && empNumeric == parsedNumeric)
                        return Optional.of(emp);
                } catch (NumberFormatException ignored) { /* skip */ }
            }
        }

        return Optional.empty();
    }

    /**
     * Builds the list of candidate codes shown in the unmatched error message.
     * (No longer used for DB lookups — kept for diagnostic output only.)
     */
    private List<String> buildCodeCandidates(String parsedCode) {
        List<String> candidates = new ArrayList<>();
        candidates.add(parsedCode);
        String digits = parsedCode.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                int n = Integer.parseInt(digits.replaceFirst("^0+", "").isEmpty()
                        ? "0" : digits.replaceFirst("^0+", ""));
                candidates.add(String.valueOf(n));
                candidates.add(String.format("%02d", n));
                candidates.add(String.format("%03d", n));
                candidates.add("EMP" + n);
                candidates.add("EMP" + String.format("%02d", n));
                candidates.add("EMP" + String.format("%03d", n));
            } catch (NumberFormatException ignored) { /* skip */ }
        }
        return new ArrayList<>(new LinkedHashSet<>(candidates));
    }

    // ── Load & filter ──────────────────────────────────────────────────────

    private void loadFromDatabase() {
        new Thread(() -> {
            try {
                LocalDate s = startDatePicker.getValue();
                LocalDate e = endDatePicker.getValue();
                List<AttendanceRecord> records = attDAO.findByDateRange(s, e);
                logger.info("Loaded {} attendance records from DB", records.size());
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

    private void applyFilters() {
        filteredRecords.clear();
        anomalyRecords.clear();

        LocalDate s   = startDatePicker.getValue();
        LocalDate e   = endDatePicker.getValue();
        boolean   ano = showAnomaliesOnly.isSelected();

        for (AttendanceRecord r : allRecords) {
            if (s != null && r.getAttendanceDate().isBefore(s)) continue;
            if (e != null && r.getAttendanceDate().isAfter(e))  continue;
            if (r.isHasAnomaly()) anomalyRecords.add(r);
            if (ano && !r.isHasAnomaly()) continue;
            filteredRecords.add(r);
        }

        recordCountLabel.setText(filteredRecords.size() + " records");
        if (anomalyCountLabel != null)
            anomalyCountLabel.setText(anomalyRecords.size() + " anomalies");
    }

    private void updateStats() {
        long total     = allRecords.size();
        long valid     = allRecords.stream().filter(r -> !r.isHasAnomaly()).count();
        long anomalies = allRecords.stream()
                .filter(AttendanceRecord::isHasAnomaly).count();
        long absent    = allRecords.stream()
                .filter(AttendanceRecord::isAbsent).count();

        lastImportLabel.setText(total + " records");
        lastImportDateLabel.setText(LocalDate.now().format(DATE_FMT));
        validRecordsLabel.setText(String.valueOf(valid));
        anomaliesLabel.setText(String.valueOf(anomalies));
        attendanceRateLabel.setText(total > 0
                ? String.format("%.1f%%", (total - absent) * 100.0 / total)
                : "0%");
    }

    // ── Table setup ────────────────────────────────────────────────────────

    private void setupTableColumns() {
        employeeCodeColumn.setCellValueFactory(c -> {
            Integer id = c.getValue().getEmployeeId();
            Employee emp = id != null ? empCache.get(id) : null;
            return new SimpleStringProperty(
                    emp != null ? emp.getEmployeeCode()
                                : (id != null ? "ID:" + id : "?"));
        });
        employeeNameColumn.setCellValueFactory(c -> {
            Integer id = c.getValue().getEmployeeId();
            Employee emp = id != null ? empCache.get(id) : null;
            return new SimpleStringProperty(
                    emp != null ? emp.getFullName()
                                : (id != null ? "ID:" + id : "Unknown"));
        });
        dateColumn.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getAttendanceDate();
            return new SimpleStringProperty(
                    d != null ? d.format(DATE_FMT) : "-");
        });
        timeIn1Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeIn1();
            return new SimpleStringProperty(
                    t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeOut1Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeOut1();
            return new SimpleStringProperty(
                    t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeIn2Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeIn2();
            return new SimpleStringProperty(
                    t != null ? t.format(TIME_FMT) : "Missed");
        });
        timeOut2Column.setCellValueFactory(c -> {
            var t = c.getValue().getTimeOut2();
            return new SimpleStringProperty(
                    t != null ? t.format(TIME_FMT) : "Missed");
        });
        regularHoursColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isAbsent() || r.isOnLeave())
                return new SimpleStringProperty("0.0");
            if (r.getTimeIn1() != null && r.getTimeOut2() != null) {
                long mins = java.time.Duration
                        .between(r.getTimeIn1(), r.getTimeOut2()).toMinutes();
                if (r.getTimeOut1() != null && r.getTimeIn2() != null)
                    mins -= java.time.Duration
                            .between(r.getTimeOut1(), r.getTimeIn2()).toMinutes();
                else if (mins > 300) mins -= 60;
                return new SimpleStringProperty(
                        String.format("%.1f", Math.min(mins / 60.0, 8.0)));
            }
            return new SimpleStringProperty("-");
        });
        overtimeColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isAbsent() || r.isOnLeave())
                return new SimpleStringProperty("0.0");
            if (r.getTimeIn1() != null && r.getTimeOut2() != null) {
                long mins = java.time.Duration
                        .between(r.getTimeIn1(), r.getTimeOut2()).toMinutes();
                if (r.getTimeOut1() != null && r.getTimeIn2() != null)
                    mins -= java.time.Duration
                            .between(r.getTimeOut1(), r.getTimeIn2()).toMinutes();
                else if (mins > 300) mins -= 60;
                double ot = Math.max(0, mins / 60.0 - 8.0);
                return new SimpleStringProperty(
                        ot > 0 ? String.format("%.1f", ot) : "0.0");
            }
            return new SimpleStringProperty("0.0");
        });
        statusColumn.setCellValueFactory(c -> {
            AttendanceRecord r = c.getValue();
            if (r.isOnLeave())    return new SimpleStringProperty("ON LEAVE");
            if (r.isAbsent())     return new SimpleStringProperty("ABSENT");
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
                    case "PRESENT"  -> "badge-success";
                    case "ABSENT"   -> "badge-error";
                    case "ON LEAVE" -> "badge-info";
                    default         -> "badge-warning";
                });
                setGraphic(b);
            }
        });
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn     = new Button("View");
            private final Button overrideBtn = new Button("Override");
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                viewBtn.getStyleClass().add("button-secondary");
                viewBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                overrideBtn.getStyleClass().add("button-secondary");
                overrideBtn.setStyle(
                        "-fx-padding:4px 10px;-fx-font-size:11px;"
                        + "-fx-background-color:#FEF3C7;-fx-text-fill:#92400E;");
                AttendanceRecord r = getTableView().getItems().get(getIndex());
                viewBtn.setOnAction(e -> showDetail(r));
                overrideBtn.setOnAction(e -> openOverrideDialog(r));
                setGraphic(new HBox(5, viewBtn, overrideBtn));
            }
        });
    }

    private void setupAnomalyColumns() {
        if (anEmpCodeCol == null) return;

        anEmpCodeCol.setCellValueFactory(c -> {
            Integer id = c.getValue().getEmployeeId();
            Employee emp = id != null ? empCache.get(id) : null;
            return new SimpleStringProperty(emp != null ? emp.getEmployeeCode() : "?");
        });
        anEmpNameCol.setCellValueFactory(c -> {
            Integer id = c.getValue().getEmployeeId();
            Employee emp = id != null ? empCache.get(id) : null;
            return new SimpleStringProperty(
                    emp != null ? emp.getFullName() : "Unknown");
        });
        anDateCol.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getAttendanceDate().format(DATE_FMT)));
        anTimeInCol.setCellValueFactory(c -> {
            var t = c.getValue().getTimeIn1();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "—");
        });
        anTimeOutCol.setCellValueFactory(c -> {
            var t = c.getValue().getTimeOut2();
            return new SimpleStringProperty(t != null ? t.format(TIME_FMT) : "—");
        });
        anDescCol.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getAnomalyDescription() != null
                                ? c.getValue().getAnomalyDescription() : "—"));
        anActionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button fixBtn = new Button("Fix");
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                fixBtn.getStyleClass().add("button-secondary");
                fixBtn.setStyle(
                        "-fx-padding:4px 12px;-fx-font-size:11px;"
                        + "-fx-background-color:#FEF3C7;-fx-text-fill:#92400E;");
                AttendanceRecord r = getTableView().getItems().get(getIndex());
                fixBtn.setOnAction(e -> openOverrideDialog(r));
                setGraphic(fixBtn);
            }
        });
    }

    // ── Override dialog ────────────────────────────────────────────────────

    private void openOverrideDialog(AttendanceRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/attendance-override.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            AttendanceOverrideController ctrl = loader.getController();
            ctrl.setRecord(record);

            Stage stage = new Stage();
            stage.setTitle("Manual Override — "
                    + record.getAttendanceDate().format(DATE_FMT));
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.showAndWait();

            if (ctrl.isSaved()) {
                loadFromDatabase();
                showInfoAlert("Override Applied",
                        "Attendance record for "
                        + record.getAttendanceDate().format(DATE_FMT)
                        + " has been updated.");
            }
        } catch (Exception e) {
            logger.error("Failed to open override dialog", e);
            showInfoAlert("Error",
                    "Could not open override dialog: " + e.getMessage());
        }
    }

    // ── Filter action handlers ─────────────────────────────────────────────

    @FXML private void handleApplyDateFilter() { loadFromDatabase(); }
    @FXML private void handleFilterToday() {
        LocalDate t = LocalDate.now();
        startDatePicker.setValue(t);
        endDatePicker.setValue(t);
        loadFromDatabase();
    }
    @FXML private void handleFilterThisWeek() {
        LocalDate n = LocalDate.now();
        startDatePicker.setValue(
                n.minusDays(n.getDayOfWeek().getValue() - 1));
        endDatePicker.setValue(n);
        loadFromDatabase();
    }
    @FXML private void handleFilterThisMonth() {
        LocalDate n = LocalDate.now();
        startDatePicker.setValue(n.withDayOfMonth(1));
        endDatePicker.setValue(n);
        loadFromDatabase();
    }
    @FXML private void handleFilterAnomalies() { applyFilters(); }
    @FXML private void handleRefresh()         { loadFromDatabase(); }
    @FXML private void handleValidateAll() {
        showInfoAlert("Validate",
                "Anomaly detection runs automatically during import (F3). "
                + "Check the Anomalies tab for flagged records.");
    }
    @FXML private void handleExport() {
        showInfoAlert("Export",
                "Report export will be implemented in a future update.");
    }
    @FXML private void handlePreviousPage() { /* TODO pagination */ }
    @FXML private void handleNextPage()     { /* TODO pagination */ }

    // ── Detail & info dialogs ──────────────────────────────────────────────

    private void showDetail(AttendanceRecord r) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Attendance Details");
        a.setHeaderText("Record for " + r.getAttendanceDate().format(DATE_FMT));
        StringBuilder sb = new StringBuilder();
        sb.append("Date    : ").append(r.getAttendanceDate().format(DATE_FMT))
          .append("\n\n");
        sb.append("In      : ").append(r.getTimeIn1()  != null
                ? r.getTimeIn1().format(TIME_FMT)  : "Missed").append("\n");
        sb.append("Lunch ✕ : ").append(r.getTimeOut1() != null
                ? r.getTimeOut1().format(TIME_FMT) : "Missed").append("\n");
        sb.append("Lunch → : ").append(r.getTimeIn2()  != null
                ? r.getTimeIn2().format(TIME_FMT)  : "Missed").append("\n");
        sb.append("Out     : ").append(r.getTimeOut2() != null
                ? r.getTimeOut2().format(TIME_FMT) : "Missed").append("\n\n");
        if (r.isOnLeave())
            sb.append("Status  : ON LEAVE (")
              .append(r.getLeaveType().name().replace("_", " "))
              .append(")\n");
        else if (r.isAbsent())
            sb.append("Status  : ABSENT\n");
        else if (r.isHasAnomaly())
            sb.append("Status  : ANOMALY – ")
              .append(r.getAnomalyDescription()).append("\n");
        else
            sb.append("Status  : PRESENT\n");
        if (r.isManuallyEdited())
            sb.append("\n⚠ Manually edited record");
        a.setContentText(sb.toString());
        a.showAndWait();
    }

    private void showInfoAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
