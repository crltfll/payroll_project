package com.example.payroll_project.controller;

import com.example.payroll_project.dao.AttendanceDAO;
import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.dao.PayPeriodDAO;
import com.example.payroll_project.dao.PayrollDAO;
import com.example.payroll_project.model.*;
import com.example.payroll_project.service.PayrollService;
import com.example.payroll_project.service.PayslipGeneratorService;
import com.example.payroll_project.util.DatabaseManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Payroll Controller (CR4, CR6, F1, F12)
 */
public class PayrollController {

    private static final Logger logger = LoggerFactory.getLogger(PayrollController.class);
    private static final NumberFormat CURRENCY =
            NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML private TextField periodNameField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private DatePicker payDatePicker;
    @FXML private ComboBox<String> periodStatusFilter;
    @FXML private TableView<PayPeriod> periodTable;
    @FXML private TableColumn<PayPeriod, String> colPeriodName;
    @FXML private TableColumn<PayPeriod, String> colPeriodStart;
    @FXML private TableColumn<PayPeriod, String> colPeriodEnd;
    @FXML private TableColumn<PayPeriod, String> colPeriodStatus;
    @FXML private TableColumn<PayPeriod, Void>   colPeriodActions;

    @FXML private Label selectedPeriodLabel;
    @FXML private Label totalEmployeesLabel;
    @FXML private Label totalGrossLabel;
    @FXML private Label totalNetLabel;
    @FXML private TableView<PayrollRecord> payrollTable;
    @FXML private TableColumn<PayrollRecord, String> colEmpCode;
    @FXML private TableColumn<PayrollRecord, String> colEmpName;
    @FXML private TableColumn<PayrollRecord, String> colDaysWorked;
    @FXML private TableColumn<PayrollRecord, String> colRegHours;
    @FXML private TableColumn<PayrollRecord, String> colOtHours;
    @FXML private TableColumn<PayrollRecord, String> colGrossPay;
    @FXML private TableColumn<PayrollRecord, String> colDeductions;
    @FXML private TableColumn<PayrollRecord, String> colNetPay;
    @FXML private TableColumn<PayrollRecord, Void>   colPayrollActions;

    @FXML private TextArea transparencyTextArea;

    private final PayPeriodDAO  periodDAO  = new PayPeriodDAO();
    private final PayrollDAO    payrollDAO = new PayrollDAO();
    private final EmployeeDAO   empDAO     = new EmployeeDAO();
    private final AttendanceDAO attDAO     = new AttendanceDAO();
    private final PayrollService          payrollSvc = new PayrollService();
    private final PayslipGeneratorService payslipSvc = new PayslipGeneratorService();

    private final ObservableList<PayPeriod>     periods = FXCollections.observableArrayList();
    private final ObservableList<PayrollRecord> records = FXCollections.observableArrayList();

    private PayPeriod selectedPeriod;
    private java.util.Map<Integer, Employee> empCache = new java.util.HashMap<>();

    // ── Init ──────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupPeriodTable();
        setupPayrollTable();

        periodStatusFilter.getItems().addAll("All", "DRAFT", "PROCESSING", "FINALIZED", "PAID");
        periodStatusFilter.setValue("All");

