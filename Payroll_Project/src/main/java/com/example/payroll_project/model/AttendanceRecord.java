package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Attendance Record domain model (CR1: FA2000 Integration, F10: Attendance Records Management)
 */
public class AttendanceRecord {

    public enum LeaveType {
        NONE, SICK_LEAVE, VACATION_LEAVE, EMERGENCY_LEAVE, MATERNITY_LEAVE, PATERNITY_LEAVE, OTHER
    }

    private Integer attendanceId;
    private Integer employeeId;
    private LocalDate attendanceDate;

    private LocalTime timeIn1;
    private LocalTime timeOut1;
    private LocalTime timeIn2;
    private LocalTime timeOut2;

    private BigDecimal regularHours;
    private BigDecimal overtimeHours;
    private BigDecimal nightDiffHours;
    private Integer lateMinutes;
    private Integer undertimeMinutes;

    private boolean absent;
    private boolean holiday;
    private boolean restDay;

    private boolean hasAnomaly;
    private String anomalyDescription;
    private boolean manuallyEdited;

    // Leave override support (persisted as leave_type column)
    private LeaveType leaveType = LeaveType.NONE;

    private Integer importBatchId;
    private String dataSource;

    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;

    public AttendanceRecord() {
        this.regularHours = BigDecimal.ZERO;
        this.overtimeHours = BigDecimal.ZERO;
        this.nightDiffHours = BigDecimal.ZERO;
        this.lateMinutes = 0;
        this.undertimeMinutes = 0;
        this.absent = false;
        this.holiday = false;
        this.restDay = false;
        this.hasAnomaly = false;
        this.manuallyEdited = false;
        this.leaveType = LeaveType.NONE;
        this.dataSource = "FA2000_CSV";
        this.createdAt = LocalDateTime.now();
    }

    public AttendanceRecord(Integer employeeId, LocalDate attendanceDate) {
        this();
        this.employeeId = employeeId;
        this.attendanceDate = attendanceDate;
    }

    public boolean isOnLeave() {
        return leaveType != null && leaveType != LeaveType.NONE;
    }

    public boolean hasCompleteTimeEntries() {
        return timeIn1 != null && timeOut2 != null;
    }

    public boolean isFullDay() {
        return regularHours != null && regularHours.compareTo(new BigDecimal("8.0")) >= 0;
    }

    public BigDecimal getTotalHoursWorked() {
        BigDecimal total = BigDecimal.ZERO;
        if (regularHours != null)  total = total.add(regularHours);
        if (overtimeHours != null) total = total.add(overtimeHours);
        return total;
    }

    public String getTimeSummary() {
        if (isOnLeave())  return "LEAVE: " + leaveType.name().replace('_', ' ');
        if (absent)       return "ABSENT";
        StringBuilder sb = new StringBuilder();
        if (timeIn1 != null) {
            sb.append(timeIn1);
            if (timeOut2 != null) sb.append(" - ").append(timeOut2);
        }
        if (hasAnomaly) sb.append(" [ANOMALY]");
        return sb.toString();
    }

    // ── Getters & Setters ──────────────────────────────────────────────────
    public Integer getAttendanceId()                  { return attendanceId; }
    public void setAttendanceId(Integer v)            { this.attendanceId = v; }
    public Integer getEmployeeId()                    { return employeeId; }
    public void setEmployeeId(Integer v)              { this.employeeId = v; }
    public LocalDate getAttendanceDate()              { return attendanceDate; }
    public void setAttendanceDate(LocalDate v)        { this.attendanceDate = v; }
    public LocalTime getTimeIn1()                     { return timeIn1; }
    public void setTimeIn1(LocalTime v)               { this.timeIn1 = v; }
    public LocalTime getTimeOut1()                    { return timeOut1; }
    public void setTimeOut1(LocalTime v)              { this.timeOut1 = v; }
    public LocalTime getTimeIn2()                     { return timeIn2; }
    public void setTimeIn2(LocalTime v)               { this.timeIn2 = v; }
    public LocalTime getTimeOut2()                    { return timeOut2; }
    public void setTimeOut2(LocalTime v)              { this.timeOut2 = v; }
    public BigDecimal getRegularHours()               { return regularHours; }
    public void setRegularHours(BigDecimal v)         { this.regularHours = v; }
    public BigDecimal getOvertimeHours()              { return overtimeHours; }
    public void setOvertimeHours(BigDecimal v)        { this.overtimeHours = v; }
    public BigDecimal getNightDiffHours()             { return nightDiffHours; }
    public void setNightDiffHours(BigDecimal v)       { this.nightDiffHours = v; }
    public Integer getLateMinutes()                   { return lateMinutes; }
    public void setLateMinutes(Integer v)             { this.lateMinutes = v; }
    public Integer getUndertimeMinutes()              { return undertimeMinutes; }
    public void setUndertimeMinutes(Integer v)        { this.undertimeMinutes = v; }
    public boolean isAbsent()                         { return absent; }
    public void setAbsent(boolean v)                  { this.absent = v; }
    public boolean isHoliday()                        { return holiday; }
    public void setHoliday(boolean v)                 { this.holiday = v; }
    public boolean isRestDay()                        { return restDay; }
    public void setRestDay(boolean v)                 { this.restDay = v; }
    public boolean isHasAnomaly()                     { return hasAnomaly; }
    public void setHasAnomaly(boolean v)              { this.hasAnomaly = v; }
    public String getAnomalyDescription()             { return anomalyDescription; }
    public void setAnomalyDescription(String v)       { this.anomalyDescription = v; }
    public boolean isManuallyEdited()                 { return manuallyEdited; }
    public void setManuallyEdited(boolean v)          { this.manuallyEdited = v; }
    public LeaveType getLeaveType()                   { return leaveType; }
    public void setLeaveType(LeaveType v)             { this.leaveType = v != null ? v : LeaveType.NONE; }
    public Integer getImportBatchId()                 { return importBatchId; }
    public void setImportBatchId(Integer v)           { this.importBatchId = v; }
    public String getDataSource()                     { return dataSource; }
    public void setDataSource(String v)               { this.dataSource = v; }
    public Integer getCreatedBy()                     { return createdBy; }
    public void setCreatedBy(Integer v)               { this.createdBy = v; }
    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void setCreatedAt(LocalDateTime v)         { this.createdAt = v; }
    public Integer getUpdatedBy()                     { return updatedBy; }
    public void setUpdatedBy(Integer v)               { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt()               { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)         { this.updatedAt = v; }

    @Override
    public String toString() {
        return String.format("Attendance[Employee: %d, Date: %s, Hours: %s]",
            employeeId, attendanceDate, getTotalHoursWorked());
    }
}
