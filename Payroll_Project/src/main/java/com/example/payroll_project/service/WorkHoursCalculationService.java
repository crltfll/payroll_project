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
 * FIX: When stored regularHours/overtimeHours are 0 (which is always the case
 * after FA2000 CSV import — the DAO saves 0 for calculated fields), fall back
 * to computing hours directly from the raw time punches (timeIn1, timeOut2, etc.)
 * This mirrors the logic already used in AttendanceController and ReportsController.
 */
public class WorkHoursCalculationService {

    private static final BigDecimal EIGHT            = new BigDecimal("8");
    private static final BigDecimal SIXTY            = new BigDecimal("60");
    private static final LocalTime  NIGHT_DIFF_START = LocalTime.of(22, 0);  // 10 PM
    private static final LocalTime  NIGHT_DIFF_END   = LocalTime.of(6, 0);   // 6 AM
    private static final LocalTime  DEFAULT_START    = LocalTime.of(8, 0);   // expected start

    public WorkHoursSummary calculate(List<AttendanceRecord> records, Employee employee) {
        WorkHoursSummary summary = new WorkHoursSummary();

        for (AttendanceRecord rec : records) {

            // Skip on-leave records — they don't contribute raw hours
            if (rec.isOnLeave()) continue;

            if (rec.isAbsent()) {
                summary.daysAbsent++;
                continue;
            }

            // Need at least a clock-in to count as worked
            if (rec.getTimeIn1() == null) continue;

            summary.daysWorked++;

            // ── Determine total worked minutes ────────────────────────────
            // Prefer stored calculated values if they are non-zero.
            // After a CSV import they will always be 0, so we fall back to
            // computing from the raw punch times.
            long rawMinutes = computeRawMinutes(rec);
            if (rawMinutes <= 0) continue;

            int lateMinutes = computeLateMinutes(rec.getTimeIn1());
            summary.totalLateMinutes += lateMinutes;

            int undertimeMinutes = computeUndertimeMinutes(rec.getTimeOut2(), rec.isHoliday());
            summary.totalUndertimeMinutes += undertimeMinutes;

            double totalHours = rawMinutes / 60.0;

            if (rec.isHoliday() || rec.isRestDay()) {
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

            double ndHours = computeNightDiffHours(rec);
            summary.totalNightDiffHours = summary.totalNightDiffHours.add(bd(ndHours));
        }

        return summary;
    }

    // ── Raw minute calculation ─────────────────────────────────────────────

    /**
     * Computes net worked minutes from punch times.
     *
     * Priority:
     *  1. If stored regularHours + overtimeHours > 0, convert those to minutes
     *     (avoids re-computing when data was already validated and saved).
     *  2. Otherwise derive from timeIn1 / timeOut2, subtracting lunch break
     *     (timeOut1→timeIn2) or deducting a flat 60-minute lunch when no
     *     lunch punches are present and the shift exceeds 5 hours.
     */
    private long computeRawMinutes(AttendanceRecord rec) {

        // ── Priority 1: use stored calculated hours if present ────────────
        BigDecimal storedReg = rec.getRegularHours();
        BigDecimal storedOt  = rec.getOvertimeHours();
        if (storedReg != null && storedOt != null) {
            double storedTotal = storedReg.doubleValue() + storedOt.doubleValue();
            if (storedTotal > 0) {
                return Math.round(storedTotal * 60);
            }
        }

        // ── Priority 2: derive from punch times ───────────────────────────
        LocalTime start = rec.getTimeIn1();
        LocalTime end   = rec.getTimeOut2();
        if (start == null || end == null) return 0;

        long total = Duration.between(start, end).toMinutes();
        if (total <= 0) return 0;   // overnight edge-case guard

        // Subtract actual lunch break if both lunch punches exist
        if (rec.getTimeOut1() != null && rec.getTimeIn2() != null) {
            long lunch = Duration.between(rec.getTimeOut1(), rec.getTimeIn2()).toMinutes();
            total -= Math.max(0, lunch);
        } else if (total > 300) {
            // No lunch punches — deduct a standard 1-hour break for shifts > 5 h
            total -= 60;
        }

        return Math.max(0, total);
    }

    // ── Late / undertime ───────────────────────────────────────────────────

    private int computeLateMinutes(LocalTime timeIn) {
        if (timeIn == null) return 0;
        if (timeIn.isAfter(DEFAULT_START)) {
            return (int) Duration.between(DEFAULT_START, timeIn).toMinutes();
        }
        return 0;
    }

    private int computeUndertimeMinutes(LocalTime timeOut, boolean holiday) {
        if (timeOut == null || holiday) return 0;
        LocalTime expected = LocalTime.of(17, 0);
        if (timeOut.isBefore(expected)) {
            return (int) Duration.between(timeOut, expected).toMinutes();
        }
        return 0;
    }

    // ── Night differential ─────────────────────────────────────────────────

    private double computeNightDiffHours(AttendanceRecord rec) {
        LocalTime start = rec.getTimeIn1();
        LocalTime end   = rec.getTimeOut2();
        if (start == null || end == null) return 0;

        double nightMins = 0;

        // Portion after 10 PM on the same day
        if (end.isAfter(NIGHT_DIFF_START)) {
            LocalTime ndStart = start.isAfter(NIGHT_DIFF_START) ? start : NIGHT_DIFF_START;
            nightMins += Duration.between(ndStart, end).toMinutes();
        }

        // Portion before 6 AM (overnight scenario)
        if (start.isBefore(NIGHT_DIFF_END)) {
            LocalTime ndEnd = end.isBefore(NIGHT_DIFF_END) ? end : NIGHT_DIFF_END;
            nightMins += Duration.between(start, ndEnd).toMinutes();
        }

        return Math.max(0, nightMins / 60.0);
    }

    // ── Utility ────────────────────────────────────────────────────────────

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Summary DTO ────────────────────────────────────────────────────────

    public static class WorkHoursSummary {
        public BigDecimal totalRegularHours   = BigDecimal.ZERO;
        public BigDecimal totalOvertimeHours  = BigDecimal.ZERO;
        public BigDecimal totalNightDiffHours = BigDecimal.ZERO;
        public BigDecimal totalHolidayHours   = BigDecimal.ZERO;
        public int daysWorked            = 0;
        public int daysAbsent            = 0;
        public int totalLateMinutes      = 0;
        public int totalUndertimeMinutes = 0;
    }
}