        LocalDate now = LocalDate.now();
        periodNameField.setText("Payroll " + now.getMonth().name() + " " + now.getYear());
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now);
        payDatePicker.setValue(now.plusDays(5));

        // Load cache first; pay periods + auto-select happen inside the callback
        // so the employee map is always ready before any records are rendered.
        loadEmployeeCache();
    }

    // ── Pay Period CRUD ───────────────────────────────────────────────────

    @FXML
    private void handleCreatePeriod() {
        String name  = periodNameField.getText().trim();
        LocalDate s  = startDatePicker.getValue();
        LocalDate e  = endDatePicker.getValue();
        LocalDate pd = payDatePicker.getValue();

        if (name.isEmpty() || s == null || e == null) {
            alert(Alert.AlertType.WARNING, "Validation",
                    "Period name, start and end dates are required.");
            return;
        }
        if (e.isBefore(s)) {
            alert(Alert.AlertType.WARNING, "Validation",
                    "End date must be after start date.");
            return;
        }

        new Thread(() -> {
            try {
                Optional<PayPeriod> existing = periodDAO.findByStartAndEnd(s, e);
                if (existing.isPresent()) {
                    PayPeriod dup = existing.get();
                    Platform.runLater(() -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Pay Period Already Exists");
                        confirm.setHeaderText("A pay period with this date range already exists.");
                        confirm.setContentText(
                                "Existing: \"" + dup.getPeriodName() + "\" ("
                                        + dup.getStatus().name() + ")\n\n"
                                        + "Would you like to select it instead?");
                        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                        confirm.showAndWait().ifPresent(resp -> {
                            if (resp == ButtonType.YES) selectPayPeriod(dup);
                        });
                    });
                    return;
                }

                PayPeriod pp = new PayPeriod(name, s, e);
                pp.setPayDate(pd);
                pp.setCreatedBy(LoginController.getCurrentUser() != null
                        ? LoginController.getCurrentUser().getUserId() : null);
                periodDAO.create(pp);
                Platform.runLater(() -> {
                    loadPayPeriods(false);
                    alert(Alert.AlertType.INFORMATION, "Success",
                            "Pay period created successfully.");
                });
            } catch (Exception ex) {
                logger.error("Create period failed", ex);
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleFilterPeriods() { loadPayPeriods(false); }

    private void loadPayPeriods(boolean autoSelectFirst) {
        new Thread(() -> {
            try {
                List<PayPeriod> all = periodDAO.findAll();
                String filter = periodStatusFilter.getValue();
                if (filter != null && !"All".equals(filter))
                    all.removeIf(p -> !p.getStatus().name().equals(filter));

                Platform.runLater(() -> {
                    periods.setAll(all);
                    periodTable.setItems(periods);

                    if (autoSelectFirst && selectedPeriod == null && !periods.isEmpty()) {
                        selectPayPeriod(periods.get(0));
                    } else if (selectedPeriod != null) {
                        periods.stream()
                                .filter(p -> p.getPayPeriodId()
                                        .equals(selectedPeriod.getPayPeriodId()))
                                .findFirst()
                                .ifPresent(this::selectPayPeriod);
                    }
                });
            } catch (SQLException ex) {
                logger.error("Load periods failed", ex);
            }
        }).start();
    }

    // ── Period table setup ────────────────────────────────────────────────

    private void setupPeriodTable() {
        colPeriodName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPeriodName()));
        colPeriodStart.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStartDate().format(DATE_FMT)));
        colPeriodEnd.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndDate().format(DATE_FMT)));
        colPeriodStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name()));

        colPeriodStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label(item);
                b.getStyleClass().add("badge");
                b.getStyleClass().add(switch (item) {
                    case "FINALIZED", "PAID" -> "badge-success";
                    case "PROCESSING"        -> "badge-info";
                    default                  -> "badge-warning";
                });
                setGraphic(b);
            }
        });

        colPeriodActions.setCellFactory(col -> new TableCell<>() {
            private final Button selectBtn   = new Button("Select");
            private final Button finalizeBtn = new Button("Finalize");
            private final Button deleteBtn   = new Button("Delete");

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }

                PayPeriod pp = getTableView().getItems().get(getIndex());

                selectBtn.getStyleClass().add("button-primary");
                selectBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");

                finalizeBtn.getStyleClass().add("button-secondary");
                finalizeBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                finalizeBtn.setDisable(pp.isLocked());

                deleteBtn.getStyleClass().add("button-danger");
                deleteBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                deleteBtn.setDisable(pp.isLocked());

                selectBtn.setOnAction(e   -> selectPayPeriod(pp));
                finalizeBtn.setOnAction(e -> finalizePayPeriod(pp));
                deleteBtn.setOnAction(e   -> handleDeletePeriod(pp));

                setGraphic(new javafx.scene.layout.HBox(4,
                        selectBtn, finalizeBtn, deleteBtn));
            }
        });
    }

    private void selectPayPeriod(PayPeriod pp) {
        selectedPeriod = pp;
        selectedPeriodLabel.setText(pp.getPeriodName() + "  ("
                + pp.getStartDate().format(DATE_FMT) + " – "
                + pp.getEndDate().format(DATE_FMT) + ")");
        loadPayrollRecords(pp);
    }

    private void finalizePayPeriod(PayPeriod pp) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Finalize pay period '" + pp.getPeriodName() + "'? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        pp.setStatus(PayPeriod.Status.FINALIZED);
                        pp.setLocked(true);
                        periodDAO.update(pp);
                        Platform.runLater(() -> {
                            loadPayPeriods(false);
                            alert(Alert.AlertType.INFORMATION, "Finalized",
                                    "Pay period has been finalized and locked.");
                        });
                    } catch (Exception ex) {
                        logger.error("Finalize failed", ex);
                        Platform.runLater(() ->
                                alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
                    }
                }).start();
            }
        });
    }

    private void handleDeletePeriod(PayPeriod pp) {
        if (pp.isLocked()) {
            alert(Alert.AlertType.WARNING, "Cannot Delete",
                    "Finalized or paid pay periods cannot be deleted.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Pay Period");
        confirm.setHeaderText("Delete \"" + pp.getPeriodName() + "\"?");
        confirm.setContentText(
                "This will permanently remove the pay period AND all associated\n"
                        + "payroll records. Attendance records are NOT affected.\n\n"
                        + "This action cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(resp -> {
            if (resp != ButtonType.OK) return;
            new Thread(() -> {
                try {
                    List<PayrollRecord> associated =
                            payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                    for (PayrollRecord pr : associated)
                        payrollDAO.delete(pr.getPayrollId());
                    periodDAO.delete(pp.getPayPeriodId());

                    Platform.runLater(() -> {
                        if (selectedPeriod != null &&
                                selectedPeriod.getPayPeriodId()
                                        .equals(pp.getPayPeriodId())) {
                            selectedPeriod = null;
                            selectedPeriodLabel.setText(
                                    "No period selected — click Select on a period above");
                            records.clear();
                            updateSummaryStats(records);
                        }
                        loadPayPeriods(false);
                        alert(Alert.AlertType.INFORMATION, "Deleted",
                                "Pay period \"" + pp.getPeriodName()
                                        + "\" has been deleted.");
                    });
                } catch (Exception ex) {
                    logger.error("Delete period failed", ex);
                    Platform.runLater(() ->
                            alert(Alert.AlertType.ERROR, "Delete Failed",
                                    ex.getMessage()));
                }
            }).start();
        });
    }

    // ── Payroll processing ────────────────────────────────────────────────

    @FXML
    private void handleProcessPayroll() {
        if (selectedPeriod == null) {
            alert(Alert.AlertType.WARNING, "No Period",
                    "Please select a pay period first.");
            return;
        }
        if (selectedPeriod.isLocked()) {
            alert(Alert.AlertType.WARNING, "Locked",
                    "This pay period is finalized and locked.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Process payroll for: " + selectedPeriod.getPeriodName() + "?\n"
                        + "This will compute salaries for all active employees.",
                ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        new Thread(() -> {
            try {
                selectedPeriod.setStatus(PayPeriod.Status.PROCESSING);
                periodDAO.update(selectedPeriod);

                List<Employee> employees = empDAO.findAll(true);
                List<PayrollRecord> computed = new ArrayList<>();

                for (Employee emp : employees) {
                    try {
                        List<AttendanceRecord> attendance =
                                attDAO.findByEmployeeAndPeriod(
                                        emp.getEmployeeId(),
                                        selectedPeriod.getStartDate(),
                                        selectedPeriod.getEndDate());

                        PayrollRecord pr = payrollSvc.compute(
                                emp, selectedPeriod, attendance);

                        Optional<PayrollRecord> existing =
                                payrollDAO.findByPeriodAndEmployee(
                                        selectedPeriod.getPayPeriodId(),
                                        emp.getEmployeeId());
                        if (existing.isPresent()) {
                            pr.setPayrollId(existing.get().getPayrollId());
                            payrollDAO.update(pr);
                        } else {
                            payrollDAO.create(pr);
                        }
                        computed.add(pr);
                    } catch (Exception ex) {
                        logger.error("Failed to process payroll for {}: {}",
                                emp.getEmployeeCode(), ex.getMessage());
                    }
                }

                // Refresh the employee cache so newly-added employees appear
                List<Employee> allEmps = empDAO.findAll(false);
                java.util.Map<Integer, Employee> freshMap = new java.util.HashMap<>();
                for (Employee e : allEmps) freshMap.put(e.getEmployeeId(), e);

                Platform.runLater(() -> {
                    empCache = freshMap;
                    records.setAll(computed);
                    payrollTable.setItems(records);
                    updateSummaryStats(computed);
                    loadPayPeriods(false);
                    alert(Alert.AlertType.INFORMATION, "Done",
                            "Payroll processed for "
                                    + computed.size() + " employee(s).");
                });
            } catch (Exception ex) {
                logger.error("Payroll processing failed", ex);
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleGeneratePayslips() {
        if (records.isEmpty()) {
            alert(Alert.AlertType.WARNING, "No Data", "Process payroll first.");
            return;
        }
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Directory for Payslips");
        File dir = dc.showDialog(payrollTable.getScene().getWindow());
        if (dir == null) return;

        new Thread(() -> {
            try {
                List<Employee> employees = empDAO.findAll(true);
                String outDir = dir.getAbsolutePath() + File.separator
                        + "payslips_" + selectedPeriod.getPeriodName()
                        .replaceAll("[^A-Za-z0-9_]", "_");
                List<String> generated = payslipSvc.generateBatch(
                        employees, selectedPeriod, new ArrayList<>(records), outDir);
                Platform.runLater(() ->
                        alert(Alert.AlertType.INFORMATION, "Payslips Generated",
                                generated.size() + " file(s) written to:\n" + outDir));
            } catch (Exception ex) {
                logger.error("Payslip generation failed", ex);
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleShowTransparency() {
        PayrollRecord selected =
                payrollTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            transparencyTextArea.setText(
                    "Select a payroll row to see the computation breakdown.");
            return;
        }
        new Thread(() -> {
            try {
                Optional<Employee> empOpt =
                        empDAO.findById(selected.getEmployeeId());
                if (empOpt.isEmpty()) return;
                Employee emp = empOpt.get();
                List<AttendanceRecord> att =
                        attDAO.findByEmployeeAndPeriod(emp.getEmployeeId(),
                                selectedPeriod.getStartDate(),
                                selectedPeriod.getEndDate());
                PayrollRecord fresh =
                        payrollSvc.compute(emp, selectedPeriod, att);
                String details = fresh.getComputationDetails() != null
                        ? fresh.getComputationDetails()
                        : "No details available.";
                Platform.runLater(() -> transparencyTextArea.setText(details));
            } catch (Exception ex) {
                Platform.runLater(() ->
                        transparencyTextArea.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    // ── Payroll records loading ───────────────────────────────────────────

    /**
     * Loads payroll records for the given period.
     * Also runs a raw COUNT query first so we can detect DAO-level mapping
     * failures (DB has rows but DAO returns empty list).
     */
    private void loadPayrollRecords(PayPeriod pp) {
        new Thread(() -> {
            try {
                // Raw count — tells us whether the DB actually has records
                int rawCount = 0;
                try (Connection conn =
                             DatabaseManager.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT COUNT(*) FROM payroll_records "
                                     + "WHERE pay_period_id = ?")) {
                    ps.setInt(1, pp.getPayPeriodId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) rawCount = rs.getInt(1);
                    }
                }
                logger.info("DB COUNT for pay_period_id={} → {} row(s)",
                        pp.getPayPeriodId(), rawCount);

                // Normal DAO load
                List<PayrollRecord> prs =
                        payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                logger.info("DAO returned {} record(s) for period '{}' (id={})",
                        prs.size(), pp.getPeriodName(), pp.getPayPeriodId());

                final int finalRawCount = rawCount;
                Platform.runLater(() -> {
                    records.setAll(prs);
                    payrollTable.setItems(records);
                    updateSummaryStats(prs);

                    // Warn if DB has rows but DAO returned nothing
                    // (indicates a mapping/SQL error in PayrollDAO)
                    if (finalRawCount > 0 && prs.isEmpty()) {
                        alert(Alert.AlertType.WARNING, "Data Mismatch",
                                "The database has " + finalRawCount
                                        + " payroll record(s) for this period "
                                        + "but none could be loaded.\n\n"
                                        + "Check the IDE console / log for a "
                                        + "SQL error in PayrollDAO.");
                    }
                });
            } catch (SQLException ex) {
                logger.error("loadPayrollRecords failed (period id={}): {}",
                        pp.getPayPeriodId(), ex.getMessage(), ex);
                Platform.runLater(() ->
                        alert(Alert.AlertType.ERROR, "Load Error",
                                "Failed to load payroll records:\n"
                                        + ex.getMessage()));
            }
        }).start();
    }

    // ── Payroll table setup ───────────────────────────────────────────────

    private void setupPayrollTable() {
        colEmpCode.setCellValueFactory(c -> {
            Employee e = empCache.get(c.getValue().getEmployeeId());
            return new SimpleStringProperty(
                    e != null ? e.getEmployeeCode()
                              : "ID:" + c.getValue().getEmployeeId());
        });
        colEmpName.setCellValueFactory(c -> {
            Employee e = empCache.get(c.getValue().getEmployeeId());
            return new SimpleStringProperty(
                    e != null ? e.getFullName()
                              : "Unknown (" + c.getValue().getEmployeeId() + ")");
        });
        colDaysWorked.setCellValueFactory(c ->
                new SimpleStringProperty(
                        String.valueOf(c.getValue().getDaysWorked())));
        colRegHours.setCellValueFactory(c ->
                new SimpleStringProperty(
                        fmt2(c.getValue().getTotalRegularHours())));
        colOtHours.setCellValueFactory(c ->
                new SimpleStringProperty(
                        fmt2(c.getValue().getTotalOvertimeHours())));
        colGrossPay.setCellValueFactory(c ->
                new SimpleStringProperty(
                        CURRENCY.format(c.getValue().getGrossPay())));
        colDeductions.setCellValueFactory(c ->
                new SimpleStringProperty(
                        CURRENCY.format(c.getValue().getTotalDeductions())));
        colNetPay.setCellValueFactory(c ->
                new SimpleStringProperty(
                        CURRENCY.format(c.getValue().getNetPay())));

        colPayrollActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("Details");
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                viewBtn.getStyleClass().add("button-secondary");
                viewBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                viewBtn.setOnAction(e -> {
                    payrollTable.getSelectionModel().select(getIndex());
                    handleShowTransparency();
                });
                setGraphic(viewBtn);
            }
        });
    }

    private void updateSummaryStats(List<PayrollRecord> prs) {
        int empCount = prs.size();
        BigDecimal gross = prs.stream().map(PayrollRecord::getGrossPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = prs.stream().map(PayrollRecord::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalEmployeesLabel.setText(String.valueOf(empCount));
        totalGrossLabel.setText(CURRENCY.format(gross));
        totalNetLabel.setText(CURRENCY.format(net));
    }

    // ── Employee cache ────────────────────────────────────────────────────

    /**
     * Loads the employee lookup cache on a background thread.
     * Pay periods are triggered from inside this callback so the cache is
     * always populated before any payroll row tries to resolve an employee name.
     */
    private void loadEmployeeCache() {
        new Thread(() -> {
            try {
                List<Employee> employees = empDAO.findAll(false);
                java.util.Map<Integer, Employee> map = new java.util.HashMap<>();
                for (Employee e : employees) map.put(e.getEmployeeId(), e);
                logger.info("Employee cache built: {} entries", map.size());

                Platform.runLater(() -> {
                    empCache = map;
                    loadPayPeriods(true);
                    if (selectedPeriod != null) {
                        loadPayrollRecords(selectedPeriod);
                    }
                });
            } catch (Exception ex) {
                logger.error("Employee cache load failed", ex);
                Platform.runLater(() -> loadPayPeriods(true));
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String fmt2(BigDecimal bd) {
        return bd != null ? String.format("%.2f", bd) : "0.00";
    }

    private void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
