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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FA2000 CSV Parser (CR1: FA2000 Biometric Attendance Integration)
 * Parses All_Report CSV exports from the FA2000 Fingerprint Attendance Checker (Model KC-02N)
 *
 * CSV Structure:
 *   Row 0: "Attend. Report"
 *   Row 1: Date,, 2025-01-26 ~ 2025-02-10,...
 *   Row 2: Dept., DeptName,..., Name, EmpName,...
 *   Row 3: Date, DateRange,..., ID, EmpID,...
 *   Rows 4-10: Summary statistics
 *   Row 11: blank
 *   Row 12: "All Report",...
 *   Row 13: "Date/Week, Time1,...,Time2,...,OT,..."  (block headers)
 *   Row 14: ",In,,Out,,,In,,Out,,In,,Out,..."        (sub-headers)
 *   Rows 15+: actual attendance data rows
 *
 * Column layout per 15-col employee block (0-indexed within block):
 *   0  = Date/Week  (e.g. "27/Mon")
 *   1  = Time1 In   (morning clock-in)
 *   3  = Time1 Out  (lunch out)
 *   6  = Time2 In   (lunch in)
 *   8  = Time2 Out  (regular end / afternoon out)
 *   10 = OT In      (OT start, or final clock-out when no separate end punch)
 *   12 = OT Out     (OT end / final clock-out)
 */
public class FA2000CSVParser {

    private static final Logger logger = LoggerFactory.getLogger(FA2000CSVParser.class);


    private static final int COL_DATE      = 0;
    private static final int COL_TIME1_IN  = 1;
    private static final int COL_TIME1_OUT = 3;
    private static final int COL_TIME2_IN  = 6;
    private static final int COL_TIME2_OUT = 8;
    private static final int COL_OT_IN     = 10;
    private static final int COL_OT_OUT    = 12;
    private static final int BLOCK_WIDTH   = 15;

    private static final String MISSED = "Missed";
    private static final Pattern DATE_PATTERN       = Pattern.compile("^(\\d{1,2})/([A-Za-z]+)$");
    private static final Pattern TIME_PATTERN       = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*~\\s*(\\d{4}-\\d{2}-\\d{2})");



    /**
     * Parse all employees found in an FA2000 All_Report CSV file.
     *
     */
    public static Map<String, List<AttendanceRecord>> parseAllEmployees(String filePath) throws IOException {
        logger.info("Parsing FA2000 All_Report CSV: {}", filePath);

        Map<String, List<AttendanceRecord>> result = new LinkedHashMap<>();

        try (Reader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader,
                     CSVFormat.DEFAULT.builder()
                             .setIgnoreEmptyLines(false)
                             .setTrim(true)
                             .build())) {

            List<CSVRecord> rows = csvParser.getRecords();

            List<EmployeeBlock> blocks = detectEmployeeBlocks(rows);
            if (blocks.isEmpty()) {
                logger.warn("No employee blocks detected in CSV");
                return result;
            }
            logger.info("Detected {} employee block(s)", blocks.size());

            int dataStart = findDataStartRow(rows);
            if (dataStart == -1) {
                logger.warn("Could not locate attendance data rows");
                return result;
            }
            logger.info("Data starts at row index {}", dataStart);

            for (EmployeeBlock block : blocks) {
                List<AttendanceRecord> records = parseEmployeeBlock(
                        rows, dataStart, block);
                if (!records.isEmpty()) {
                    result.put(block.employeeCode, records);
                    logger.info("Parsed {} records for employee {}",
                            records.size(), block.employeeCode);
                }
            }
        }

