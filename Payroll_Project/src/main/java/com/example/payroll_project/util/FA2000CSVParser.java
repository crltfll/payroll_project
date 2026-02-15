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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FA2000 CSV Parser (CR1: FA2000 Biometric Attendance Integration)
 * Parses CSV exports from the FA2000 Fingerprint Attendance Checker (Model KC-02N)
 * 
 * Expected CSV Format:
 * Complex multi-column format with:
 * - Header rows containing metadata and employee information
 * - Summary row with totals
 * - Daily attendance records with Date/Week, Time1 (In/Out), Time2 (In/Out), OT (In/Out)
 * - "Missed" entries indicate no punch recorded
 */
public class FA2000CSVParser {
    
    private static final Logger logger = LoggerFactory.getLogger(FA2000CSVParser.class);
    
    // Constants for parsing
    private static final String MISSED = "Missed";
    private static final int HEADER_ROWS = 23; // Skip to actual data rows
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d+)/(\\w+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    
    /**
     * Parse FA2000 CSV file with complex format
     */
    public static List<AttendanceRecord> parseCSV(String filePath) throws IOException {
        logger.info("Parsing FA2000 CSV file: {}", filePath);
        
        List<AttendanceRecord> records = new ArrayList<>();
        String employeeCode = extractEmployeeCode(filePath);
        int currentYear = LocalDate.now().getYear();
        
        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                 .builder()
                 .setIgnoreEmptyLines(false) // Keep empty lines for structure
                 .setTrim(true)
                 .build())) {
            
            List<CSVRecord> allRecords = csvParser.getRecords();
            
            // Skip header rows and process data rows
            for (int i = HEADER_ROWS; i < allRecords.size(); i++) {
                CSVRecord record = allRecords.get(i);
                
                // Check if this is a data row (has date/week format)
                if (record.size() > 0 && !record.get(0).trim().isEmpty()) {
                    try {
                        AttendanceRecord attendanceRecord = parseDataRow(
                            record, employeeCode, currentYear
                        );
                        if (attendanceRecord != null) {
                            records.add(attendanceRecord);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse row {}: {}", i, e.getMessage());
                    }
                }
            }
        }
        
        logger.info("Parsed {} attendance records", records.size());
        return records;
    }
    
    /**
     * Extract employee code from filename or metadata
     */
    private static String extractEmployeeCode(String filePath) {
        // Try to extract from filename (e.g., "7_Report.csv" -> "7")
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            return String.format("%03d", Integer.parseInt(matcher.group(1)));
        }
        return "UNKNOWN";
    }
    
    /**
     * Parse a single data row
     */
    private static AttendanceRecord parseDataRow(CSVRecord record, String employeeCode, int year) {
        // Column 0: Date/Week (e.g., "26/Sun", "27/Mon")
        String dateWeek = record.get(0).trim();
        if (dateWeek.isEmpty() || !DATE_PATTERN.matcher(dateWeek).matches()) {
            return null;
        }
        
        // Extract date
        Matcher dateMatcher = DATE_PATTERN.matcher(dateWeek);
        if (!dateMatcher.find()) {
            return null;
        }
        
        int day = Integer.parseInt(dateMatcher.group(1));
        
        // Determine month from the date range in the file
        // For now, use current month or parse from headers
        int month = LocalDate.now().getMonthValue();
        
        LocalDate attendanceDate;
        try {
            attendanceDate = LocalDate.of(year, month, day);
        } catch (Exception e) {
            logger.warn("Invalid date: {}/{}/{}", year, month, day);
            return null;
        }
        
        // Create attendance record
        AttendanceRecord attendanceRecord = new AttendanceRecord();
        attendanceRecord.setAttendanceDate(attendanceDate);
        // Note: employeeId will be set by service layer after lookup
        
        // Parse time punches
        // Columns: 1=Time1 In, 3=Time1 Out, 6=Time2 In, 8=Time2 Out, 11=OT In, 13=OT Out
        LocalTime time1In = parseTime(getColumn(record, 1));
        LocalTime time1Out = parseTime(getColumn(record, 3));
        LocalTime time2In = parseTime(getColumn(record, 6));
        LocalTime time2Out = parseTime(getColumn(record, 8));
        LocalTime otIn = parseTime(getColumn(record, 11));
        LocalTime otOut = parseTime(getColumn(record, 13));
        
        // Assign to record (handling various punch combinations)
        if (time1In != null) {
            attendanceRecord.setTimeIn1(time1In);
        }
        if (time1Out != null) {
            attendanceRecord.setTimeOut1(time1Out);
        }
        if (time2In != null) {
            attendanceRecord.setTimeIn2(time2In);
        }
        if (time2Out != null) {
            attendanceRecord.setTimeOut2(time2Out);
        }
        
        // Handle OT times
        if (otIn != null || otOut != null) {
            // If we have OT but no regular out time, use OT as out time
            if (attendanceRecord.getTimeOut2() == null && otOut != null) {
                attendanceRecord.setTimeOut2(otOut);
            }
        }
        
        // Check if absent (all times are Missed)
        if (time1In == null && time1Out == null && time2In == null && time2Out == null && otOut == null) {
            attendanceRecord.setAbsent(true);
        }
        
        // Detect anomalies (F3: Intelligent Attendance Validation)
        detectAnomalies(attendanceRecord);
        
        return attendanceRecord;
    }
    
    /**
     * Safely get column value
     */
    private static String getColumn(CSVRecord record, int index) {
        if (index < record.size()) {
            String value = record.get(index).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
    
    /**
     * Parse time string, handling "Missed" and time formats
     */
    private static LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty() || MISSED.equalsIgnoreCase(timeStr)) {
            return null;
        }
        
        try {
            // Handle formats like "08:59", "18:40"
            Matcher matcher = TIME_PATTERN.matcher(timeStr);
            if (matcher.find()) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                return LocalTime.of(hour, minute);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse time: {}", timeStr);
        }
        
        return null;
    }
    
    /**
     * Detect attendance anomalies (F3)
     */
    private static void detectAnomalies(AttendanceRecord record) {
        if (record.isAbsent()) {
            // No anomaly for full absence
            return;
        }
        
        List<String> anomalies = new ArrayList<>();
        
        // Missing punch-in but have punch-out
        if (record.getTimeIn1() == null && record.getTimeOut2() != null) {
            anomalies.add("Missing check-in");
        }
        
        // Have punch-in but missing punch-out
        if (record.getTimeIn1() != null && record.getTimeOut2() == null) {
            anomalies.add("Missing check-out");
        }
        
        // Incomplete lunch break entries
        if ((record.getTimeOut1() == null && record.getTimeIn2() != null) ||
            (record.getTimeOut1() != null && record.getTimeIn2() == null)) {
            anomalies.add("Incomplete lunch break punches");
        }
        
        // Check for impossible sequences
        if (record.getTimeIn1() != null && record.getTimeOut1() != null) {
            if (record.getTimeOut1().isBefore(record.getTimeIn1())) {
                anomalies.add("Lunch out before morning in");
            }
        }
        
        if (record.getTimeIn2() != null && record.getTimeOut2() != null) {
            if (record.getTimeOut2().isBefore(record.getTimeIn2())) {
                anomalies.add("Evening out before afternoon in");
            }
        }
        
        // Very short shifts (less than 4 hours total)
        if (record.getTimeIn1() != null && record.getTimeOut2() != null) {
            long minutes = java.time.Duration.between(
                record.getTimeIn1(), 
                record.getTimeOut2()
            ).toMinutes();
            
            if (minutes < 240 && minutes > 0) {
                anomalies.add("Very short shift (" + (minutes / 60) + " hours)");
            }
        }
        
        if (!anomalies.isEmpty()) {
            record.setHasAnomaly(true);
            record.setAnomalyDescription(String.join("; ", anomalies));
        }
    }
    
    /**
     * Parse date range from CSV header
     */
    private static String[] parseDateRange(CSVParser csvParser) {
        try {
            for (CSVRecord record : csvParser.getRecords()) {
                if (record.size() > 2) {
                    String value = record.get(2);
                    if (value != null && value.contains("~")) {
                        return value.split("~");
                    }
                }
                if (csvParser.getRecordNumber() > 5) break;
            }
        } catch (Exception e) {
            logger.warn("Could not parse date range from header");
        }
        return new String[]{null, null};
    }
}
