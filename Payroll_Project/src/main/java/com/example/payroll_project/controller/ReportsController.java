package com.example.payroll_project.controller;

import com.example.payroll_project.dao.AttendanceDAO;
import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.dao.PayPeriodDAO;
import com.example.payroll_project.dao.PayrollDAO;
import com.example.payroll_project.model.*;
import com.example.payroll_project.service.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reports & Analytics Controller
 * Provides three report tabs:
 *   1. Payroll Summary   – per-employee gross / deductions / net for a pay period
 *   2. Statutory         – SSS / PhilHealth / Pag-IBIG / BIR contributions (employee + employer)
 *   3. Attendance        – per-employee attendance summary for a date range
 */
public class ReportsController {

    private static final Logger logger = LoggerFactory.getLogger(ReportsController.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ── Payroll Summary tab ─────────────────────────────────────────────────
    @FXML private ComboBox<PayPeriod> payrollPeriodCombo;
    @FXML private Label payrollEmpCount;
    @FXML private Label payrollTotalGross;
    @FXML private Label payrollTotalDeds;
    @FXML private Label payrollTotalNet;
    @FXML private TableView<PayrollRow> payrollTable;
    @FXML private TableColumn<PayrollRow, String> pcCode;
    @FXML private TableColumn<PayrollRow, String> pcName;
    @FXML private TableColumn<PayrollRow, String> pcDays;
    @FXML private TableColumn<PayrollRow, String> pcRegHrs;
    @FXML private TableColumn<PayrollRow, String> pcOtHrs;
    @FXML private TableColumn<PayrollRow, String> pcBasic;
    @FXML private TableColumn<PayrollRow, String> pcOtPay;
    @FXML private TableColumn<PayrollRow, String> pcNd;
    @FXML private TableColumn<PayrollRow, String> pcHol;
    @FXML private TableColumn<PayrollRow, String> pcGross;
    @FXML private TableColumn<PayrollRow, String> pcSSS;
    @FXML private TableColumn<PayrollRow, String> pcPH;
    @FXML private TableColumn<PayrollRow, String> pcPI;
    @FXML private TableColumn<PayrollRow, String> pcTax;
    @FXML private TableColumn<PayrollRow, String> pcDeds;
    @FXML private TableColumn<PayrollRow, String> pcNet;

    // ── Statutory tab ───────────────────────────────────────────────────────
    @FXML private ComboBox<PayPeriod> statPeriodCombo;
    @FXML private Label statTotalSSSEmp;
    @FXML private Label statTotalSSSEr;
    @FXML private Label statTotalPHEmp;
    @FXML private Label statTotalPIEmp;
    @FXML private Label statTotalTax;
    @FXML private TableView<StatutoryRow> statTable;
    @FXML private TableColumn<StatutoryRow, String> scCode;
    @FXML private TableColumn<StatutoryRow, String> scName;
    @FXML private TableColumn<StatutoryRow, String> scSalary;
    @FXML private TableColumn<StatutoryRow, String> scSSSEmp;
    @FXML private TableColumn<StatutoryRow, String> scSSSEr;
    @FXML private TableColumn<StatutoryRow, String> scPHEmp;
    @FXML private TableColumn<StatutoryRow, String> scPHEr;
    @FXML private TableColumn<StatutoryRow, String> scPIEmp;
    @FXML private TableColumn<StatutoryRow, String> scPIEr;
    @FXML private TableColumn<StatutoryRow, String> scTax;

    // ── Attendance tab ──────────────────────────────────────────────────────
    @FXML private DatePicker attFromDate;
    @FXML private DatePicker attToDate;
    @FXML private Label attPresentLbl;
    @FXML private Label attAbsentLbl;
    @FXML private Label attRateLbl;
    @FXML private Label attAnomalyLbl;
    @FXML private TableView<AttRow> attTable;
    @FXML private TableColumn<AttRow, String> acCode;
    @FXML private TableColumn<AttRow, String> acName;
    @FXML private TableColumn<AttRow, String> acDept;
    @FXML private TableColumn<AttRow, String> acWorked;
    @FXML private TableColumn<AttRow, String> acAbsent;
    @FXML private TableColumn<AttRow, String> acRegHrs;
    @FXML private TableColumn<AttRow, String> acOtHrs;
    @FXML private TableColumn<AttRow, String> acNdHrs;
    @FXML private TableColumn<AttRow, String> acLate;
    @FXML private TableColumn<AttRow, String> acUnder;
    @FXML private TableColumn<AttRow, String> acAnomalies;

    // ── DAOs & services ─────────────────────────────────────────────────────
    private final PayPeriodDAO  periodDAO   = new PayPeriodDAO();
    private final PayrollDAO    payrollDAO  = new PayrollDAO();
    private final EmployeeDAO   empDAO      = new EmployeeDAO();
    private final AttendanceDAO attDAO      = new AttendanceDAO();
    private final SSSCalculationService       sssService  = new SSSCalculationService();
    private final PhilHealthCalculationService phService  = new PhilHealthCalculationService();
    private final PagIBIGCalculationService    piService  = new PagIBIGCalculationService();

    // ── Observable data ─────────────────────────────────────────────────────
    private final ObservableList<PayrollRow>  payrollRows  = FXCollections.observableArrayList();
    private final ObservableList<StatutoryRow> statRows    = FXCollections.observableArrayList();
    private final ObservableList<AttRow>       attRows     = FXCollections.observableArrayList();

    // ── Init ────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupPayrollTable();
        setupStatutoryTable();
        setupAttendanceTable();
        loadPayPeriods();

        attToDate.setValue(LocalDate.now());
        attFromDate.setValue(LocalDate.now().withDayOfMonth(1));
    }

