package com.example.payroll_project.service;

import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.model.Employee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * Work Hours Calculation Service (CR2)
 *
 * Philippine Labor Standards:
 *   Regular work day: 8 hours
 *   Regular work week: 48 hours (8 hrs × 6 days)
 *   Overtime: hours beyond 8 hrs/day (×1.25 regular; ×1.30 rest day; ×1.30 special holiday; ×2.0 regular holiday)
 *   Night differential: work between 10 PM – 6 AM (10% premium)
 *   Default shift: 08:00 – 17:00 with 1 hr lunch = 8 regular hrs
 *
 * This service computes totals across all attendance records in a pay period.
 */
public class WorkHoursCalculationService {

    private static final BigDecimal EIGHT              = new BigDecimal("8");
    private static final BigDecimal SIXTY              = new BigDecimal("60");
    private static final LocalTime  NIGHT_DIFF_START   = LocalTime.of(22, 0);  // 10 PM
    private static final LocalTime  NIGHT_DIFF_END     = LocalTime.of(6, 0);   // 6 AM (next day)
    private static final LocalTime  DEFAULT_START      = LocalTime.of(8, 0);   // expected start

    // -----------------------------------------------------------------------

    /**
     * Calculate all hour-related totals for a list of attendance records.
     */
    public WorkHoursSummary calculate(List<AttendanceRecord> records, Employee employee) {
        WorkHoursSummary summary = new WorkHoursSummary();

        for (AttendanceRecord rec : records) {
            if (rec.isAbsent()) {
                summary.daysAbsent++;
                continue;
            }
            if (rec.getTimeIn1() == null) continue; // missing punch – skip

            summary.daysWorked++;

            // Total raw minutes worked
            long rawMinutes = computeRawMinutes(rec);
            if (rawMinutes <= 0) continue;

            // Late minutes (compared to expected start 08:00)
            int lateMinutes = computeLateMinutes(rec.getTimeIn1());
            summary.totalLateMinutes += lateMinutes;

            // Undertime (clock-out before expected end)
            int undertimeMinutes = computeUndertimeMinutes(rec.getTimeOut2(), rec.isHoliday());
            summary.totalUndertimeMinutes += undertimeMinutes;

            // Convert raw minutes to hours
            double totalHours = rawMinutes / 60.0;

            if (rec.isHoliday() || rec.isRestDay()) {
                // All hours are holiday / rest-day hours
                double holidayHrs = Math.min(totalHours, 8.0);
                double otHrs      = Math.max(0, totalHours - 8.0);
                summary.totalHolidayHours  = summary.totalHolidayHours.add(bd(holidayHrs));
                summary.totalOvertimeHours = summary.totalOvertimeHours.add(bd(otHrs));
            } else {
                double regularHrs = Math.min(totalHours, 8.0);
                double otHrs      = Math.max(0, totalHours - 8.0);
                summary.totalRegularHours  = summary.totalRegularHours.add(bd(regularHrs));
                summary.totalOvertimeHours = summary.totalOvertimeHours.add(bd(otHrs));
            }

            // Night differential hours
            double ndHours = computeNightDiffHours(rec);
            summary.totalNightDiffHours = summary.totalNightDiffHours.add(bd(ndHours));
        }

        return summary;
    }

    // -----------------------------------------------------------------------

    /** Total worked minutes (subtracts lunch break if both punches exist). */
    private long computeRawMinutes(AttendanceRecord rec) {
        LocalTime start = rec.getTimeIn1();
        LocalTime end   = rec.getTimeOut2();
        if (start == null || end == null) return 0;

        long total = Duration.between(start, end).toMinutes();
        if (total < 0) return 0; // overnight – edge case

        // Subtract recorded lunch break
        if (rec.getTimeOut1() != null && rec.getTimeIn2() != null) {
            long lunch = Duration.between(rec.getTimeOut1(), rec.getTimeIn2()).toMinutes();
            total -= Math.max(0, lunch);
        } else {
            // Deduct default 1-hr (60 min) lunch if shift > 5 hours and no punch recorded
            if (total > 300) total -= 60;
        }

        return Math.max(0, total);
    }

    private int computeLateMinutes(LocalTime timeIn) {
        if (timeIn == null) return 0;
        if (timeIn.isAfter(DEFAULT_START)) {
            return (int) Duration.between(DEFAULT_START, timeIn).toMinutes();
        }
        return 0;
    }

    /** Standard end is 17:00; undertime if employee left before that. */
    private int computeUndertimeMinutes(LocalTime timeOut, boolean holiday) {
        if (timeOut == null || holiday) return 0;
        LocalTime expected = LocalTime.of(17, 0);
        if (timeOut.isBefore(expected)) {
            return (int) Duration.between(timeOut, expected).toMinutes();
        }
        return 0;
    }

    /** Count minutes worked between 22:00 and 06:00 the next morning. */
    private double computeNightDiffHours(AttendanceRecord rec) {
        LocalTime start = rec.getTimeIn1();
        LocalTime end   = rec.getTimeOut2();
        if (start == null || end == null) return 0;

        double nightMins = 0;
        // Walk through the shift minute by minute (simplified: check if end crosses midnight)
        // Simple heuristic: if end time is past 22:00, count from 22:00 to end
        if (end.isAfter(NIGHT_DIFF_START) || end.isBefore(NIGHT_DIFF_END)) {
            LocalTime ndStart = start.isAfter(NIGHT_DIFF_START) ? start : NIGHT_DIFF_START;
            if (end.isAfter(NIGHT_DIFF_START)) {
                nightMins += Duration.between(ndStart, end).toMinutes();
            }
        }
        // If start is before 06:00 (early morning – crosses midnight)
        if (start.isBefore(NIGHT_DIFF_END)) {
            LocalTime ndEnd = end.isBefore(NIGHT_DIFF_END) ? end : NIGHT_DIFF_END;
            nightMins += Duration.between(start, ndEnd).toMinutes();
        }

        return Math.max(0, nightMins / 60.0);
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // Summary bean
    // -----------------------------------------------------------------------

    public static class WorkHoursSummary {
        public BigDecimal totalRegularHours  = BigDecimal.ZERO;
        public BigDecimal totalOvertimeHours = BigDecimal.ZERO;
        public BigDecimal totalNightDiffHours = BigDecimal.ZERO;
        public BigDecimal totalHolidayHours  = BigDecimal.ZERO;
        public int daysWorked        = 0;
        public int daysAbsent        = 0;
        public int totalLateMinutes  = 0;
        public int totalUndertimeMinutes = 0;
    }
}
