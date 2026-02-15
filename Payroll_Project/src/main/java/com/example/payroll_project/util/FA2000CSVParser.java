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
 * Updated to handle All_Report.csv format:
 * - Header with date range in row 2 (e.g., "2025-01-26 ~ 2025-02-10")
 * - Employee ID in row 4 (e.g., "ID,7")
 * - Data starts from row ~24 with format: Date/Week, Time1 In/Out, Time2 In/Out, OT In/Out
 * - "Missed" entries indicate no punch recorded
 */
public class FA2000CSVParser {
    
    private static final Logger logger = LoggerFactory.getLogger(FA2000CSVParser.class);
    
    // Constants for parsing
    private static final String MISSED = "Missed";
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d+)/(\\w+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*~\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern EMPLOYEE_ID_PATTERN = Pattern.compile("ID[,\\s]*(\\d+)");
    
    /**
     * Parse FA2000 All_Report CSV file
     */
    public static List<AttendanceRecord> parseCSV(String filePath) throws IOException {
        logger.info("Parsing FA2000 All_Report CSV file: {}", filePath);
        
        List<AttendanceRecord> records = new ArrayList<>();
        
        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                 .builder()
                 .setIgnoreEmptyLines(false)
                 .setTrim(true)
                 .build())) {
            
            List<CSVRecord> allRecords = csvParser.getRecords();
            
            // Extract metadata
            String employeeCode = extractEmployeeId(allRecords);
            LocalDate[] dateRange = extractDateRange(allRecords);
            int currentYear = dateRange[0].getYear();
            int currentMonth = dateRange[0].getMonthValue();
            
            logger.info("Extracted Employee ID: {}, Date Range: {} to {}", 
                       employeeCode, dateRange[0], dateRange[1]);
            
            // Find where actual attendance data starts (after "Date/Week" header)
            int dataStartRow = findDataStartRow(allRecords);
            
            if (dataStartRow == -1) {
                logger.warn("Could not find data start row");
                return records;
            }
            
            logger.info("Data starts at row: {}", dataStartRow);
            
            // Process data rows
            for (int i = dataStartRow; i < allRecords.size(); i++) {
                CSVRecord record = allRecords.get(i);
                
                if (record.size() == 0 || record.get(0).trim().isEmpty()) {
                    continue;
                }
                
                try {
                    AttendanceRecord attendanceRecord = parseDataRow(
                        record, employeeCode, currentYear, currentMonth
                    );
                    if (attendanceRecord != null) {
                        records.add(attendanceRecord);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse row {}: {}", i, e.getMessage());
                }
            }
        }
        
        logger.info("Successfully parsed {} attendance records", records.size());
        return records;
    }
    
    /**
     * Extract employee ID from CSV header
     */
    private static String extractEmployeeId(List<CSVRecord> allRecords) {
        // Look in first few rows for employee ID
        for (int i = 0; i < Math.min(10, allRecords.size()); i++) {
            CSVRecord record = allRecords.get(i);
            for (String cell : record) {
                if (cell != null) {
                    Matcher matcher = EMPLOYEE_ID_PATTERN.matcher(cell);
                    if (matcher.find()) {
                        String id = matcher.group(1);
                        return String.format("EMP%03d", Integer.parseInt(id));
                    }
                }
            }
        }
        return "UNKNOWN";
    }
    
    /**
     * Extract date range from CSV header (row 2)
     */
    private static LocalDate[] extractDateRange(List<CSVRecord> allRecords) {
        LocalDate[] range = new LocalDate[2];
        LocalDate now = LocalDate.now();
        range[0] = now.withDayOfMonth(1);
        range[1] = now;
        
        if (allRecords.size() > 2) {
            CSVRecord row2 = allRecords.get(1); // Row 2 (index 1)
            for (String cell : row2) {
                if (cell != null && cell.contains("~")) {
                    Matcher matcher = DATE_RANGE_PATTERN.matcher(cell);
                    if (matcher.find()) {
                        try {
                            range[0] = LocalDate.parse(matcher.group(1));
                            range[1] = LocalDate.parse(matcher.group(2));
                            return range;
                        } catch (Exception e) {
                            logger.warn("Failed to parse date range: {}", e.getMessage());
                        }
                    }
                }
            }
        }
        
        return range;
    }
    
    /**
     * Find the row where actual attendance data starts
     */
    private static int findDataStartRow(List<CSVRecord> allRecords) {
        for (int i = 0; i < allRecords.size(); i++) {
            CSVRecord record = allRecords.get(i);
            if (record.size() > 0) {
                String firstCell = record.get(0).trim();
                // Look for date pattern like "26/Sun", "27/Mon", etc.
                if (DATE_PATTERN.matcher(firstCell).matches()) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Parse a single data row
     * Format: Date/Week (col 0), Time1 In (col 1), Time1 Out (col 3), 
     *         Time2 In (col 6), Time2 Out (col 8), OT In (col 11), OT Out (col 13)
     */
    private static AttendanceRecord parseDataRow(CSVRecord record, String employeeCode, 
                                                  int year, int month) {
        // Column 0: Date/Week (e.g., "26/Sun", "27/Mon")
        String dateWeek = getColumn(record, 0);
        if (dateWeek == null || dateWeek.isEmpty()) {
            return null;
        }
        
        Matcher dateMatcher = DATE_PATTERN.matcher(dateWeek);
        if (!dateMatcher.find()) {
            return null;
        }
        
        int day = Integer.parseInt(dateMatcher.group(1));
        
        // Handle month rollover (e.g., Jan 26 - Feb 10)
        LocalDate attendanceDate;
        try {
            attendanceDate = LocalDate.of(year, month, day);
        } catch (Exception e) {
            // Day might be in next month
            try {
                int nextMonth = month == 12 ? 1 : month + 1;
                int nextYear = month == 12 ? year + 1 : year;
                attendanceDate = LocalDate.of(nextYear, nextMonth, day);
            } catch (Exception e2) {
                logger.warn("Invalid date: {}/{}/{}", year, month, day);
                return null;
            }
        }
        
        // Create attendance record
        AttendanceRecord attendanceRecord = new AttendanceRecord();
        attendanceRecord.setAttendanceDate(attendanceDate);
        
        // Parse time punches from All_Report format
        // Columns: 1=Time1 In, 3=Time1 Out, 6=Time2 In, 8=Time2 Out, 11=OT In, 13=OT Out
        LocalTime time1In = parseTime(getColumn(record, 1));
        LocalTime time1Out = parseTime(getColumn(record, 3));
        LocalTime time2In = parseTime(getColumn(record, 6));
        LocalTime time2Out = parseTime(getColumn(record, 8));
        LocalTime otIn = parseTime(getColumn(record, 11));
        LocalTime otOut = parseTime(getColumn(record, 13));
        
        // In this format, Time1 seems to be morning in, OT Out is evening out
        // Most entries show: Time1 In (morning), OT Out (evening), with Missed for Time1 Out and Time2
        
        if (time1In != null) {
            attendanceRecord.setTimeIn1(time1In); // Morning clock in
        }
        
        // If we have OT Out, use it as the final clock out
        if (otOut != null) {
            attendanceRecord.setTimeOut2(otOut); // Evening clock out
        }
        
        // If we have Time1 Out, it might be lunch out
        if (time1Out != null && !MISSED.equalsIgnoreCase(getColumn(record, 3))) {
            attendanceRecord.setTimeOut1(time1Out);
        }
        
        // If we have Time2 In, it might be lunch in
        if (time2In != null && !MISSED.equalsIgnoreCase(getColumn(record, 6))) {
            attendanceRecord.setTimeIn2(time2In);
        }
        
        // If we have Time2 Out (not OT), use it
        if (time2Out != null && !MISSED.equalsIgnoreCase(getColumn(record, 8))) {
            if (attendanceRecord.getTimeOut2() == null) {
                attendanceRecord.setTimeOut2(time2Out);
            }
        }
        
        // Check if absent (all times are Missed)
        if (time1In == null && time1Out == null && time2In == null && 
            time2Out == null && otIn == null && otOut == null) {
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
            String value = record.get(index);
            if (value != null) {
                value = value.trim();
                if (!value.isEmpty() && !MISSED.equalsIgnoreCase(value)) {
                    return value;
                }
            }
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
            return;
        }
        
        List<String> anomalies = new ArrayList<>();
        
        // Missing morning clock-in but have clock-out
        if (record.getTimeIn1() == null && record.getTimeOut2() != null) {
            anomalies.add("Missing morning clock-in");
        }
        
        // Have morning clock-in but missing clock-out
        if (record.getTimeIn1() != null && record.getTimeOut2() == null) {
            anomalies.add("Missing evening clock-out");
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
}
