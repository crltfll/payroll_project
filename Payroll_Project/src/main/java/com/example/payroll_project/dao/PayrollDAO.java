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
 */
public class PayrollDAO implements BaseDAO<PayrollRecord, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(PayrollDAO.class);
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public PayrollRecord create(PayrollRecord r) throws SQLException {
        String sql = """
            INSERT INTO payroll_records (
                pay_period_id, employee_id,
                total_regular_hours, total_overtime_hours, total_night_diff_hours,
                days_worked, days_absent,
                basic_pay, overtime_pay, night_diff_pay,
                gross_pay,
                sss_contribution, philhealth_contribution, pagibig_contribution,
                withholding_tax, total_deductions, net_pay,
                is_finalized, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1,  r.getPayPeriodId());
            ps.setInt(2,  r.getEmployeeId());
            setBD(ps, 3,  r.getTotalRegularHours());
            setBD(ps, 4,  r.getTotalOvertimeHours());
            setBD(ps, 5,  r.getTotalNightDiffHours());
            ps.setInt(6,  r.getDaysWorked());
            ps.setInt(7,  r.getDaysAbsent());
            setBD(ps, 8,  r.getBasicPay());
            setBD(ps, 9,  r.getOvertimePay());
            setBD(ps, 10, r.getNightDiffPay());
            setBD(ps, 11, r.getGrossPay());
            setBD(ps, 12, r.getSssContribution());
            setBD(ps, 13, r.getPhilhealthContribution());
            setBD(ps, 14, r.getPagibigContribution());
            setBD(ps, 15, r.getWithholdingTax());
            setBD(ps, 16, r.getTotalDeductions());
            setBD(ps, 17, r.getNetPay());
            ps.setBoolean(18, r.isFinalized());
            ps.setTimestamp(19, Timestamp.valueOf(
                    r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) r.setPayrollId(k.getInt(1));
            }
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
                if (rs.next()) return Optional.of(map(rs));
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
            while (rs.next()) list.add(map(rs));
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
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public Optional<PayrollRecord> findByPeriodAndEmployee(int payPeriodId, int employeeId)
            throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM payroll_records WHERE pay_period_id=? AND employee_id=?")) {
            ps.setInt(1, payPeriodId);
            ps.setInt(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean update(PayrollRecord r) throws SQLException {
        String sql = """
            UPDATE payroll_records SET
                total_regular_hours=?, total_overtime_hours=?, total_night_diff_hours=?,
                days_worked=?, days_absent=?,
                basic_pay=?, overtime_pay=?, night_diff_pay=?,
                gross_pay=?,
                sss_contribution=?, philhealth_contribution=?, pagibig_contribution=?,
                withholding_tax=?, total_deductions=?, net_pay=?,
                is_finalized=?, updated_at=?
            WHERE payroll_id=?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            setBD(ps, 1,  r.getTotalRegularHours());
            setBD(ps, 2,  r.getTotalOvertimeHours());
            setBD(ps, 3,  r.getTotalNightDiffHours());
            ps.setInt(4,  r.getDaysWorked());
            ps.setInt(5,  r.getDaysAbsent());
            setBD(ps, 6,  r.getBasicPay());
            setBD(ps, 7,  r.getOvertimePay());
            setBD(ps, 8,  r.getNightDiffPay());
            setBD(ps, 9,  r.getGrossPay());
            setBD(ps, 10, r.getSssContribution());
            setBD(ps, 11, r.getPhilhealthContribution());
            setBD(ps, 12, r.getPagibigContribution());
            setBD(ps, 13, r.getWithholdingTax());
            setBD(ps, 14, r.getTotalDeductions());
            setBD(ps, 15, r.getNetPay());
            ps.setBoolean(16, r.isFinalized());
            ps.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(18, r.getPayrollId());
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

    // -----------------------------------------------------------------------

    private PayrollRecord map(ResultSet rs) throws SQLException {
        PayrollRecord r = new PayrollRecord();
        r.setPayrollId  (rs.getInt("payroll_id"));
        r.setPayPeriodId(rs.getInt("pay_period_id"));
        r.setEmployeeId (rs.getInt("employee_id"));
        r.setTotalRegularHours   (rs.getBigDecimal("total_regular_hours"));
        r.setTotalOvertimeHours  (rs.getBigDecimal("total_overtime_hours"));
        r.setTotalNightDiffHours (rs.getBigDecimal("night_diff_hours") != null
                ? rs.getBigDecimal("night_diff_hours")
                : BigDecimal.ZERO);
        r.setDaysWorked (rs.getInt("days_worked"));
        r.setDaysAbsent (rs.getInt("days_absent"));
        r.setBasicPay           (rs.getBigDecimal("basic_pay"));
        r.setOvertimePay        (rs.getBigDecimal("overtime_pay"));
        r.setNightDiffPay       (rs.getBigDecimal("night_diff_pay"));
        r.setGrossPay           (rs.getBigDecimal("gross_pay"));
        r.setSssContribution    (rs.getBigDecimal("sss_contribution"));
        r.setPhilhealthContribution(rs.getBigDecimal("philhealth_contribution"));
        r.setPagibigContribution(rs.getBigDecimal("pagibig_contribution"));
        r.setWithholdingTax     (rs.getBigDecimal("withholding_tax"));
        r.setTotalDeductions    (rs.getBigDecimal("total_deductions"));
        r.setNetPay             (rs.getBigDecimal("net_pay"));
        r.setFinalized          (rs.getBoolean("is_finalized"));
        return r;
    }

    private void setBD(PreparedStatement ps, int idx, BigDecimal bd) throws SQLException {
        ps.setBigDecimal(idx, bd != null ? bd : BigDecimal.ZERO);
    }
}
