package com.example.payroll_project.dao;

import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Attendance Record DAO (CR1, F10)
 */
public class AttendanceDAO implements BaseDAO<AttendanceRecord, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceDAO.class);
    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public AttendanceRecord create(AttendanceRecord r) throws SQLException {
        String sql = """
            INSERT INTO attendance_records (
                employee_id, attendance_date,
                time_in_1, time_out_1, time_in_2, time_out_2,
                regular_hours, overtime_hours, night_diff_hours,
                late_minutes, undertime_minutes,
                is_absent, is_holiday, is_rest_day,
                has_anomaly, anomaly_description, is_manually_edited,
                import_batch_id, data_source,
                created_by, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt   (1, r.getEmployeeId());
            ps.setDate  (2, Date.valueOf(r.getAttendanceDate()));
            setTime(ps, 3, r.getTimeIn1());
            setTime(ps, 4, r.getTimeOut1());
            setTime(ps, 5, r.getTimeIn2());
            setTime(ps, 6, r.getTimeOut2());
            setBD  (ps, 7, r.getRegularHours());
            setBD  (ps, 8, r.getOvertimeHours());
            setBD  (ps, 9, r.getNightDiffHours());
            ps.setInt   (10, r.getLateMinutes());
            ps.setInt   (11, r.getUndertimeMinutes());
            ps.setBoolean(12, r.isAbsent());
            ps.setBoolean(13, r.isHoliday());
            ps.setBoolean(14, r.isRestDay());
            ps.setBoolean(15, r.isHasAnomaly());
            ps.setString(16, r.getAnomalyDescription());
            ps.setBoolean(17, r.isManuallyEdited());
            ps.setObject(18, r.getImportBatchId());
            ps.setString(19, r.getDataSource() != null ? r.getDataSource() : "FA2000_CSV");
            ps.setObject(20, r.getCreatedBy());
            ps.setTimestamp(21, Timestamp.valueOf(
                    r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setAttendanceId(keys.getInt(1));
            }
            return r;
        }
    }

    @Override
    public Optional<AttendanceRecord> findById(Integer id) throws SQLException {
        String sql = "SELECT * FROM attendance_records WHERE attendance_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<AttendanceRecord> findAll() throws SQLException {
        return findByDateRange(null, null);
    }

    @Override
    public boolean update(AttendanceRecord r) throws SQLException {
        String sql = """
            UPDATE attendance_records SET
                time_in_1=?, time_out_1=?, time_in_2=?, time_out_2=?,
                regular_hours=?, overtime_hours=?, night_diff_hours=?,
                late_minutes=?, undertime_minutes=?,
                is_absent=?, is_holiday=?, is_rest_day=?,
                has_anomaly=?, anomaly_description=?,
                is_manually_edited=1,
                updated_by=?, updated_at=?
            WHERE attendance_id=?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            setTime(ps, 1, r.getTimeIn1());
            setTime(ps, 2, r.getTimeOut1());
            setTime(ps, 3, r.getTimeIn2());
            setTime(ps, 4, r.getTimeOut2());
            setBD  (ps, 5, r.getRegularHours());
            setBD  (ps, 6, r.getOvertimeHours());
            setBD  (ps, 7, r.getNightDiffHours());
            ps.setInt    (8,  r.getLateMinutes());
            ps.setInt    (9,  r.getUndertimeMinutes());
            ps.setBoolean(10, r.isAbsent());
            ps.setBoolean(11, r.isHoliday());
            ps.setBoolean(12, r.isRestDay());
            ps.setBoolean(13, r.isHasAnomaly());
            ps.setString (14, r.getAnomalyDescription());
            ps.setObject (15, r.getUpdatedBy());
            ps.setTimestamp(16, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt    (17, r.getAttendanceId());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM attendance_records WHERE attendance_id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean exists(Integer id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM attendance_records WHERE attendance_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @Override
    public long count() throws SQLException {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM attendance_records")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // -----------------------------------------------------------------------
    // Extra queries
    // -----------------------------------------------------------------------

    /**
     * Find all records for a given employee in a date range.
     */
    public List<AttendanceRecord> findByEmployeeAndPeriod(int employeeId,
                                                            LocalDate from,
                                                            LocalDate to) throws SQLException {
        String sql = """
            SELECT * FROM attendance_records
            WHERE employee_id = ?
              AND attendance_date BETWEEN ? AND ?
            ORDER BY attendance_date
        """;
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt (1, employeeId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Find records within a date range (all employees).
     */
    public List<AttendanceRecord> findByDateRange(LocalDate from, LocalDate to) throws SQLException {
        String sql = (from != null && to != null)
            ? "SELECT * FROM attendance_records WHERE attendance_date BETWEEN ? AND ? ORDER BY attendance_date, employee_id"
            : "SELECT * FROM attendance_records ORDER BY attendance_date, employee_id";

        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            PreparedStatement ps;
            if (from != null && to != null) {
                ps = c.prepareStatement(sql);
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
            } else {
                ps = c.prepareStatement(sql);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            ps.close();
        }
        return list;
    }

    /**
     * Find records with anomalies.
     */
    public List<AttendanceRecord> findAnomalies() throws SQLException {
        String sql = "SELECT * FROM attendance_records WHERE has_anomaly=1 ORDER BY attendance_date";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /**
     * Upsert: insert or update on conflict (employee_id, attendance_date).
     */
    public AttendanceRecord upsert(AttendanceRecord r) throws SQLException {
        String check = "SELECT attendance_id FROM attendance_records WHERE employee_id=? AND attendance_date=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(check)) {
            ps.setInt (1, r.getEmployeeId());
            ps.setDate(2, Date.valueOf(r.getAttendanceDate()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.setAttendanceId(rs.getInt(1));
                    update(r);
                    return r;
                }
            }
        }
        return create(r);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private AttendanceRecord map(ResultSet rs) throws SQLException {
        AttendanceRecord r = new AttendanceRecord();
        r.setAttendanceId (rs.getInt   ("attendance_id"));
        r.setEmployeeId   (rs.getInt   ("employee_id"));
        r.setAttendanceDate(rs.getDate ("attendance_date").toLocalDate());
        r.setTimeIn1 (getTime(rs, "time_in_1"));
        r.setTimeOut1(getTime(rs, "time_out_1"));
        r.setTimeIn2 (getTime(rs, "time_in_2"));
        r.setTimeOut2(getTime(rs, "time_out_2"));
        r.setRegularHours  (rs.getBigDecimal("regular_hours"));
        r.setOvertimeHours (rs.getBigDecimal("overtime_hours"));
        r.setNightDiffHours(rs.getBigDecimal("night_diff_hours"));
        r.setLateMinutes      (rs.getInt("late_minutes"));
        r.setUndertimeMinutes (rs.getInt("undertime_minutes"));
        r.setAbsent        (rs.getBoolean("is_absent"));
        r.setHoliday       (rs.getBoolean("is_holiday"));
        r.setRestDay       (rs.getBoolean("is_rest_day"));
        r.setHasAnomaly    (rs.getBoolean("has_anomaly"));
        r.setAnomalyDescription(rs.getString("anomaly_description"));
        r.setManuallyEdited(rs.getBoolean("is_manually_edited"));
        r.setImportBatchId ((Integer) rs.getObject("import_batch_id"));
        r.setDataSource    (rs.getString("data_source"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) r.setCreatedAt(ca.toLocalDateTime());
        return r;
    }

    private LocalTime getTime(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        if (s == null || s.isBlank()) return null;
        try { return LocalTime.parse(s); } catch (Exception e) { return null; }
    }

    private void setTime(PreparedStatement ps, int idx, LocalTime t) throws SQLException {
        if (t != null) ps.setString(idx, t.toString()); else ps.setNull(idx, Types.VARCHAR);
    }

    private void setBD(PreparedStatement ps, int idx, BigDecimal bd) throws SQLException {
        if (bd != null) ps.setBigDecimal(idx, bd); else ps.setBigDecimal(idx, BigDecimal.ZERO);
    }
}
