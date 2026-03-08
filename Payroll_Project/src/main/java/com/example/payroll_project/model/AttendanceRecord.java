package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Attendance Record domain model (CR1: FA2000 Integration, F10: Attendance Records Management)
 * Represents daily attendance data from the FA2000 biometric device
 */
public class AttendanceRecord {
    
    private Integer attendanceId;

    private Integer employeeId;
    private LocalDate attendanceDate;
    
    private LocalTime timeIn1;   // Morning in
    private LocalTime timeOut1;  // Lunch out
    private LocalTime timeIn2;   // Lunch in
    private LocalTime timeOut2;  // Evening out
    
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
        this.dataSource = "FA2000_CSV";
        this.createdAt = LocalDateTime.now();
    }
    
    public AttendanceRecord(Integer employeeId, LocalDate attendanceDate) {
        this();
        this.employeeId = employeeId;
        this.attendanceDate = attendanceDate;
    }
    

    public boolean hasCompleteTimeEntries() {
        return timeIn1 != null && timeOut2 != null;
    }
    public boolean isFullDay() {
        return regularHours != null && regularHours.compareTo(new BigDecimal("8.0")) >= 0;
    }
    public BigDecimal getTotalHoursWorked() {
        BigDecimal total = BigDecimal.ZERO;
        if (regularHours != null) total = total.add(regularHours);
        if (overtimeHours != null) total = total.add(overtimeHours);
        return total;
    }
    public String getTimeSummary() {
        if (absent) {
            return "ABSENT";
        }
        
        StringBuilder sb = new StringBuilder();
        if (timeIn1 != null) {
            sb.append(timeIn1);
            if (timeOut2 != null) {
                sb.append(" - ").append(timeOut2);
            }
        }
        
        if (hasAnomaly) {
            sb.append(" [ANOMALY]");
        }
        
        return sb.toString();
    }

    public Integer getAttendanceId() {
        return attendanceId;
    }
    public void setAttendanceId(Integer attendanceId) {
        this.attendanceId = attendanceId;
    }
    public Integer getEmployeeId() {
        return employeeId;
    }
    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }
    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }
    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }
    public LocalTime getTimeIn1() {
        return timeIn1;
    }
    public void setTimeIn1(LocalTime timeIn1) {
        this.timeIn1 = timeIn1;
    }
    public LocalTime getTimeOut1() {
        return timeOut1;
    }
    public void setTimeOut1(LocalTime timeOut1) {
        this.timeOut1 = timeOut1;
    }
    public LocalTime getTimeIn2() {
        return timeIn2;
    }
    public void setTimeIn2(LocalTime timeIn2) {
        this.timeIn2 = timeIn2;
    }
    public LocalTime getTimeOut2() {
        return timeOut2;
    }
    public void setTimeOut2(LocalTime timeOut2) {
        this.timeOut2 = timeOut2;
    }
    public BigDecimal getRegularHours() {
        return regularHours;
    }
    public void setRegularHours(BigDecimal regularHours) {
        this.regularHours = regularHours;
    }
    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }
    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }
    public BigDecimal getNightDiffHours() {
        return nightDiffHours;
    }
    public void setNightDiffHours(BigDecimal nightDiffHours) {
        this.nightDiffHours = nightDiffHours;
    }
    public Integer getLateMinutes() {
        return lateMinutes;
    }
    public void setLateMinutes(Integer lateMinutes) {
        this.lateMinutes = lateMinutes;
    }
    public Integer getUndertimeMinutes() {
        return undertimeMinutes;
    }
    public void setUndertimeMinutes(Integer undertimeMinutes) {
        this.undertimeMinutes = undertimeMinutes;
    }
    public boolean isAbsent() {
        return absent;
    }
    public void setAbsent(boolean absent) {
        this.absent = absent;
    }
    public boolean isHoliday() {
        return holiday;
    }
    public void setHoliday(boolean holiday) {
        this.holiday = holiday;
    }
    public boolean isRestDay() {
        return restDay;
    }
    public void setRestDay(boolean restDay) {
        this.restDay = restDay;
    }
    public boolean isHasAnomaly() {
        return hasAnomaly;
    }
    public void setHasAnomaly(boolean hasAnomaly) {
        this.hasAnomaly = hasAnomaly;
    }
    public String getAnomalyDescription() {
        return anomalyDescription;
    }
    public void setAnomalyDescription(String anomalyDescription) {
        this.anomalyDescription = anomalyDescription;
    }
    public boolean isManuallyEdited() {
        return manuallyEdited;
    }
    public void setManuallyEdited(boolean manuallyEdited) {
        this.manuallyEdited = manuallyEdited;
    }
    public Integer getImportBatchId() {
        return importBatchId;
    }
    public void setImportBatchId(Integer importBatchId) {
        this.importBatchId = importBatchId;
    }
    public String getDataSource() {
        return dataSource;
    }
    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
    public Integer getCreatedBy() {
        return createdBy;
    }
    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public Integer getUpdatedBy() {
        return updatedBy;
    }
    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return String.format("Attendance[Employee: %d, Date: %s, Hours: %s]", 
            employeeId, attendanceDate, getTotalHoursWorked());
    }
}
