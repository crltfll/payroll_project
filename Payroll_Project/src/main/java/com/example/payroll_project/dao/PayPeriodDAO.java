package com.example.payroll_project.dao;

import com.example.payroll_project.model.PayPeriod;
import com.example.payroll_project.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pay Period DAO (F12)
 */
public class PayPeriodDAO implements BaseDAO<PayPeriod, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(PayPeriodDAO.class);
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public PayPeriod create(PayPeriod pp) throws SQLException {
        String sql = """
            INSERT INTO pay_periods
              (period_name, start_date, end_date, pay_date, status, is_locked, created_by, created_at)
            VALUES (?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pp.getPeriodName());
            ps.setDate  (2, Date.valueOf(pp.getStartDate()));
            ps.setDate  (3, Date.valueOf(pp.getEndDate()));
            ps.setObject(4, pp.getPayDate() != null ? Date.valueOf(pp.getPayDate()) : null);
            ps.setString(5, pp.getStatus().name());
            ps.setBoolean(6, pp.isLocked());
            ps.setObject(7, pp.getCreatedBy());
            ps.setTimestamp(8, Timestamp.valueOf(
                    pp.getCreatedAt() != null ? pp.getCreatedAt() : LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                if (k.next()) pp.setPayPeriodId(k.getInt(1));
            }
            return pp;
        }
    }

    @Override
    public Optional<PayPeriod> findById(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM pay_periods WHERE pay_period_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PayPeriod> findAll() throws SQLException {
        List<PayPeriod> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT * FROM pay_periods ORDER BY start_date DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    @Override
    public boolean update(PayPeriod pp) throws SQLException {
        String sql = """
            UPDATE pay_periods SET
              period_name=?, start_date=?, end_date=?, pay_date=?,
              status=?, is_locked=?
            WHERE pay_period_id=?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pp.getPeriodName());
            ps.setDate  (2, Date.valueOf(pp.getStartDate()));
            ps.setDate  (3, Date.valueOf(pp.getEndDate()));
            ps.setObject(4, pp.getPayDate() != null ? Date.valueOf(pp.getPayDate()) : null);
            ps.setString(5, pp.getStatus().name());
            ps.setBoolean(6, pp.isLocked());
            ps.setInt   (7, pp.getPayPeriodId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Cannot delete finalized pay periods (regulatory). */
    @Override
    public boolean delete(Integer id) throws SQLException {
        Optional<PayPeriod> pp = findById(id);
        if (pp.isPresent() && pp.get().isLocked()) {
            throw new SQLException("Cannot delete a finalized pay period.");
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM pay_periods WHERE pay_period_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean exists(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM pay_periods WHERE pay_period_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM pay_periods")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public List<PayPeriod> findByStatus(PayPeriod.Status status) throws SQLException {
        List<PayPeriod> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM pay_periods WHERE status=? ORDER BY start_date DESC")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    private PayPeriod map(ResultSet rs) throws SQLException {
        PayPeriod pp = new PayPeriod();
        pp.setPayPeriodId(rs.getInt("pay_period_id"));
        pp.setPeriodName (rs.getString("period_name"));
        pp.setStartDate  (rs.getDate("start_date").toLocalDate());
        pp.setEndDate    (rs.getDate("end_date").toLocalDate());
        Date pd = rs.getDate("pay_date");
        if (pd != null) pp.setPayDate(pd.toLocalDate());
        pp.setStatus(PayPeriod.Status.valueOf(rs.getString("status")));
        pp.setLocked(rs.getBoolean("is_locked"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) pp.setCreatedAt(ca.toLocalDateTime());
        pp.setCreatedBy((Integer) rs.getObject("created_by"));
        return pp;
    }
}