        return result;
    }


    public static List<AttendanceRecord> parseCSV(String filePath) throws IOException {
        Map<String, List<AttendanceRecord>> all = parseAllEmployees(filePath);
        List<AttendanceRecord> flat = new ArrayList<>();
        all.values().forEach(flat::addAll);
        return flat;
    }



    private static List<EmployeeBlock> detectEmployeeBlocks(List<CSVRecord> rows) {
        List<EmployeeBlock> blocks = new ArrayList<>();


        LocalDate[] dateRange = extractDateRange(rows);

        for (int blockIdx = 0; blockIdx < 10; blockIdx++) {
            int idLabelCol = 8 + blockIdx * BLOCK_WIDTH;
            int idValueCol = 9 + blockIdx * BLOCK_WIDTH;

            if (rows.size() < 4) break;

            CSVRecord row3 = rows.get(3);

            String idLabel = safeGet(row3, idLabelCol);
            String idValue = safeGet(row3, idValueCol);

            if ("ID".equalsIgnoreCase(idLabel) && idValue != null && idValue.matches("\\d+")) {
                String empCode  = String.format("EMP%03d", Integer.parseInt(idValue));

                CSVRecord row2   = rows.get(2);
                String nameLabel = safeGet(row2, idLabelCol);    // "Name"
                String empName   = safeGet(row2, idValueCol);    // actual name
                if (empName == null || empName.isEmpty()) empName = empCode;

                blocks.add(new EmployeeBlock(blockIdx, empCode, empName,
                        blockIdx * BLOCK_WIDTH, dateRange));
            } else {

                if (blockIdx > 0) break;
            }
        }

        return blocks;
    }



    private static int findDataStartRow(List<CSVRecord> rows) {
        for (int i = 0; i < rows.size(); i++) {
            CSVRecord row = rows.get(i);
            if (row.size() > 0) {
                String cell = row.get(0).trim();
                if (DATE_PATTERN.matcher(cell).matches()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<AttendanceRecord> parseEmployeeBlock(List<CSVRecord> rows,
                                                              int dataStart,
                                                              EmployeeBlock block) {
        List<AttendanceRecord> records = new ArrayList<>();
        LocalDate prevDate = null;
        int year  = block.dateRange[0].getYear();
        int month = block.dateRange[0].getMonthValue();

        for (int r = dataStart; r < rows.size(); r++) {
            CSVRecord row = rows.get(r);
            int offset = block.colOffset;

            String dateCell = safeGet(row, offset + COL_DATE);
            if (dateCell == null || dateCell.isEmpty()) continue;

            Matcher dm = DATE_PATTERN.matcher(dateCell);
            if (!dm.matches()) continue;

            int day = Integer.parseInt(dm.group(1));

            LocalDate attendanceDate = resolveDate(year, month, day, prevDate, block.dateRange);
            if (attendanceDate == null) continue;
            prevDate = attendanceDate;

            LocalTime time1In  = parseTime(safeGet(row, offset + COL_TIME1_IN));
            LocalTime time1Out = parseTime(safeGet(row, offset + COL_TIME1_OUT));
            LocalTime time2In  = parseTime(safeGet(row, offset + COL_TIME2_IN));
            LocalTime time2Out = parseTime(safeGet(row, offset + COL_TIME2_OUT));
            LocalTime otIn     = parseTime(safeGet(row, offset + COL_OT_IN));
            LocalTime otOut    = parseTime(safeGet(row, offset + COL_OT_OUT));

            AttendanceRecord rec = new AttendanceRecord();
            rec.setAttendanceDate(attendanceDate);

            rec.setTimeIn1(time1In);

            if (otOut != null) {
                rec.setTimeOut2(otOut);
            } else if (time2Out != null) {
                rec.setTimeOut2(time2Out);
            } else if (otIn != null) {
                rec.setTimeOut2(otIn);
            }

            if (time1Out != null) rec.setTimeOut1(time1Out);
            if (time2In  != null) rec.setTimeIn2(time2In);
            if (time1In == null && time1Out == null && time2In == null
                    && time2Out == null && otIn == null && otOut == null) {
                rec.setAbsent(true);
            }

            detectAnomalies(rec);
            records.add(rec);
        }

        return records;
    }



    private static LocalDate resolveDate(int baseYear, int baseMonth, int day,
                                          LocalDate prevDate, LocalDate[] range) {
        try {
            LocalDate d = LocalDate.of(baseYear, baseMonth, day);
            if (!d.isBefore(range[0]) && !d.isAfter(range[1])) {
                if (prevDate == null || !d.isBefore(prevDate)) return d;
            }
        } catch (Exception ignored) { /* invalid day in month */ }

        try {
            int nextMonth = baseMonth == 12 ? 1  : baseMonth + 1;
            int nextYear  = baseMonth == 12 ? baseYear + 1 : baseYear;
            LocalDate d = LocalDate.of(nextYear, nextMonth, day);
            if (!d.isBefore(range[0]) && !d.isAfter(range[1])) {
                return d;
            }
        } catch (Exception ignored) { /* invalid day in month */ }

        return null;
    }


    private static LocalDate[] extractDateRange(List<CSVRecord> rows) {
        LocalDate now = LocalDate.now();
        LocalDate[] range = { now.withDayOfMonth(1), now };

        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            for (String cell : rows.get(i)) {
                if (cell != null && cell.contains("~")) {
                    Matcher m = DATE_RANGE_PATTERN.matcher(cell);
                    if (m.find()) {
                        try {
                            range[0] = LocalDate.parse(m.group(1));
                            range[1] = LocalDate.parse(m.group(2));
                            return range;
                        } catch (Exception ignored) { /* keep default */ }
                    }
                }
            }
        }
        return range;
    }


    private static void detectAnomalies(AttendanceRecord rec) {
        if (rec.isAbsent()) return;

        List<String> anomalies = new ArrayList<>();


        if (rec.getTimeIn1() == null && rec.getTimeOut2() != null) {
            anomalies.add("Missing morning clock-in");
        }

        if (rec.getTimeIn1() != null && rec.getTimeOut2() == null) {
            anomalies.add("Missing clock-out");
        }

        if ((rec.getTimeOut1() == null) != (rec.getTimeIn2() == null)) {
            anomalies.add("Incomplete lunch break punches");
        }

        if (rec.getTimeIn1() != null && rec.getTimeOut1() != null
                && rec.getTimeOut1().isBefore(rec.getTimeIn1())) {
            anomalies.add("Lunch-out before clock-in");
        }
        if (rec.getTimeIn2() != null && rec.getTimeOut2() != null
                && rec.getTimeOut2().isBefore(rec.getTimeIn2())) {
            anomalies.add("Clock-out before afternoon clock-in");
        }

        if (rec.getTimeIn1() != null && rec.getTimeOut2() != null) {
            long mins = java.time.Duration.between(rec.getTimeIn1(), rec.getTimeOut2()).toMinutes();
            if (mins > 0 && mins < 240) {
                anomalies.add("Very short shift (" + (mins / 60) + "h " + (mins % 60) + "m)");
            }
        }

        if (!anomalies.isEmpty()) {
            rec.setHasAnomaly(true);
            rec.setAnomalyDescription(String.join("; ", anomalies));
        }
    }



    private static String safeGet(CSVRecord row, int index) {
        if (index >= row.size()) return null;
        String v = row.get(index).trim();
        return (v.isEmpty() || MISSED.equalsIgnoreCase(v)) ? null : v;
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        Matcher m = TIME_PATTERN.matcher(s);
        if (m.find()) {
            try {
                return LocalTime.of(Integer.parseInt(m.group(1)),
                                    Integer.parseInt(m.group(2)));
            } catch (Exception ignored) { /* invalid */ }
        }
        return null;
    }


    private static class EmployeeBlock {
        final int       blockIndex;
        final String    employeeCode;
        final String    employeeName;
        final int       colOffset;
        final LocalDate[] dateRange;

        EmployeeBlock(int blockIndex, String empCode, String empName,
                      int colOffset, LocalDate[] dateRange) {
            this.blockIndex   = blockIndex;
            this.employeeCode = empCode;
            this.employeeName = empName;
            this.colOffset    = colOffset;
            this.dateRange    = dateRange;
        }
    }
}
