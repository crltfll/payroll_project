package com.example.payroll_project.controller;

import com.example.payroll_project.dao.AttendanceDAO;
import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.dao.PayPeriodDAO;
import com.example.payroll_project.dao.PayrollDAO;
import com.example.payroll_project.model.*;
import com.example.payroll_project.service.PayrollService;
import com.example.payroll_project.service.PayslipGeneratorService;
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
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Payroll Controller (CR4, CR6, F1, F12)
 *
 * FIX 1: Auto-selects the most recently created pay period on startup so
 *         payroll records are visible without requiring a manual "Select" click.
 *
 * FIX 2: Added Delete button for non-locked (DRAFT / PROCESSING) pay periods.
 */
public class PayrollController {

    private static final Logger logger = LoggerFactory.getLogger(PayrollController.class);
    private static final NumberFormat CURRENCY =
            NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // ── Pay Period Section ────────────────────────────────────────────────
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

    // ── Payroll Processing Section ────────────────────────────────────────
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

    // ── Transparency Panel (F1) ───────────────────────────────────────────
    @FXML private TextArea transparencyTextArea;

    // DAOs & Services
    private final PayPeriodDAO   periodDAO   = new PayPeriodDAO();
    private final PayrollDAO     payrollDAO  = new PayrollDAO();
    private final EmployeeDAO    empDAO      = new EmployeeDAO();
    private final AttendanceDAO  attDAO      = new AttendanceDAO();
    private final PayrollService payrollSvc  = new PayrollService();
    private final PayslipGeneratorService payslipSvc = new PayslipGeneratorService();

    private final ObservableList<PayPeriod>     periods  = FXCollections.observableArrayList();
    private final ObservableList<PayrollRecord> records  = FXCollections.observableArrayList();

    private PayPeriod selectedPeriod;
    private java.util.Map<Integer, Employee> empCache = new java.util.HashMap<>();

    // -----------------------------------------------------------------------

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

