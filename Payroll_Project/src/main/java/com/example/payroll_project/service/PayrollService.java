package com.example.payroll_project.service;

import com.example.payroll_project.model.AttendanceRecord;
import com.example.payroll_project.model.Employee;
import com.example.payroll_project.model.PayPeriod;
import com.example.payroll_project.model.PayrollRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Payroll Computation Service (CR4)
 *
 * KEY RULES (Philippine Labor Law):
 *  ┌─────────────────────────────────────────────────────────────────┐
 *  │  SSS / PhilHealth / Pag-IBIG  →  based on MONTHLY BASIC SALARY │
 *  │  NOT on gross pay (OT / holiday / allowances excluded)          │
 *  │  For semi-monthly periods the monthly contribution is halved.   │
 *  │                                                                 │
 *  │  BIR withholding tax  →  computed on TAXABLE INCOME per period  │
 *  │    = gross pay − non-taxable allowances − statutory deductions  │
 *  │  Annualisation uses ×12 (monthly) or ×24 (semi-monthly).       │
 *  │                                                                 │
 *  │  FIX: If gross pay is ₱0.00 (no hours worked / no attendance), │
 *  │  all deductions are ₱0.00. There is nothing to deduct from.    │
 *  │  Statutory deductions are also capped so net pay ≥ ₱0.00.      │
 *  └─────────────────────────────────────────────────────────────────┘
 */
public class PayrollService {

    private static final Logger logger = LoggerFactory.getLogger(PayrollService.class);

    // Philippine Labor Code premium rates
    private static final BigDecimal OT_RATE_REGULAR  = new BigDecimal("1.25");
    private static final BigDecimal OT_RATE_REST_DAY = new BigDecimal("1.30");
    private static final BigDecimal OT_RATE_HOLIDAY  = new BigDecimal("2.00");
    private static final BigDecimal HOLIDAY_RATE      = new BigDecimal("2.00");

    private final WorkHoursCalculationService hoursService = new WorkHoursCalculationService();
    private final SSSCalculationService       sss          = new SSSCalculationService();
    private final PhilHealthCalculationService philHealth  = new PhilHealthCalculationService();
    private final PagIBIGCalculationService   pagIbig      = new PagIBIGCalculationService();
    private final BIRTaxCalculationService    bir          = new BIRTaxCalculationService();

    // ── Period type detection ──────────────────────────────────────────────

    enum PeriodType { WEEKLY, SEMI_MONTHLY, MONTHLY }

    PeriodType detectPeriodType(PayPeriod period) {
        long days = ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        if (days <= 10)  return PeriodType.WEEKLY;
        if (days <= 21)  return PeriodType.SEMI_MONTHLY;
        return PeriodType.MONTHLY;
    }

    private BigDecimal contributionDivisor(PeriodType type) {
        return switch (type) {
            case WEEKLY       -> new BigDecimal("4");
            case SEMI_MONTHLY -> new BigDecimal("2");
            case MONTHLY      -> BigDecimal.ONE;
        };
    }

    // ── Main computation ───────────────────────────────────────────────────

    public PayrollRecord compute(Employee employee,
                                  PayPeriod payPeriod,
                                  List<AttendanceRecord> attendance) {

        PayrollRecord pr = new PayrollRecord(
                payPeriod.getPayPeriodId(), employee.getEmployeeId());

        // ── 1. Work hours summary ──────────────────────────────────────────
        WorkHoursCalculationService.WorkHoursSummary hours =
                hoursService.calculate(attendance, employee);

        pr.setTotalRegularHours  (hours.totalRegularHours);
        pr.setTotalOvertimeHours (hours.totalOvertimeHours);
        pr.setTotalNightDiffHours(hours.totalNightDiffHours);
        pr.setTotalHolidayHours  (hours.totalHolidayHours);
        pr.setDaysWorked         (hours.daysWorked);
        pr.setDaysAbsent         (hours.daysAbsent);
        pr.setTotalLateMinutes   (hours.totalLateMinutes);
        pr.setTotalUndertimeMinutes(hours.totalUndertimeMinutes);

        // ── 2. Rate derivation ─────────────────────────────────────────────
        BigDecimal monthlyRate = toMonthlySalary(employee);
        BigDecimal dailyRate   = toDailyRate(employee);
        BigDecimal hourlyRate  = dailyRate.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);

        // ── 3. Gross pay components ────────────────────────────────────────
        BigDecimal basicPay = hours.totalRegularHours
                .multiply(hourlyRate)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setBasicPay(basicPay);

        BigDecimal otPay = hours.totalOvertimeHours
                .multiply(hourlyRate).multiply(OT_RATE_REGULAR)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setOvertimePay(otPay);

        BigDecimal ndExtra = hours.totalNightDiffHours
                .multiply(hourlyRate).multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
        pr.setNightDiffPay(ndExtra);

