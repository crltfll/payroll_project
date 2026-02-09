package com.example.payroll_project.util;

import com.example.payroll_project.model.AttendanceRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * FA2000 CSV Parser (CR1: FA2000 Biometric Attendance Integration)
 * Parses CSV exports from the FA2000 Fingerprint Attendance Checker (Model KC-02N)
 * 
 * Expected CSV Format:
 * EmployeeID, DateTime, Status
 * Example: 001, 2026-02-08 08:00:00, I (Check In)
 *          001, 2026-02-08 17:00:00, O (Check Out)
 */
public class FA2000CSVParser {
    
    private static final Logger logger = LoggerFactory.getLogger(FA2000CSVParser.class);
    
    // CSV column names (adjust based on actual FA2000 export format)
    private static final String COL_EMPLOYEE_ID = "EmployeeID";
    private static final String COL_DATE_TIME = "DateTime";
    private static final String COL_STATUS = "Status";
    
    // Date time formatters
    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    };
    
    /**
     * Parse FA2000 CSV file and group attendance records by employee and date
     */
    public static List<AttendanceRecord> parseCSV(String filePath) throws IOException {
        logger.info("Parsing FA2000 CSV file: {}", filePath);
        
        List<RawAttendancePunch> punches = new ArrayList<>();
        
        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                 .builder()
                 .setHeader()
                 .setSkipHeaderRecord(true)
                 .setIgnoreEmptyLines(true)
                 .setTrim(true)
                 .build())) {
            
            for (CSVRecord record : csvParser) {
                try {
                    RawAttendancePunch punch = parseRecord(record);
                    if (punch != null) {
                        punches.add(punch);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse record {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        }
        
        logger.info("Parsed {} attendance punches", punches.size());
        
        // Group punches into attendance records
        List<AttendanceRecord> records = groupPunchesIntoRecords(punches);
        
        logger.info("Created {} attendance records", records.size());
        
        return records;
    }
    
    /**
     * Parse a single CSV record
     */
    private static RawAttendancePunch parseRecord(CSVRecord record) {
        String employeeCode = getColumnValue(record, COL_EMPLOYEE_ID, "AC-No.", "Pin");
        String dateTimeStr = getColumnValue(record, COL_DATE_TIME, "DateTime", "Date Time", "Time");
        String status = getColumnValue(record, COL_STATUS, "State", "Type");
        
        if (employeeCode == null || dateTimeStr == null) {
            logger.warn("Missing required fields in record {}", record.getRecordNumber());
            return null;
        }
        
        // Parse date time
        LocalDateTime dateTime = parseDateTime(dateTimeStr);
        if (dateTime == null) {
            logger.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
        
        // Determine if this is check-in or check-out
        boolean isCheckIn = determineCheckInStatus(status);
        
        return new RawAttendancePunch(employeeCode, dateTime, isCheckIn);
    }
    
    /**
     * Get column value with fallback to alternative column names
     */
    private static String getColumnValue(CSVRecord record, String... possibleNames) {
        for (String name : possibleNames) {
            try {
                if (record.isMapped(name)) {
                    String value = record.get(name);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            } catch (IllegalArgumentException e) {
                // Column doesn't exist, try next
            }
        }
        return null;
    }
    
    /**
     * Parse date time string with multiple format attempts
     */
    private static LocalDateTime parseDateTime(String dateTimeStr) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        return null;
    }
    
    /**
     * Determine if punch is check-in or check-out based on status field
     */
    private static boolean determineCheckInStatus(String status) {
        if (status == null) {
            return true; // Default to check-in if unknown
        }
        
        String s = status.toUpperCase();
        return s.contains("I") || s.contains("IN") || s.equals("0") || s.equals("CHECK IN");
    }
    
    /**
     * Group raw punches into attendance records (one per employee per day)
     */
    private static List<AttendanceRecord> groupPunchesIntoRecords(List<RawAttendancePunch> punches) {
        // Sort punches by employee and date time
        punches.sort(Comparator
            .comparing(RawAttendancePunch::getEmployeeCode)
            .thenComparing(RawAttendancePunch::getDateTime));
        
        // Group by employee and date
        Map<String, List<RawAttendancePunch>> groupedPunches = new HashMap<>();
        
        for (RawAttendancePunch punch : punches) {
            String key = punch.getEmployeeCode() + "|" + punch.getDateTime().toLocalDate();
            groupedPunches.computeIfAbsent(key, k -> new ArrayList<>()).add(punch);
        }
        
        // Convert to attendance records
        List<AttendanceRecord> records = new ArrayList<>();
        
        for (Map.Entry<String, List<RawAttendancePunch>> entry : groupedPunches.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|");
            String employeeCode = keyParts[0];
            LocalDate date = LocalDate.parse(keyParts[1]);
            
            AttendanceRecord record = createAttendanceRecord(employeeCode, date, entry.getValue());
            records.add(record);
        }
        
        return records;
    }
    
    /**
     * Create attendance record from punches
     */
    private static AttendanceRecord createAttendanceRecord(
            String employeeCode, LocalDate date, List<RawAttendancePunch> punches) {
        
        AttendanceRecord record = new AttendanceRecord();
        record.setAttendanceDate(date);
        // Note: employeeId will be set by the service layer after looking up by employeeCode
        
        // Extract time punches
        List<LocalTime> checkIns = new ArrayList<>();
        List<LocalTime> checkOuts = new ArrayList<>();
        
        for (RawAttendancePunch punch : punches) {
            if (punch.isCheckIn()) {
                checkIns.add(punch.getDateTime().toLocalTime());
            } else {
                checkOuts.add(punch.getDateTime().toLocalTime());
            }
        }
        
        // Assign to record slots (max 2 pairs)
        if (!checkIns.isEmpty()) {
            record.setTimeIn1(checkIns.get(0));
        }
        if (checkIns.size() > 1) {
            record.setTimeIn2(checkIns.get(1));
        }
        if (!checkOuts.isEmpty()) {
            record.setTimeOut1(checkOuts.get(0));
        }
        if (checkOuts.size() > 1) {
            record.setTimeOut2(checkOuts.get(1));
        }
        
        // Detect anomalies (F3: Intelligent Attendance Validation)
        detectAnomalies(record, checkIns, checkOuts);
        
        return record;
    }
    
    /**
     * Detect attendance anomalies (F3)
     */
    private static void detectAnomalies(AttendanceRecord record, 
                                        List<LocalTime> checkIns, 
                                        List<LocalTime> checkOuts) {
        List<String> anomalies = new ArrayList<>();
        
        // Missing punch-out
        if (!checkIns.isEmpty() && checkOuts.isEmpty()) {
            anomalies.add("Missing check-out");
        }
        
        // Missing punch-in
        if (checkIns.isEmpty() && !checkOuts.isEmpty()) {
            anomalies.add("Missing check-in");
        }
        
        // Duplicate punches
        if (checkIns.size() > 2) {
            anomalies.add("Multiple check-ins (" + checkIns.size() + ")");
        }
        if (checkOuts.size() > 2) {
            anomalies.add("Multiple check-outs (" + checkOuts.size() + ")");
        }
        
        // Impossible time ranges (check-out before check-in)
        if (!checkIns.isEmpty() && !checkOuts.isEmpty()) {
            if (checkOuts.get(0).isBefore(checkIns.get(0))) {
                anomalies.add("Check-out before check-in");
            }
        }
        
        // Very short shifts (less than 1 hour)
        if (record.getTimeIn1() != null && record.getTimeOut2() != null) {
            long minutes = java.time.Duration.between(record.getTimeIn1(), record.getTimeOut2()).toMinutes();
            if (minutes < 60 && minutes > 0) {
                anomalies.add("Very short shift (" + minutes + " minutes)");
            }
        }
        
        if (!anomalies.isEmpty()) {
            record.setHasAnomaly(true);
            record.setAnomalyDescription(String.join("; ", anomalies));
        }
    }
    
    /**
     * Internal class to hold raw attendance punch data
     */
    private static class RawAttendancePunch {
        private final String employeeCode;
        private final LocalDateTime dateTime;
        private final boolean isCheckIn;
        
        public RawAttendancePunch(String employeeCode, LocalDateTime dateTime, boolean isCheckIn) {
            this.employeeCode = employeeCode;
            this.dateTime = dateTime;
            this.isCheckIn = isCheckIn;
        }
        
        public String getEmployeeCode() {
            return employeeCode;
        }
        
        public LocalDateTime getDateTime() {
            return dateTime;
        }
        
        public boolean isCheckIn() {
            return isCheckIn;
        }
    }
}