    // ── Pay Period loading ──────────────────────────────────────────────────

    private void loadPayPeriods() {
        new Thread(() -> {
            try {
                List<PayPeriod> periods = periodDAO.findAll();
                Platform.runLater(() -> {
                    payrollPeriodCombo.getItems().setAll(periods);
                    statPeriodCombo.getItems().setAll(periods);
                    if (!periods.isEmpty()) {
                        payrollPeriodCombo.setValue(periods.get(0));
                        statPeriodCombo.setValue(periods.get(0));
                    }
                });
            } catch (SQLException e) {
                logger.error("Failed to load pay periods", e);
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TAB 1 – PAYROLL SUMMARY
    // ════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleGeneratePayrollReport() {
        PayPeriod pp = payrollPeriodCombo.getValue();
        if (pp == null) { alert("Select a pay period first."); return; }

        new Thread(() -> {
            try {
                List<PayrollRecord> records = payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                Map<Integer, Employee> empMap = buildEmpMap();

                List<PayrollRow> rows = new ArrayList<>();
                for (PayrollRecord pr : records) {
                    Employee e = empMap.get(pr.getEmployeeId());
                    rows.add(new PayrollRow(pr, e));
                }

                BigDecimal totGross = rows.stream().map(r -> r.pr.getGrossPay()).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totDeds  = rows.stream().map(r -> r.pr.getTotalDeductions()).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totNet   = rows.stream().map(r -> r.pr.getNetPay()).reduce(BigDecimal.ZERO, BigDecimal::add);
                int count = rows.size();

                Platform.runLater(() -> {
                    payrollRows.setAll(rows);
                    payrollEmpCount.setText(String.valueOf(count));
                    payrollTotalGross.setText(CURRENCY.format(totGross));
                    payrollTotalDeds.setText(CURRENCY.format(totDeds));
                    payrollTotalNet.setText(CURRENCY.format(totNet));
                });
            } catch (Exception ex) {
                logger.error("Payroll report generation failed", ex);
                Platform.runLater(() -> alert("Error: " + ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleExportPayrollReport() {
        if (payrollRows.isEmpty()) { alert("Generate the report first."); return; }
        File file = chooseSaveFile("Payroll_Summary", "payroll_summary");
        if (file == null) return;

        new Thread(() -> {
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {

                PayPeriod pp = payrollPeriodCombo.getValue();
                pw.printf("PAYROLL SUMMARY REPORT%n");
                pw.printf("Pay Period : %s%n", pp != null ? pp.getPeriodName() : "");
                pw.printf("Period     : %s to %s%n",
                        pp != null ? pp.getStartDate().format(DATE_FMT) : "",
                        pp != null ? pp.getEndDate().format(DATE_FMT) : "");
                pw.println();
                pw.println("Code,Name,Days,Reg Hrs,OT Hrs,Basic Pay,OT Pay,Night Diff,Holiday Pay,"
                         + "Gross Pay,SSS,PhilHealth,Pag-IBIG,Tax,Total Deductions,Net Pay");

                for (PayrollRow r : payrollRows) {
                    pw.printf("%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csv(r.code), csv(r.name), r.pr.getDaysWorked(),
                            fmt2(r.pr.getTotalRegularHours()), fmt2(r.pr.getTotalOvertimeHours()),
                            money(r.pr.getBasicPay()), money(r.pr.getOvertimePay()),
                            money(r.pr.getNightDiffPay()), money(r.pr.getHolidayPay()),
                            money(r.pr.getGrossPay()),
                            money(r.pr.getSssContribution()), money(r.pr.getPhilhealthContribution()),
                            money(r.pr.getPagibigContribution()), money(r.pr.getWithholdingTax()),
                            money(r.pr.getTotalDeductions()), money(r.pr.getNetPay()));
                }

                BigDecimal totGross = payrollRows.stream().map(r -> r.pr.getGrossPay()).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totNet   = payrollRows.stream().map(r -> r.pr.getNetPay()).reduce(BigDecimal.ZERO, BigDecimal::add);
                pw.printf("%n,,,,,,,,, TOTAL GROSS: %s,,,,,TOTAL NET: %s%n",
                        money(totGross), money(totNet));

                Platform.runLater(() -> infoAlert("Exported", "Saved to:\n" + file.getAbsolutePath()));
            } catch (IOException ex) {
                logger.error("Export failed", ex);
                Platform.runLater(() -> alert("Export failed: " + ex.getMessage()));
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TAB 2 – STATUTORY COMPLIANCE
    // ════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleGenerateStatutoryReport() {
        PayPeriod pp = statPeriodCombo.getValue();
        if (pp == null) { alert("Select a pay period first."); return; }

        new Thread(() -> {
            try {
                List<PayrollRecord>  records = payrollDAO.findByPayPeriod(pp.getPayPeriodId());
                Map<Integer, Employee> empMap = buildEmpMap();

                List<StatutoryRow> rows = new ArrayList<>();
                for (PayrollRecord pr : records) {
                    Employee e = empMap.get(pr.getEmployeeId());
                    BigDecimal mSalary = monthlySalary(e);

                    BigDecimal sssEmp = pr.getSssContribution();
                    BigDecimal sssEr  = e != null ? sssService.calculateEmployerContribution(mSalary) : BigDecimal.ZERO;
                    BigDecimal phEmp  = pr.getPhilhealthContribution();
                    BigDecimal phEr   = e != null ? phService.calculateEmployerContribution(mSalary) : BigDecimal.ZERO;
                    BigDecimal piEmp  = pr.getPagibigContribution();
                    BigDecimal piEr   = e != null ? piService.calculateEmployerContribution(mSalary) : BigDecimal.ZERO;

                    rows.add(new StatutoryRow(pr, e, mSalary, sssEmp, sssEr, phEmp, phEr, piEmp, piEr));
                }

                BigDecimal tSSSEmp = rows.stream().map(r -> r.sssEmp).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal tSSSEr  = rows.stream().map(r -> r.sssEr).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal tPHEmp  = rows.stream().map(r -> r.phEmp).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal tPIEmp  = rows.stream().map(r -> r.piEmp).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal tTax    = rows.stream().map(r -> r.pr.getWithholdingTax()).reduce(BigDecimal.ZERO, BigDecimal::add);

                Platform.runLater(() -> {
                    statRows.setAll(rows);
                    statTotalSSSEmp.setText(CURRENCY.format(tSSSEmp));
                    statTotalSSSEr.setText(CURRENCY.format(tSSSEr));
                    statTotalPHEmp.setText(CURRENCY.format(tPHEmp));
                    statTotalPIEmp.setText(CURRENCY.format(tPIEmp));
                    statTotalTax.setText(CURRENCY.format(tTax));
                });
            } catch (Exception ex) {
                logger.error("Statutory report failed", ex);
                Platform.runLater(() -> alert("Error: " + ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleExportStatutoryReport() {
        if (statRows.isEmpty()) { alert("Generate the report first."); return; }
        File file = chooseSaveFile("Statutory_Report", "statutory_report");
        if (file == null) return;

        new Thread(() -> {
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {

                PayPeriod pp = statPeriodCombo.getValue();
                pw.printf("STATUTORY COMPLIANCE REPORT%n");
                pw.printf("Pay Period : %s%n", pp != null ? pp.getPeriodName() : "");
                pw.println();
                pw.println("Code,Name,Monthly Salary,"
                         + "SSS Employee,SSS Employer,SSS Total,"
                         + "PhilHealth Employee,PhilHealth Employer,PhilHealth Total,"
                         + "Pag-IBIG Employee,Pag-IBIG Employer,Pag-IBIG Total,"
                         + "Withholding Tax");

                for (StatutoryRow r : statRows) {
                    pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csv(r.code), csv(r.name), money(r.mSalary),
                            money(r.sssEmp), money(r.sssEr), money(r.sssEmp.add(r.sssEr)),
                            money(r.phEmp), money(r.phEr), money(r.phEmp.add(r.phEr)),
                            money(r.piEmp), money(r.piEr), money(r.piEmp.add(r.piEr)),
                            money(r.pr.getWithholdingTax()));
                }

                Platform.runLater(() -> infoAlert("Exported", "Saved to:\n" + file.getAbsolutePath()));
            } catch (IOException ex) {
                Platform.runLater(() -> alert("Export failed: " + ex.getMessage()));
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TAB 3 – ATTENDANCE ANALYTICS
    // ════════════════════════════════════════════════════════════════════════

    @FXML
    private void handleGenerateAttendanceReport() {
        LocalDate from = attFromDate.getValue();
        LocalDate to   = attToDate.getValue();
        if (from == null || to == null) { alert("Select a date range."); return; }
        if (to.isBefore(from)) { alert("End date must be after start date."); return; }

        new Thread(() -> {
            try {
                List<AttendanceRecord> records = attDAO.findByDateRange(from, to);
                Map<Integer, Employee> empMap  = buildEmpMap();

                // Group by employee
                Map<Integer, List<AttendanceRecord>> byEmp = records.stream()
                        .collect(Collectors.groupingBy(AttendanceRecord::getEmployeeId));

                List<AttRow> rows = new ArrayList<>();
                for (Map.Entry<Integer, List<AttendanceRecord>> entry : byEmp.entrySet()) {
                    Employee e = empMap.get(entry.getKey());
                    rows.add(new AttRow(entry.getKey(), e, entry.getValue()));
                }
                rows.sort(Comparator.comparing(r -> r.name));

                long totalPresent  = rows.stream().mapToLong(r -> r.daysWorked).sum();
                long totalAbsent   = rows.stream().mapToLong(r -> r.daysAbsent).sum();
                long totalAnomalies= rows.stream().mapToLong(r -> r.anomalies).sum();
                long totalDays     = totalPresent + totalAbsent;
                String rate = totalDays > 0
                        ? String.format("%.1f%%", totalPresent * 100.0 / totalDays)
                        : "N/A";

                Platform.runLater(() -> {
                    attRows.setAll(rows);
                    attPresentLbl.setText(String.valueOf(totalPresent));
                    attAbsentLbl.setText(String.valueOf(totalAbsent));
                    attRateLbl.setText(rate);
                    attAnomalyLbl.setText(String.valueOf(totalAnomalies));
                });
            } catch (Exception ex) {
                logger.error("Attendance report failed", ex);
                Platform.runLater(() -> alert("Error: " + ex.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleExportAttendanceReport() {
        if (attRows.isEmpty()) { alert("Generate the report first."); return; }
        File file = chooseSaveFile("Attendance_Report", "attendance_report");
        if (file == null) return;

        new Thread(() -> {
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8))) {

                pw.println("ATTENDANCE ANALYTICS REPORT");
                pw.printf("Period : %s to %s%n",
                        attFromDate.getValue().format(DATE_FMT),
                        attToDate.getValue().format(DATE_FMT));
                pw.println();
                pw.println("Code,Name,Department,Days Worked,Days Absent,"
                         + "Regular Hrs,OT Hrs,Night Diff Hrs,Late (min),Undertime (min),Anomalies");

                for (AttRow r : attRows) {
                    pw.printf("%s,%s,%s,%d,%d,%s,%s,%s,%d,%d,%d%n",
                            csv(r.code), csv(r.name), csv(r.dept),
                            r.daysWorked, r.daysAbsent,
                            fmt2(r.regHours), fmt2(r.otHours), fmt2(r.ndHours),
                            r.lateMinutes, r.undertimeMinutes, r.anomalies);
                }

                Platform.runLater(() -> infoAlert("Exported", "Saved to:\n" + file.getAbsolutePath()));
            } catch (IOException ex) {
                Platform.runLater(() -> alert("Export failed: " + ex.getMessage()));
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TABLE SETUP
    // ════════════════════════════════════════════════════════════════════════

    private void setupPayrollTable() {
        pcCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code));
        pcName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        pcDays.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().pr.getDaysWorked())));
        pcRegHrs.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().pr.getTotalRegularHours())));
        pcOtHrs.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().pr.getTotalOvertimeHours())));
        pcBasic.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getBasicPay())));
        pcOtPay.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getOvertimePay())));
        pcNd.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getNightDiffPay())));
        pcHol.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getHolidayPay())));
        pcGross.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getGrossPay())));
        pcSSS.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getSssContribution())));
        pcPH.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getPhilhealthContribution())));
        pcPI.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getPagibigContribution())));
        pcTax.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getWithholdingTax())));
        pcDeds.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getTotalDeductions())));
        pcNet.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getNetPay())));
        payrollTable.setItems(payrollRows);
    }

    private void setupStatutoryTable() {
        scCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code));
        scName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        scSalary.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().mSalary)));
        scSSSEmp.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().sssEmp)));
        scSSSEr.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().sssEr)));
        scPHEmp.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().phEmp)));
        scPHEr.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().phEr)));
        scPIEmp.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().piEmp)));
        scPIEr.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().piEr)));
        scTax.setCellValueFactory(c -> new SimpleStringProperty(money(c.getValue().pr.getWithholdingTax())));
        statTable.setItems(statRows);
    }

    private void setupAttendanceTable() {
        acCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code));
        acName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        acDept.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().dept));
        acWorked.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().daysWorked)));
        acAbsent.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().daysAbsent)));
        acRegHrs.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().regHours)));
        acOtHrs.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().otHours)));
        acNdHrs.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().ndHours)));
        acLate.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().lateMinutes)));
        acUnder.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().undertimeMinutes)));
        acAnomalies.setCellValueFactory(c -> {
            int a = c.getValue().anomalies;
            return new SimpleStringProperty(a > 0 ? "⚠ " + a : "0");
        });
        acAnomalies.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(item);
                setStyle(item.startsWith("⚠") ? "-fx-text-fill:#F59E0B; -fx-font-weight:600;" : "");
            }
        });
        attTable.setItems(attRows);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DATA MODELS (inner classes)
    // ════════════════════════════════════════════════════════════════════════

    static class PayrollRow {
        final PayrollRecord pr;
        final String code, name;

        PayrollRow(PayrollRecord pr, Employee e) {
            this.pr   = pr;
            this.code = e != null ? e.getEmployeeCode() : "ID:" + pr.getEmployeeId();
            this.name = e != null ? e.getFullName() : "Unknown";
        }
    }

    static class StatutoryRow {
        final PayrollRecord pr;
        final String code, name;
        final BigDecimal mSalary, sssEmp, sssEr, phEmp, phEr, piEmp, piEr;

        StatutoryRow(PayrollRecord pr, Employee e, BigDecimal mSalary,
                     BigDecimal sssEmp, BigDecimal sssEr,
                     BigDecimal phEmp, BigDecimal phEr,
                     BigDecimal piEmp, BigDecimal piEr) {
            this.pr      = pr;
            this.code    = e != null ? e.getEmployeeCode() : "ID:" + pr.getEmployeeId();
            this.name    = e != null ? e.getFullName() : "Unknown";
            this.mSalary = mSalary;
            this.sssEmp  = sssEmp; this.sssEr = sssEr;
            this.phEmp   = phEmp;  this.phEr  = phEr;
            this.piEmp   = piEmp;  this.piEr  = piEr;
        }
    }

    static class AttRow {
        final int empId;
        final String code, name, dept;
        int daysWorked, daysAbsent, lateMinutes, undertimeMinutes, anomalies;
        BigDecimal regHours = BigDecimal.ZERO;
        BigDecimal otHours  = BigDecimal.ZERO;
        BigDecimal ndHours  = BigDecimal.ZERO;

        AttRow(int empId, Employee e, List<AttendanceRecord> records) {
            this.empId = empId;
            this.code  = e != null ? e.getEmployeeCode() : "ID:" + empId;
            this.name  = e != null ? e.getFullName() : "Unknown";
            this.dept  = e != null && e.getDepartment() != null ? e.getDepartment() : "—";

            for (AttendanceRecord r : records) {
                if (r.isAbsent()) { daysAbsent++; continue; }
                if (r.getTimeIn1() == null) continue;
                daysWorked++;
                if (r.isHasAnomaly()) anomalies++;
                lateMinutes     += r.getLateMinutes()      != null ? r.getLateMinutes()      : 0;
                undertimeMinutes+= r.getUndertimeMinutes() != null ? r.getUndertimeMinutes() : 0;

                // Compute hours from raw times if stored hours are zero
                BigDecimal reg = r.getRegularHours();
                BigDecimal ot  = r.getOvertimeHours();
                BigDecimal nd  = r.getNightDiffHours();
                if ((reg == null || reg.compareTo(BigDecimal.ZERO) == 0)
                        && r.getTimeIn1() != null && r.getTimeOut2() != null) {
                    long mins = java.time.Duration.between(r.getTimeIn1(), r.getTimeOut2()).toMinutes();
                    if (r.getTimeOut1() != null && r.getTimeIn2() != null)
                        mins -= java.time.Duration.between(r.getTimeOut1(), r.getTimeIn2()).toMinutes();
                    else if (mins > 300) mins -= 60;
                    mins = Math.max(0, mins);
                    reg = BigDecimal.valueOf(Math.min(mins / 60.0, 8.0)).setScale(2, RoundingMode.HALF_UP);
                    ot  = BigDecimal.valueOf(Math.max(0, mins / 60.0 - 8.0)).setScale(2, RoundingMode.HALF_UP);
                }
                regHours = regHours.add(reg != null ? reg : BigDecimal.ZERO);
                otHours  = otHours.add(ot   != null ? ot  : BigDecimal.ZERO);
                ndHours  = ndHours.add(nd   != null ? nd  : BigDecimal.ZERO);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private Map<Integer, Employee> buildEmpMap() throws SQLException {
        Map<Integer, Employee> map = new HashMap<>();
        for (Employee e : empDAO.findAll(false)) map.put(e.getEmployeeId(), e);
        return map;
    }

    private BigDecimal monthlySalary(Employee e) {
        if (e == null || e.getBaseRate() == null) return BigDecimal.ZERO;
        return switch (e.getRateType()) {
            case MONTHLY -> e.getBaseRate();
            case DAILY   -> e.getBaseRate().multiply(new BigDecimal("26"));
            case HOURLY  -> e.getBaseRate().multiply(new BigDecimal("8")).multiply(new BigDecimal("26"));
        };
    }

    private File chooseSaveFile(String title, String defaultName) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save " + title);
        fc.setInitialFileName(defaultName + "_" + LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        return fc.showSaveDialog(payrollTable.getScene() != null
                ? payrollTable.getScene().getWindow()
                : attTable.getScene().getWindow());
    }

    private String money(BigDecimal bd) {
        return bd != null ? String.format("%.2f", bd) : "0.00";
    }

    private String fmt2(BigDecimal bd) {
        return bd != null ? bd.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void infoAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