        BigDecimal holPay = hours.totalHolidayHours
                .multiply(hourlyRate).multiply(HOLIDAY_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setHolidayPay(holPay);

        BigDecimal grossPay = basicPay.add(otPay).add(ndExtra).add(holPay)
                .add(pr.getTotalAllowances());
        pr.setGrossPay(grossPay);

        // ── 4. Statutory deductions ────────────────────────────────────────
        // FIX: If the employee earned nothing this period, skip all deductions.
        // There is no income to deduct from — applying deductions to ₱0 gross
        // is both legally incorrect and produces nonsensical negative net pay.
        PeriodType periodType = detectPeriodType(payPeriod);
        BigDecimal divisor    = contributionDivisor(periodType);

        BigDecimal sssContrib;
        BigDecimal philHealthContrib;
        BigDecimal pagIbigContrib;
        BigDecimal withholdingTax;

        if (grossPay.compareTo(BigDecimal.ZERO) <= 0) {
            // No earnings → no deductions
            sssContrib       = BigDecimal.ZERO;
            philHealthContrib = BigDecimal.ZERO;
            pagIbigContrib   = BigDecimal.ZERO;
            withholdingTax   = BigDecimal.ZERO;
        } else {
            // Normal case: compute statutory deductions on monthly basic salary,
            // prorated by period type
            sssContrib = sss.calculateEmployeeContribution(monthlyRate)
                            .divide(divisor, 2, RoundingMode.HALF_UP);
            philHealthContrib = philHealth.calculateEmployeeContribution(monthlyRate)
                                          .divide(divisor, 2, RoundingMode.HALF_UP);
            pagIbigContrib = pagIbig.calculateEmployeeContribution(monthlyRate)
                                    .divide(divisor, 2, RoundingMode.HALF_UP);

            // ── 5. Withholding tax ─────────────────────────────────────────
            // Taxable income = gross − non-taxable allowances − mandatory deductions
            BigDecimal taxableIncome = grossPay
                    .subtract(pr.getNonTaxableAllowances())
                    .subtract(sssContrib)
                    .subtract(philHealthContrib)
                    .subtract(pagIbigContrib)
                    .max(BigDecimal.ZERO);

            withholdingTax = switch (periodType) {
                case SEMI_MONTHLY -> bir.calculateSemiMonthlyTax(taxableIncome);
                case WEEKLY       -> bir.calculateWeeklyTax(taxableIncome);
                case MONTHLY      -> bir.calculateMonthlyTax(taxableIncome);
            };

            // Cap total deductions so they never exceed gross pay
            // (e.g. employee worked only a few days in a monthly period)
            BigDecimal totalStatutory = sssContrib
                    .add(philHealthContrib)
                    .add(pagIbigContrib)
                    .add(withholdingTax)
                    .add(pr.getTotalOtherDeductions());

            if (totalStatutory.compareTo(grossPay) > 0) {
                // Scale down proportionally so net pay = ₱0 rather than negative
                BigDecimal scale = grossPay.divide(totalStatutory, 4, RoundingMode.HALF_UP);
                sssContrib        = sssContrib.multiply(scale).setScale(2, RoundingMode.HALF_UP);
                philHealthContrib = philHealthContrib.multiply(scale).setScale(2, RoundingMode.HALF_UP);
                pagIbigContrib    = pagIbigContrib.multiply(scale).setScale(2, RoundingMode.HALF_UP);
                withholdingTax    = withholdingTax.multiply(scale).setScale(2, RoundingMode.HALF_UP);
            }
        }

        pr.setSssContribution         (sssContrib);
        pr.setPhilhealthContribution  (philHealthContrib);
        pr.setPagibigContribution     (pagIbigContrib);
        pr.setWithholdingTax          (withholdingTax);

        // ── 6. Net pay ─────────────────────────────────────────────────────
        BigDecimal taxableIncomeFinal = grossPay
                .subtract(pr.getNonTaxableAllowances())
                .subtract(sssContrib)
                .subtract(philHealthContrib)
                .subtract(pagIbigContrib)
                .max(BigDecimal.ZERO);

        BigDecimal totalDeductions = sssContrib
                .add(philHealthContrib)
                .add(pagIbigContrib)
                .add(withholdingTax)
                .add(pr.getTotalOtherDeductions());
        pr.setTotalDeductions(totalDeductions);
        pr.setNetPay(grossPay.subtract(totalDeductions).max(BigDecimal.ZERO));

        pr.setComputationDetails(buildTransparencyDetails(
                employee, hourlyRate, hours, pr, taxableIncomeFinal,
                monthlyRate, periodType, divisor, grossPay));

        logger.info("Payroll [{}] computed for {}: period={}, gross={}, deductions={}, net={}",
                periodType, employee.getEmployeeCode(),
                payPeriod.getPeriodName(), pr.getGrossPay(),
                pr.getTotalDeductions(), pr.getNetPay());

        return pr;
    }

    // ── Rate helpers ───────────────────────────────────────────────────────

