package com.example.payroll_project.dao;

import com.example.payroll_project.model.PayrollRecord;
import com.example.payroll_project.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Payroll Record DAO (CR4, F12)
 *
 * FIX: create() now persists ALL 27 PayrollRecord fields so that
 * records loaded back from the DB match what was computed.
 * mapSafe() likewise reads every column that create() writes.
 * ensureSchema() adds any column that might be absent in older DBs.
 */
public class PayrollDAO implements BaseDAO<PayrollRecord, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(PayrollDAO.class);
    private final DatabaseManager db = DatabaseManager.getInstance();

    public PayrollDAO() {
        ensureSchema();
    }

    // ── Schema migration ──────────────────────────────────────────────────

    private void ensureSchema() {
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS payroll_records (
                    payroll_id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    pay_period_id               INTEGER NOT NULL,
                    employee_id                 INTEGER NOT NULL,
                    total_regular_hours         DECIMAL(6,2)  DEFAULT 0,
                    total_overtime_hours        DECIMAL(6,2)  DEFAULT 0,
                    total_night_diff_hours      DECIMAL(6,2)  DEFAULT 0,
                    total_holiday_hours         DECIMAL(6,2)  DEFAULT 0,
                    total_rest_day_hours        DECIMAL(6,2)  DEFAULT 0,
                    days_worked                 INTEGER       DEFAULT 0,
                    days_absent                 INTEGER       DEFAULT 0,
                    total_late_minutes          INTEGER       DEFAULT 0,
                    total_undertime_minutes     INTEGER       DEFAULT 0,
                    basic_pay                   DECIMAL(12,2) DEFAULT 0,
                    overtime_pay                DECIMAL(12,2) DEFAULT 0,
                    night_diff_pay              DECIMAL(12,2) DEFAULT 0,
                    holiday_pay                 DECIMAL(12,2) DEFAULT 0,
                    total_allowances            DECIMAL(12,2) DEFAULT 0,
                    taxable_allowances          DECIMAL(12,2) DEFAULT 0,
                    non_taxable_allowances      DECIMAL(12,2) DEFAULT 0,
                    gross_pay                   DECIMAL(12,2) DEFAULT 0,
                    sss_contribution            DECIMAL(10,2) DEFAULT 0,
                    philhealth_contribution     DECIMAL(10,2) DEFAULT 0,
                    pagibig_contribution        DECIMAL(10,2) DEFAULT 0,
                    withholding_tax             DECIMAL(10,2) DEFAULT 0,
                    total_other_deductions      DECIMAL(12,2) DEFAULT 0,
                    total_deductions            DECIMAL(12,2) DEFAULT 0,
                    net_pay                     DECIMAL(12,2) DEFAULT 0,
                    computation_details         TEXT,
                    is_finalized                BOOLEAN       DEFAULT 0,
                    payslip_generated           BOOLEAN       DEFAULT 0,
                    payslip_path                TEXT,
                    created_by                  INTEGER,
                    created_at                  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                    updated_by                  INTEGER,
                    updated_at                  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(pay_period_id, employee_id)
                )
            """);

            // Add any column absent in older databases
            ResultSet cols = s.executeQuery("PRAGMA table_info(payroll_records)");
            java.util.Set<String> existing = new java.util.HashSet<>();
            while (cols.next()) existing.add(cols.getString("name").toLowerCase());
            cols.close();

            logger.info("payroll_records columns in DB: {}", existing);

            String[][] needed = {
                {"total_regular_hours",     "DECIMAL(6,2)  DEFAULT 0"},
                {"total_overtime_hours",    "DECIMAL(6,2)  DEFAULT 0"},
                {"total_night_diff_hours",  "DECIMAL(6,2)  DEFAULT 0"},
                {"total_holiday_hours",     "DECIMAL(6,2)  DEFAULT 0"},
                {"total_rest_day_hours",    "DECIMAL(6,2)  DEFAULT 0"},
                {"days_worked",             "INTEGER       DEFAULT 0"},
                {"days_absent",             "INTEGER       DEFAULT 0"},
                {"total_late_minutes",      "INTEGER       DEFAULT 0"},
                {"total_undertime_minutes", "INTEGER       DEFAULT 0"},
                {"basic_pay",               "DECIMAL(12,2) DEFAULT 0"},
                {"overtime_pay",            "DECIMAL(12,2) DEFAULT 0"},
                {"night_diff_pay",          "DECIMAL(12,2) DEFAULT 0"},
                {"holiday_pay",             "DECIMAL(12,2) DEFAULT 0"},
                {"total_allowances",        "DECIMAL(12,2) DEFAULT 0"},
                {"taxable_allowances",      "DECIMAL(12,2) DEFAULT 0"},
                {"non_taxable_allowances",  "DECIMAL(12,2) DEFAULT 0"},
                {"gross_pay",               "DECIMAL(12,2) DEFAULT 0"},
                {"sss_contribution",        "DECIMAL(10,2) DEFAULT 0"},
                {"philhealth_contribution", "DECIMAL(10,2) DEFAULT 0"},
                {"pagibig_contribution",    "DECIMAL(10,2) DEFAULT 0"},
                {"withholding_tax",         "DECIMAL(10,2) DEFAULT 0"},
                {"total_other_deductions",  "DECIMAL(12,2) DEFAULT 0"},
                {"total_deductions",        "DECIMAL(12,2) DEFAULT 0"},
                {"net_pay",                 "DECIMAL(12,2) DEFAULT 0"},
                {"computation_details",     "TEXT"},
                {"is_finalized",            "BOOLEAN       DEFAULT 0"},
                {"payslip_generated",       "BOOLEAN       DEFAULT 0"},
                {"payslip_path",            "TEXT"},
                {"updated_at",              "TIMESTAMP     DEFAULT CURRENT_TIMESTAMP"},
            };
            for (String[] col : needed) {
                if (!existing.contains(col[0].toLowerCase())) {
                    s.execute("ALTER TABLE payroll_records ADD COLUMN " + col[0] + " " + col[1]);
                    logger.info("Migration: added column '{}' to payroll_records", col[0]);
                }
            }

        } catch (Exception e) {
            logger.error("PayrollDAO schema migration failed: {}", e.getMessage(), e);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Override
    public PayrollRecord create(PayrollRecord r) throws SQLException {
        // All 27 business columns inserted explicitly so no field silently defaults to 0.
        String sql = """
            INSERT INTO payroll_records (
                pay_period_id, employee_id,
                total_regular_hours, total_overtime_hours, total_night_diff_hours,
                total_holiday_hours, total_rest_day_hours,
                days_worked, days_absent,
                total_late_minutes, total_undertime_minutes,
                basic_pay, overtime_pay, night_diff_pay, holiday_pay,
                total_allowances, taxable_allowances, non_taxable_allowances,
                gross_pay,
                sss_contribution, philhealth_contribution, pagibig_contribution,
                withholding_tax, total_other_deductions, total_deductions, net_pay,
                computation_details,
                is_finalized, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1,  r.getPayPeriodId());
            ps.setInt(2,  r.getEmployeeId());
            setBD(ps, 3,  r.getTotalRegularHours());
            setBD(ps, 4,  r.getTotalOvertimeHours());
            setBD(ps, 5,  r.getTotalNightDiffHours());
            setBD(ps, 6,  r.getTotalHolidayHours());
            setBD(ps, 7,  r.getTotalRestDayHours());
            ps.setInt(8,  r.getDaysWorked());
            ps.setInt(9,  r.getDaysAbsent());
            ps.setInt(10, r.getTotalLateMinutes()      != null ? r.getTotalLateMinutes()      : 0);
            ps.setInt(11, r.getTotalUndertimeMinutes() != null ? r.getTotalUndertimeMinutes() : 0);
            setBD(ps, 12, r.getBasicPay());
            setBD(ps, 13, r.getOvertimePay());
            setBD(ps, 14, r.getNightDiffPay());
            setBD(ps, 15, r.getHolidayPay());
            setBD(ps, 16, r.getTotalAllowances());
            setBD(ps, 17, r.getTaxableAllowances());
            setBD(ps, 18, r.getNonTaxableAllowances());
            setBD(ps, 19, r.getGrossPay());
            setBD(ps, 20, r.getSssContribution());
            setBD(ps, 21, r.getPhilhealthContribution());
            setBD(ps, 22, r.getPagibigContribution());
            setBD(ps, 23, r.getWithholdingTax());
            setBD(ps, 24, r.getTotalOtherDeductions());
            setBD(ps, 25, r.getTotalDeductions());
            setBD(ps, 26, r.getNetPay());
            ps.setString(27, r.getComputationDetails());
            ps.setBoolean(28, r.isFinalized());
            ps.setTimestamp(29, Timestamp.valueOf(
                    r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) r.setPayrollId(k.getInt(1));
            }
            logger.info("Created payroll record id={} for employee_id={} period_id={}",
                    r.getPayrollId(), r.getEmployeeId(), r.getPayPeriodId());
            return r;
        }
    }

    @Override
    public Optional<PayrollRecord> findById(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM payroll_records WHERE payroll_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapFull(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PayrollRecord> findAll() throws SQLException {
        List<PayrollRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM payroll_records")) {
            while (rs.next()) {
                try { list.add(mapFull(rs)); }
                catch (Exception ex) {
                    logger.error("Skipping malformed payroll row: {}", ex.getMessage());
                }
            }
        }
        return list;
    }

    public List<PayrollRecord> findByPayPeriod(int payPeriodId) throws SQLException {
        List<PayrollRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM payroll_records WHERE pay_period_id=?")) {
            ps.setInt(1, payPeriodId);
            try (ResultSet rs = ps.executeQuery()) {
                // Snapshot the columns actually present in this result set
                ResultSetMetaData meta = rs.getMetaData();
                java.util.Set<String> cols = new java.util.HashSet<>();
                for (int i = 1; i <= meta.getColumnCount(); i++)
                    cols.add(meta.getColumnName(i).toLowerCase());
                logger.info("findByPayPeriod({}) — ResultSet columns: {}", payPeriodId, cols);

                while (rs.next()) {
                    try {
                        list.add(mapSafe(rs, cols));
                    } catch (Exception ex) {
                        logger.error("Skipping row employee_id={}: {}",
                                safeGetInt(rs, "employee_id"), ex.getMessage(), ex);
                    }
                }
            }
        }
        logger.info("findByPayPeriod({}) → {} record(s)", payPeriodId, list.size());
        return list;
    }

    public Optional<PayrollRecord> findByPeriodAndEmployee(
            int payPeriodId, int employeeId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM payroll_records WHERE pay_period_id=? AND employee_id=?")) {
            ps.setInt(1, payPeriodId);
            ps.setInt(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapFull(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean update(PayrollRecord r) throws SQLException {
        String sql = """
            UPDATE payroll_records SET
                total_regular_hours=?,    total_overtime_hours=?,
                total_night_diff_hours=?, total_holiday_hours=?,   total_rest_day_hours=?,
                days_worked=?,            days_absent=?,
                total_late_minutes=?,     total_undertime_minutes=?,
                basic_pay=?,              overtime_pay=?,           night_diff_pay=?,
                holiday_pay=?,
                total_allowances=?,       taxable_allowances=?,     non_taxable_allowances=?,
                gross_pay=?,
                sss_contribution=?,       philhealth_contribution=?,
                pagibig_contribution=?,   withholding_tax=?,
                total_other_deductions=?, total_deductions=?,       net_pay=?,
                computation_details=?,
                is_finalized=?,           updated_at=?
            WHERE payroll_id=?
        """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            setBD(ps, 1,  r.getTotalRegularHours());
            setBD(ps, 2,  r.getTotalOvertimeHours());
            setBD(ps, 3,  r.getTotalNightDiffHours());
            setBD(ps, 4,  r.getTotalHolidayHours());
            setBD(ps, 5,  r.getTotalRestDayHours());
            ps.setInt(6,  r.getDaysWorked());
            ps.setInt(7,  r.getDaysAbsent());
            ps.setInt(8,  r.getTotalLateMinutes()      != null ? r.getTotalLateMinutes()      : 0);
            ps.setInt(9,  r.getTotalUndertimeMinutes() != null ? r.getTotalUndertimeMinutes() : 0);
            setBD(ps, 10, r.getBasicPay());
            setBD(ps, 11, r.getOvertimePay());
            setBD(ps, 12, r.getNightDiffPay());
            setBD(ps, 13, r.getHolidayPay());
            setBD(ps, 14, r.getTotalAllowances());
            setBD(ps, 15, r.getTaxableAllowances());
            setBD(ps, 16, r.getNonTaxableAllowances());
            setBD(ps, 17, r.getGrossPay());
            setBD(ps, 18, r.getSssContribution());
            setBD(ps, 19, r.getPhilhealthContribution());
            setBD(ps, 20, r.getPagibigContribution());
            setBD(ps, 21, r.getWithholdingTax());
            setBD(ps, 22, r.getTotalOtherDeductions());
            setBD(ps, 23, r.getTotalDeductions());
            setBD(ps, 24, r.getNetPay());
            ps.setString(25, r.getComputationDetails());
            ps.setBoolean(26, r.isFinalized());
            ps.setTimestamp(27, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(28, r.getPayrollId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM payroll_records WHERE payroll_id=? AND is_finalized=0")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean exists(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM payroll_records WHERE payroll_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM payroll_records")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ── Row mapping ───────────────────────────────────────────────────────

    /** Full map — every column guaranteed present (used for single-row lookups). */
    private PayrollRecord mapFull(ResultSet rs) throws SQLException {
        PayrollRecord r = new PayrollRecord();
        r.setPayrollId  (rs.getInt("payroll_id"));
        r.setPayPeriodId(rs.getInt("pay_period_id"));
        r.setEmployeeId (rs.getInt("employee_id"));
        r.setTotalRegularHours    (getBD(rs, "total_regular_hours"));
        r.setTotalOvertimeHours   (getBD(rs, "total_overtime_hours"));
        r.setTotalNightDiffHours  (getBD(rs, "total_night_diff_hours"));
        r.setTotalHolidayHours    (getBD(rs, "total_holiday_hours"));
        r.setTotalRestDayHours    (getBD(rs, "total_rest_day_hours"));
        r.setDaysWorked           (rs.getInt("days_worked"));
        r.setDaysAbsent           (rs.getInt("days_absent"));
        r.setTotalLateMinutes     (rs.getInt("total_late_minutes"));
        r.setTotalUndertimeMinutes(rs.getInt("total_undertime_minutes"));
        r.setBasicPay             (getBD(rs, "basic_pay"));
        r.setOvertimePay          (getBD(rs, "overtime_pay"));
        r.setNightDiffPay         (getBD(rs, "night_diff_pay"));
        r.setHolidayPay           (getBD(rs, "holiday_pay"));
        r.setTotalAllowances      (getBD(rs, "total_allowances"));
        r.setTaxableAllowances    (getBD(rs, "taxable_allowances"));
        r.setNonTaxableAllowances (getBD(rs, "non_taxable_allowances"));
        r.setGrossPay             (getBD(rs, "gross_pay"));
        r.setSssContribution      (getBD(rs, "sss_contribution"));
        r.setPhilhealthContribution(getBD(rs, "philhealth_contribution"));
        r.setPagibigContribution  (getBD(rs, "pagibig_contribution"));
        r.setWithholdingTax       (getBD(rs, "withholding_tax"));
        r.setTotalOtherDeductions (getBD(rs, "total_other_deductions"));
        r.setTotalDeductions      (getBD(rs, "total_deductions"));
        r.setNetPay               (getBD(rs, "net_pay"));
        r.setComputationDetails   (rs.getString("computation_details"));
        r.setFinalized            (rs.getBoolean("is_finalized"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) r.setCreatedAt(ca.toLocalDateTime());
        return r;
    }

    /**
     * Defensive map for findByPayPeriod() — falls back to ZERO for any column
     * absent from the ResultSet metadata (older DB schema).
     */
    private PayrollRecord mapSafe(ResultSet rs,
                                   java.util.Set<String> cols) throws SQLException {
        PayrollRecord r = new PayrollRecord();
        r.setPayrollId  (rs.getInt("payroll_id"));
        r.setPayPeriodId(rs.getInt("pay_period_id"));
        r.setEmployeeId (rs.getInt("employee_id"));

        r.setTotalRegularHours    (cols.contains("total_regular_hours")    ? getBD(rs,"total_regular_hours")    : BigDecimal.ZERO);
        r.setTotalOvertimeHours   (cols.contains("total_overtime_hours")   ? getBD(rs,"total_overtime_hours")   : BigDecimal.ZERO);
        r.setTotalNightDiffHours  (cols.contains("total_night_diff_hours") ? getBD(rs,"total_night_diff_hours") : BigDecimal.ZERO);
        r.setTotalHolidayHours    (cols.contains("total_holiday_hours")    ? getBD(rs,"total_holiday_hours")    : BigDecimal.ZERO);
        r.setTotalRestDayHours    (cols.contains("total_rest_day_hours")   ? getBD(rs,"total_rest_day_hours")   : BigDecimal.ZERO);
        r.setDaysWorked           (cols.contains("days_worked")            ? rs.getInt("days_worked")            : 0);
        r.setDaysAbsent           (cols.contains("days_absent")            ? rs.getInt("days_absent")            : 0);
        r.setTotalLateMinutes     (cols.contains("total_late_minutes")     ? rs.getInt("total_late_minutes")     : 0);
        r.setTotalUndertimeMinutes(cols.contains("total_undertime_minutes")? rs.getInt("total_undertime_minutes"): 0);
        r.setBasicPay             (cols.contains("basic_pay")              ? getBD(rs,"basic_pay")              : BigDecimal.ZERO);
        r.setOvertimePay          (cols.contains("overtime_pay")           ? getBD(rs,"overtime_pay")           : BigDecimal.ZERO);
        r.setNightDiffPay         (cols.contains("night_diff_pay")         ? getBD(rs,"night_diff_pay")         : BigDecimal.ZERO);
        r.setHolidayPay           (cols.contains("holiday_pay")            ? getBD(rs,"holiday_pay")            : BigDecimal.ZERO);
        r.setTotalAllowances      (cols.contains("total_allowances")       ? getBD(rs,"total_allowances")       : BigDecimal.ZERO);
        r.setTaxableAllowances    (cols.contains("taxable_allowances")     ? getBD(rs,"taxable_allowances")     : BigDecimal.ZERO);
        r.setNonTaxableAllowances (cols.contains("non_taxable_allowances") ? getBD(rs,"non_taxable_allowances") : BigDecimal.ZERO);
        r.setGrossPay             (cols.contains("gross_pay")              ? getBD(rs,"gross_pay")              : BigDecimal.ZERO);
        r.setSssContribution      (cols.contains("sss_contribution")       ? getBD(rs,"sss_contribution")       : BigDecimal.ZERO);
        r.setPhilhealthContribution(cols.contains("philhealth_contribution")? getBD(rs,"philhealth_contribution"): BigDecimal.ZERO);
        r.setPagibigContribution  (cols.contains("pagibig_contribution")   ? getBD(rs,"pagibig_contribution")   : BigDecimal.ZERO);
        r.setWithholdingTax       (cols.contains("withholding_tax")        ? getBD(rs,"withholding_tax")        : BigDecimal.ZERO);
        r.setTotalOtherDeductions (cols.contains("total_other_deductions") ? getBD(rs,"total_other_deductions") : BigDecimal.ZERO);
        r.setTotalDeductions      (cols.contains("total_deductions")       ? getBD(rs,"total_deductions")       : BigDecimal.ZERO);
        r.setNetPay               (cols.contains("net_pay")                ? getBD(rs,"net_pay")                : BigDecimal.ZERO);
        r.setComputationDetails   (cols.contains("computation_details")    ? rs.getString("computation_details") : null);
        r.setFinalized            (cols.contains("is_finalized")           && rs.getBoolean("is_finalized"));

        Timestamp ca = cols.contains("created_at") ? rs.getTimestamp("created_at") : null;
        if (ca != null) r.setCreatedAt(ca.toLocalDateTime());
        return r;
    }

    private BigDecimal getBD(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v != null ? v : BigDecimal.ZERO;
    }

    private void setBD(PreparedStatement ps, int idx, BigDecimal bd) throws SQLException {
        ps.setBigDecimal(idx, bd != null ? bd : BigDecimal.ZERO);
    }

    private int safeGetInt(ResultSet rs, String col) {
        try { return rs.getInt(col); } catch (Exception e) { return -1; }
    }
}
