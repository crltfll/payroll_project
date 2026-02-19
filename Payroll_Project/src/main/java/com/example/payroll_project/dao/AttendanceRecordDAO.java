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
 * Attendance Record Data Access Object (CR1: FA2000 Integration)
 * Handles all database operations for attendance records
 */
public class AttendanceRecordDAO implements BaseDAO<AttendanceRecord, Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(AttendanceRecordDAO.class);
    private final DatabaseManager dbManager;
    
    public AttendanceRecordDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    @Override
    public AttendanceRecord create(AttendanceRecord record) throws SQLException {
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setAttendanceParameters(stmt, record);
            stmt.setObject(20, record.getCreatedBy());
            stmt.setTimestamp(21, Timestamp.valueOf(record.getCreatedAt()));
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creating attendance record failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    record.setAttendanceId(generatedKeys.getInt(1));
                    logger.debug("Created attendance record: {}", record.getAttendanceId());
                    return record;
                } else {
                    throw new SQLException("Creating attendance record failed, no ID obtained.");
                }
            }
        }
    }
    
    /**
     * Create or update attendance record (upsert)
     */
    public AttendanceRecord createOrUpdate(AttendanceRecord record) throws SQLException {
        // Check if record exists for this employee and date
        Optional<AttendanceRecord> existing = findByEmployeeAndDate(
            record.getEmployeeId(), 
            record.getAttendanceDate()
        );
        
        if (existing.isPresent()) {
            record.setAttendanceId(existing.get().getAttendanceId());
            update(record);
            return record;
        } else {
            return create(record);
        }
    }
    
    /**
     * Batch insert for CSV import
     */
    public int batchCreate(List<AttendanceRecord> records) throws SQLException {
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(employee_id, attendance_date) DO UPDATE SET
                time_in_1 = excluded.time_in_1,
                time_out_1 = excluded.time_out_1,
                time_in_2 = excluded.time_in_2,
                time_out_2 = excluded.time_out_2,
                regular_hours = excluded.regular_hours,
                overtime_hours = excluded.overtime_hours,
                night_diff_hours = excluded.night_diff_hours,
                late_minutes = excluded.late_minutes,
                undertime_minutes = excluded.undertime_minutes,
                is_absent = excluded.is_absent,
                has_anomaly = excluded.has_anomaly,
                anomaly_description = excluded.anomaly_description,
                updated_at = CURRENT_TIMESTAMP
        """;
        
        int insertedCount = 0;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            for (AttendanceRecord record : records) {
                setAttendanceParameters(stmt, record);
                stmt.setObject(20, record.getCreatedBy());
                stmt.setTimestamp(21, Timestamp.valueOf(record.getCreatedAt()));
                
                stmt.addBatch();
                insertedCount++;
                
                // Execute batch every 100 records
                if (insertedCount % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            
            // Execute remaining batch
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
            
            logger.info("Batch inserted {} attendance records", insertedCount);
            return insertedCount;
            
        } catch (SQLException e) {
            logger.error("Failed to batch insert attendance records", e);
            dbManager.rollback();
            throw e;
        }
    }
    
    @Override
    public Optional<AttendanceRecord> findById(Integer id) throws SQLException {
        String sql = "SELECT * FROM attendance_records WHERE attendance_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAttendance(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Find attendance record by employee and date
     */
    public Optional<AttendanceRecord> findByEmployeeAndDate(Integer employeeId, LocalDate date) 
            throws SQLException {
        String sql = """
            SELECT * FROM attendance_records 
            WHERE employee_id = ? AND attendance_date = ?
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, employeeId);
            stmt.setDate(2, Date.valueOf(date));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToAttendance(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Find attendance records by date range
     */
    public List<AttendanceRecord> findByDateRange(LocalDate startDate, LocalDate endDate) 
            throws SQLException {
        String sql = """
            SELECT * FROM attendance_records
            WHERE attendance_date BETWEEN ? AND ?
            ORDER BY attendance_date DESC, employee_id
        """;
        
        List<AttendanceRecord> records = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapResultSetToAttendance(rs));
                }
            }
        }
        
        return records;
    }
    
    /**
     * Find attendance records with anomalies
     */
    public List<AttendanceRecord> findAnomalies(LocalDate startDate, LocalDate endDate) 
            throws SQLException {
        String sql = """
            SELECT * FROM attendance_records
            WHERE attendance_date BETWEEN ? AND ?
            AND has_anomaly = 1
            ORDER BY attendance_date DESC
        """;
        
        List<AttendanceRecord> records = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapResultSetToAttendance(rs));
                }
            }
        }
        
        return records;
    }
    
    /**
     * Find records by employee
     */
    public List<AttendanceRecord> findByEmployee(Integer employeeId, LocalDate startDate, 
            LocalDate endDate) throws SQLException {
        String sql = """
            SELECT * FROM attendance_records
            WHERE employee_id = ?
            AND attendance_date BETWEEN ? AND ?
            ORDER BY attendance_date DESC
        """;
        
        List<AttendanceRecord> records = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, employeeId);
            stmt.setDate(2, Date.valueOf(startDate));
            stmt.setDate(3, Date.valueOf(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapResultSetToAttendance(rs));
                }
            }
        }
        
        return records;
    }
    
    @Override
    public List<AttendanceRecord> findAll() throws SQLException {
        String sql = """
            SELECT * FROM attendance_records
            ORDER BY attendance_date DESC, employee_id
            LIMIT 1000
        """;
        
        List<AttendanceRecord> records = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                records.add(mapResultSetToAttendance(rs));
            }
        }
        
        return records;
    }
    
    @Override
    public boolean update(AttendanceRecord record) throws SQLException {
        String sql = """
            UPDATE attendance_records SET
                time_in_1 = ?, time_out_1 = ?, time_in_2 = ?, time_out_2 = ?,
                regular_hours = ?, overtime_hours = ?, night_diff_hours = ?,
                late_minutes = ?, undertime_minutes = ?,
                is_absent = ?, is_holiday = ?, is_rest_day = ?,
                has_anomaly = ?, anomaly_description = ?, is_manually_edited = ?,
                updated_by = ?, updated_at = ?
            WHERE attendance_id = ?
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, record.getTimeIn1() != null ? Time.valueOf(record.getTimeIn1()) : null);
            stmt.setObject(2, record.getTimeOut1() != null ? Time.valueOf(record.getTimeOut1()) : null);
            stmt.setObject(3, record.getTimeIn2() != null ? Time.valueOf(record.getTimeIn2()) : null);
            stmt.setObject(4, record.getTimeOut2() != null ? Time.valueOf(record.getTimeOut2()) : null);
            stmt.setBigDecimal(5, record.getRegularHours());
            stmt.setBigDecimal(6, record.getOvertimeHours());
            stmt.setBigDecimal(7, record.getNightDiffHours());
            stmt.setInt(8, record.getLateMinutes());
            stmt.setInt(9, record.getUndertimeMinutes());
            stmt.setBoolean(10, record.isAbsent());
            stmt.setBoolean(11, record.isHoliday());
            stmt.setBoolean(12, record.isRestDay());
            stmt.setBoolean(13, record.isHasAnomaly());
            stmt.setString(14, record.getAnomalyDescription());
            stmt.setBoolean(15, record.isManuallyEdited());
            stmt.setObject(16, record.getUpdatedBy());
            stmt.setTimestamp(17, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(18, record.getAttendanceId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Updated attendance record: {}", record.getAttendanceId());
                return true;
            }
            return false;
        }
    }
    
    @Override
    public boolean delete(Integer id) throws SQLException {
        String sql = "DELETE FROM attendance_records WHERE attendance_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Deleted attendance record: {}", id);
                return true;
            }
            return false;
        }
    }
    
    @Override
    public boolean exists(Integer id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_records WHERE attendance_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    @Override
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM attendance_records";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        return 0;
    }
    
    /**
     * Count records in date range
     */
    public long countInRange(LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM attendance_records
            WHERE attendance_date BETWEEN ? AND ?
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Count anomalies in date range
     */
    public long countAnomalies(LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM attendance_records
            WHERE attendance_date BETWEEN ? AND ?
            AND has_anomaly = 1
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Set attendance parameters for PreparedStatement
     */
    private void setAttendanceParameters(PreparedStatement stmt, AttendanceRecord record) 
            throws SQLException {
        stmt.setInt(1, record.getEmployeeId());
        stmt.setDate(2, Date.valueOf(record.getAttendanceDate()));
        stmt.setObject(3, record.getTimeIn1() != null ? Time.valueOf(record.getTimeIn1()) : null);
        stmt.setObject(4, record.getTimeOut1() != null ? Time.valueOf(record.getTimeOut1()) : null);
        stmt.setObject(5, record.getTimeIn2() != null ? Time.valueOf(record.getTimeIn2()) : null);
        stmt.setObject(6, record.getTimeOut2() != null ? Time.valueOf(record.getTimeOut2()) : null);
        stmt.setBigDecimal(7, record.getRegularHours());
        stmt.setBigDecimal(8, record.getOvertimeHours());
        stmt.setBigDecimal(9, record.getNightDiffHours());
        stmt.setInt(10, record.getLateMinutes());
        stmt.setInt(11, record.getUndertimeMinutes());
        stmt.setBoolean(12, record.isAbsent());
        stmt.setBoolean(13, record.isHoliday());
        stmt.setBoolean(14, record.isRestDay());
        stmt.setBoolean(15, record.isHasAnomaly());
        stmt.setString(16, record.getAnomalyDescription());
        stmt.setBoolean(17, record.isManuallyEdited());
        stmt.setObject(18, record.getImportBatchId());
        stmt.setString(19, record.getDataSource());
    }
    
    /**
     * Map ResultSet to AttendanceRecord object
     */
    private AttendanceRecord mapResultSetToAttendance(ResultSet rs) throws SQLException {
        AttendanceRecord record = new AttendanceRecord();
        
        record.setAttendanceId(rs.getInt("attendance_id"));
        record.setEmployeeId(rs.getInt("employee_id"));
        
        Date attendanceDate = rs.getDate("attendance_date");
        if (attendanceDate != null) {
            record.setAttendanceDate(attendanceDate.toLocalDate());
        }
        
        Time timeIn1 = rs.getTime("time_in_1");
        if (timeIn1 != null) {
            record.setTimeIn1(timeIn1.toLocalTime());
        }
        
        Time timeOut1 = rs.getTime("time_out_1");
        if (timeOut1 != null) {
            record.setTimeOut1(timeOut1.toLocalTime());
        }
        
        Time timeIn2 = rs.getTime("time_in_2");
        if (timeIn2 != null) {
            record.setTimeIn2(timeIn2.toLocalTime());
        }
        
        Time timeOut2 = rs.getTime("time_out_2");
        if (timeOut2 != null) {
            record.setTimeOut2(timeOut2.toLocalTime());
        }
        
        record.setRegularHours(rs.getBigDecimal("regular_hours"));
        record.setOvertimeHours(rs.getBigDecimal("overtime_hours"));
        record.setNightDiffHours(rs.getBigDecimal("night_diff_hours"));
        record.setLateMinutes(rs.getInt("late_minutes"));
        record.setUndertimeMinutes(rs.getInt("undertime_minutes"));
        record.setAbsent(rs.getBoolean("is_absent"));
        record.setHoliday(rs.getBoolean("is_holiday"));
        record.setRestDay(rs.getBoolean("is_rest_day"));
        record.setHasAnomaly(rs.getBoolean("has_anomaly"));
        record.setAnomalyDescription(rs.getString("anomaly_description"));
        record.setManuallyEdited(rs.getBoolean("is_manually_edited"));
        
        Integer importBatchId = (Integer) rs.getObject("import_batch_id");
        record.setImportBatchId(importBatchId);
        
        record.setDataSource(rs.getString("data_source"));
        
        Integer createdBy = (Integer) rs.getObject("created_by");
        record.setCreatedBy(createdBy);
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            record.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Integer updatedBy = (Integer) rs.getObject("updated_by");
        record.setUpdatedBy(updatedBy);
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            record.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        return record;
    }
}