    private BigDecimal toMonthlySalary(Employee emp) {
        BigDecimal rate = emp.getBaseRate();
        if (rate == null) return BigDecimal.ZERO;
        return switch (emp.getRateType()) {
            case MONTHLY -> rate;
            case DAILY   -> rate.multiply(new BigDecimal("26"));
            case HOURLY  -> rate.multiply(new BigDecimal("8")).multiply(new BigDecimal("26"));
        };
    }

    private BigDecimal toDailyRate(Employee emp) {
        BigDecimal rate = emp.getBaseRate();
        if (rate == null) return BigDecimal.ZERO;
        return switch (emp.getRateType()) {
            case MONTHLY -> rate.divide(new BigDecimal("26"), 4, RoundingMode.HALF_UP);
            case DAILY   -> rate;
            case HOURLY  -> rate.multiply(new BigDecimal("8"));
        };
    }

    // ── Transparency text ──────────────────────────────────────────────────

    private String buildTransparencyDetails(
            Employee emp,
            BigDecimal hourlyRate,
            WorkHoursCalculationService.WorkHoursSummary hours,
            PayrollRecord pr,
            BigDecimal taxableIncome,
            BigDecimal monthlyRate,
            PeriodType periodType,
            BigDecimal divisor,
            BigDecimal grossPay) {

        String periodLabel = switch (periodType) {
            case MONTHLY      -> "Monthly";
            case SEMI_MONTHLY -> "Semi-Monthly (contributions ÷ 2)";
            case WEEKLY       -> "Weekly (contributions ÷ 4)";
        };

        String deductionNote = grossPay.compareTo(BigDecimal.ZERO) <= 0
                ? "\n⚠️  No earnings this period — all deductions set to ₱0.00\n"
                : String.format(
                    "\nNOTE: Monthly contributions are prorated by period type (÷%s)\n",
                    divisor.toPlainString());

        return "=== Payroll Computation Breakdown ===\n"
             + String.format("Employee       : %s - %s\n", emp.getEmployeeCode(), emp.getFullName())
             + String.format("Pay Period Type: %s\n", periodLabel)
             + String.format("Hourly Rate    : ₱%,.4f\n", hourlyRate)
             + "\n--- Earnings ---\n"
             + String.format("Regular Hours  : %s hrs × ₱%,.4f = ₱%,.2f\n",
                   hours.totalRegularHours, hourlyRate, pr.getBasicPay())
             + String.format("Overtime Hours : %s hrs × ₱%,.4f × 1.25 = ₱%,.2f\n",
                   hours.totalOvertimeHours, hourlyRate, pr.getOvertimePay())
             + String.format("Night Diff Hrs : %s hrs × ₱%,.4f × 10%% = ₱%,.2f\n",
                   hours.totalNightDiffHours, hourlyRate, pr.getNightDiffPay())
             + String.format("Holiday Hours  : %s hrs × ₱%,.4f × 2.00 = ₱%,.2f\n",
                   hours.totalHolidayHours, hourlyRate, pr.getHolidayPay())
             + String.format("Gross Pay      : ₱%,.2f\n", pr.getGrossPay())
             + String.format("\n--- Statutory Deductions (based on Monthly Basic Salary ₱%,.2f) ---\n",
                   monthlyRate)
             + deductionNote
             + sss.getComputationDetails(monthlyRate) + " ÷" + divisor.toPlainString()
                   + " = ₱" + String.format("%,.2f", pr.getSssContribution()) + "\n"
             + philHealth.getComputationDetails(monthlyRate) + " ÷" + divisor.toPlainString()
                   + " = ₱" + String.format("%,.2f", pr.getPhilhealthContribution()) + "\n"
             + pagIbig.getComputationDetails(monthlyRate) + " ÷" + divisor.toPlainString()
                   + " = ₱" + String.format("%,.2f", pr.getPagibigContribution()) + "\n"
             + "\n--- Withholding Tax (BIR TRAIN Law) ---\n"
             + String.format("Taxable Income : Gross ₱%,.2f − Non-Taxable Allowances ₱%,.2f\n",
                   pr.getGrossPay(), pr.getNonTaxableAllowances())
             + String.format("               − SSS ₱%,.2f − PhilHealth ₱%,.2f − Pag-IBIG ₱%,.2f\n",
                   pr.getSssContribution(), pr.getPhilhealthContribution(), pr.getPagibigContribution())
             + String.format("               = ₱%,.2f\n", taxableIncome)
             + bir.getComputationDetails(taxableIncome) + " [" + periodLabel + "]\n"
             + String.format("Total Deductions: ₱%,.2f\n", pr.getTotalDeductions())
             + "\n--- Summary ---\n"
             + String.format("Gross Pay  : ₱%,.2f\n", pr.getGrossPay())
             + String.format("Deductions : ₱%,.2f\n", pr.getTotalDeductions())
             + String.format("NET PAY    : ₱%,.2f\n", pr.getNetPay());
    }
}