        loadPayPeriods(true);   // true = auto-select most recent period
        loadEmployeeCache();
    }

    // -----------------------------------------------------------------------
    // Pay Period Management
    // -----------------------------------------------------------------------

    @FXML
    private void handleCreatePeriod() {
        String name  = periodNameField.getText().trim();
        LocalDate s  = startDatePicker.getValue();
        LocalDate e  = endDatePicker.getValue();
        LocalDate pd = payDatePicker.getValue();

        if (name.isEmpty() || s == null || e == null) {
            alert(Alert.AlertType.WARNING, "Validation", "Period name, start and end dates are required.");
            return;
        }
        if (e.isBefore(s)) {
            alert(Alert.AlertType.WARNING, "Validation", "End date must be after start date.");
            return;
        }

        new Thread(() -> {
            try {
                // Check for duplicate date range BEFORE inserting
                Optional<PayPeriod> existing = periodDAO.findByStartAndEnd(s, e);
                if (existing.isPresent()) {
                    PayPeriod dup = existing.get();
                    Platform.runLater(() -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Pay Period Already Exists");
                        confirm.setHeaderText("A pay period with this date range already exists.");
                        confirm.setContentText(
                                "Existing: \"" + dup.getPeriodName() + "\" (" + dup.getStatus().name() + ")\n\n"
                                        + "Would you like to select it instead of creating a duplicate?");
                        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                        confirm.showAndWait().ifPresent(resp -> {
                            if (resp == ButtonType.YES) {
                                selectPayPeriod(dup);
                            }
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
                    alert(Alert.AlertType.INFORMATION, "Success", "Pay period created successfully.");
                });
            } catch (Exception ex) {
                logger.error("Create period failed", ex);
                Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
            }
        }).start();
    }
    @FXML
    private void handleFilterPeriods() {
        loadPayPeriods(false);
    }

    /**
     * @param autoSelectFirst when true, automatically selects and loads the most
     *                        recently created period (so payroll records appear on
     *                        startup without a manual "Select" click).
     */
    private void loadPayPeriods(boolean autoSelectFirst) {
        new Thread(() -> {
            try {
                List<PayPeriod> all = periodDAO.findAll();
                String filter = periodStatusFilter.getValue();
                if (filter != null && !"All".equals(filter)) {
                    all.removeIf(p -> !p.getStatus().name().equals(filter));
                }
                Platform.runLater(() -> {
                    periods.setAll(all);
                    periodTable.setItems(periods);

                    // FIX: auto-select the most recently created period so that
                    // payroll records are visible immediately on app start.
                    if (autoSelectFirst && selectedPeriod == null && !periods.isEmpty()) {
                        selectPayPeriod(periods.get(0));
                    } else if (selectedPeriod != null) {
                        // Re-select the currently selected period after a list refresh.
                        periods.stream()
                                .filter(p -> p.getPayPeriodId().equals(selectedPeriod.getPayPeriodId()))
                                .findFirst()
                                .ifPresent(this::selectPayPeriod);
                    }
                });
            } catch (SQLException ex) {
                logger.error("Load periods failed", ex);
            }
        }).start();
    }

    private void setupPeriodTable() {
        colPeriodName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPeriodName()));
        colPeriodStart.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStartDate().format(DATE_FMT)));
        colPeriodEnd.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEndDate().format(DATE_FMT)));
        colPeriodStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name()));

        // Status badge
        colPeriodStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label(item);
                b.getStyleClass().add("badge");
                b.getStyleClass().add(switch (item) {
                    case "FINALIZED","PAID" -> "badge-success";
                    case "PROCESSING"       -> "badge-info";
                    case "DRAFT"            -> "badge-warning";
                    default                 -> "badge-warning";
                });
                setGraphic(b);
            }
        });

        // Actions — Select, Finalize, Delete
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

                // Delete is only allowed for non-locked (DRAFT / PROCESSING) periods
                deleteBtn.getStyleClass().add("button-danger");
                deleteBtn.setStyle("-fx-padding:4px 10px;-fx-font-size:11px;");
                deleteBtn.setDisable(pp.isLocked());

                selectBtn.setOnAction(e   -> selectPayPeriod(pp));
                finalizeBtn.setOnAction(e -> finalizePayPeriod(pp));
                deleteBtn.setOnAction(e   -> handleDeletePeriod(pp));

                setGraphic(new javafx.scene.layout.HBox(4, selectBtn, finalizeBtn, deleteBtn));
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
                        Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
                    }
                }).start();
            }
        });
    }

    /**
     * FIX: Delete a pay period (only allowed when it is NOT locked / finalized).
     * Also removes all associated payroll records to keep the DB consistent.
     */
    private void handleDeletePeriod(PayPeriod pp) {
        if (pp.isLocked()) {
            alert(Alert.AlertType.WARNING, "Cannot Delete",
                    "Finalized or paid pay periods cannot be deleted (regulatory requirement).");
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
                    // Delete payroll records first (FK constraint)
                    List<PayrollRecord> associated =
                            payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                    for (PayrollRecord pr : associated) {
                        payrollDAO.delete(pr.getPayrollId());
                    }
                    periodDAO.delete(pp.getPayPeriodId());

                    Platform.runLater(() -> {
                        // Clear selection if deleted period was selected
                        if (selectedPeriod != null &&
                                selectedPeriod.getPayPeriodId().equals(pp.getPayPeriodId())) {
                            selectedPeriod = null;
                            selectedPeriodLabel.setText("No period selected — click Select on a period above");
                            records.clear();
                            updateSummaryStats(records);
                        }
                        loadPayPeriods(false);
                        alert(Alert.AlertType.INFORMATION, "Deleted",
                                "Pay period \"" + pp.getPeriodName() + "\" has been deleted.");
                    });
                } catch (Exception ex) {
                    logger.error("Delete period failed", ex);
                    Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Delete Failed", ex.getMessage()));
                }
            }).start();
        });
    }

    // -----------------------------------------------------------------------
    // Payroll Processing
    // -----------------------------------------------------------------------

    @FXML
    private void handleProcessPayroll() {
        if (selectedPeriod == null) {
            alert(Alert.AlertType.WARNING, "No Period", "Please select a pay period first.");
            return;
        }
        if (selectedPeriod.isLocked()) {
            alert(Alert.AlertType.WARNING, "Locked", "This pay period is finalized and locked.");
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
                        List<com.example.payroll_project.model.AttendanceRecord> attendance =
                                attDAO.findByEmployeeAndPeriod(emp.getEmployeeId(),
                                        selectedPeriod.getStartDate(),
                                        selectedPeriod.getEndDate());

                        PayrollRecord pr = payrollSvc.compute(emp, selectedPeriod, attendance);

                        Optional<PayrollRecord> existing =
                                payrollDAO.findByPeriodAndEmployee(
                                        selectedPeriod.getPayPeriodId(), emp.getEmployeeId());
                        if (existing.isPresent()) {
                            pr.setPayrollId(existing.get().getPayrollId());
                            payrollDAO.update(pr);
                        } else {
                            payrollDAO.create(pr);
                        }
                        computed.add(pr);

                    } catch (Exception ex) {
                        logger.error("Failed to process payroll for employee {}: {}",
                                emp.getEmployeeCode(), ex.getMessage());
                    }
                }

                Platform.runLater(() -> {
                    records.setAll(computed);
                    payrollTable.setItems(records);
                    updateSummaryStats(computed);
                    loadPayPeriods(false);
                    alert(Alert.AlertType.INFORMATION, "Done",
                            "Payroll processed for " + computed.size() + " employee(s).");
                });
            } catch (Exception ex) {
                logger.error("Payroll processing failed", ex);
                Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
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

                Platform.runLater(() -> alert(Alert.AlertType.INFORMATION, "Payslips Generated",
                        generated.size() + " file(s) written to:\n" + outDir));

            } catch (Exception ex) {
                logger.error("Payslip generation failed", ex);
                Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Error", ex.getMessage()));
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Transparency (F1)
    // -----------------------------------------------------------------------

    @FXML
    private void handleShowTransparency() {
        PayrollRecord selected = payrollTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            transparencyTextArea.setText("Select a payroll row to see the computation breakdown.");
            return;
        }

        new Thread(() -> {
            try {
                Optional<Employee> empOpt = empDAO.findById(selected.getEmployeeId());
                if (empOpt.isEmpty()) return;

                Employee emp = empOpt.get();
                List<com.example.payroll_project.model.AttendanceRecord> att =
                        attDAO.findByEmployeeAndPeriod(emp.getEmployeeId(),
                                selectedPeriod.getStartDate(), selectedPeriod.getEndDate());

                PayrollRecord fresh = payrollSvc.compute(emp, selectedPeriod, att);
                String details = fresh.getComputationDetails() != null
                        ? fresh.getComputationDetails()
                        : "No details available.";

                Platform.runLater(() -> transparencyTextArea.setText(details));
            } catch (Exception ex) {
                Platform.runLater(() -> transparencyTextArea.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Payroll table setup
    // -----------------------------------------------------------------------

    private void loadPayrollRecords(PayPeriod pp) {
        new Thread(() -> {
            try {
                List<PayrollRecord> prs = payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                Platform.runLater(() -> {
                    records.setAll(prs);
                    payrollTable.setItems(records);
                    updateSummaryStats(prs);
                });
            } catch (SQLException ex) {
                logger.error("Load payroll records failed", ex);
            }
        }).start();
    }

    private void setupPayrollTable() {
        colEmpCode.setCellValueFactory(c -> {
            Employee e = empCache.get(c.getValue().getEmployeeId());
            return new SimpleStringProperty(e != null ? e.getEmployeeCode() : "—");
        });
        colEmpName.setCellValueFactory(c -> {
            Employee e = empCache.get(c.getValue().getEmployeeId());
            return new SimpleStringProperty(e != null ? e.getFullName() : "Unknown");
        });
        colDaysWorked.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getDaysWorked())));
        colRegHours.setCellValueFactory(c ->
                new SimpleStringProperty(fmt2(c.getValue().getTotalRegularHours())));
        colOtHours.setCellValueFactory(c ->
                new SimpleStringProperty(fmt2(c.getValue().getTotalOvertimeHours())));
        colGrossPay.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getGrossPay())));
        colDeductions.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getTotalDeductions())));
        colNetPay.setCellValueFactory(c ->
                new SimpleStringProperty(CURRENCY.format(c.getValue().getNetPay())));

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
        int empCount   = prs.size();
        BigDecimal gross = prs.stream().map(PayrollRecord::getGrossPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = prs.stream().map(PayrollRecord::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalEmployeesLabel.setText(String.valueOf(empCount));
        totalGrossLabel.setText(CURRENCY.format(gross));
        totalNetLabel.setText(CURRENCY.format(net));
    }

    private void loadEmployeeCache() {
        new Thread(() -> {
            try {
                List<Employee> employees = empDAO.findAll(false);
                java.util.Map<Integer, Employee> map = new java.util.HashMap<>();
                for (Employee e : employees) map.put(e.getEmployeeId(), e);
                Platform.runLater(() -> {
                    empCache = map;
                    payrollTable.refresh();
                });
            } catch (Exception ex) {
                logger.error("Cache load failed", ex);
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
