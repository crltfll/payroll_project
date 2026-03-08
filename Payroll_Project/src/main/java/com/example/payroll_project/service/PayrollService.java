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
 * Orchestrates:
 *   1. Work hours calculation    (CR2)
 *   2. Gross pay computation     (CR4)
 *   3. Statutory deductions      (CR3)
 *   4. Net pay derivation        (CR4)
 *   5. Transparency details      (F1)
 */
public class PayrollService {

    private static final Logger logger = LoggerFactory.getLogger(PayrollService.class);


    private static final BigDecimal OT_RATE_REGULAR     = new BigDecimal("1.25");
    private static final BigDecimal OT_RATE_REST_DAY    = new BigDecimal("1.30");
    private static final BigDecimal OT_RATE_HOLIDAY     = new BigDecimal("2.00");
    private static final BigDecimal NIGHT_DIFF_RATE     = new BigDecimal("1.10");
    private static final BigDecimal HOLIDAY_RATE        = new BigDecimal("2.00");
    private static final BigDecimal SPECIAL_HOLIDAY_RATE = new BigDecimal("1.30");

    private final WorkHoursCalculationService hoursService  = new WorkHoursCalculationService();
    private final SSSCalculationService       sss           = new SSSCalculationService();
    private final PhilHealthCalculationService philHealth   = new PhilHealthCalculationService();
    private final PagIBIGCalculationService   pagIbig       = new PagIBIGCalculationService();
    private final BIRTaxCalculationService    bir           = new BIRTaxCalculationService();


    public PayrollRecord compute(Employee employee, PayPeriod payPeriod,
                                  List<AttendanceRecord> attendance) {

        PayrollRecord pr = new PayrollRecord(payPeriod.getPayPeriodId(),
                                              employee.getEmployeeId());

        WorkHoursCalculationService.WorkHoursSummary hours =
                hoursService.calculate(attendance, employee);

        pr.setTotalRegularHours(hours.totalRegularHours);
        pr.setTotalOvertimeHours(hours.totalOvertimeHours);
        pr.setTotalNightDiffHours(hours.totalNightDiffHours);
        pr.setTotalHolidayHours(hours.totalHolidayHours);
        pr.setDaysWorked(hours.daysWorked);
        pr.setDaysAbsent(hours.daysAbsent);
        pr.setTotalLateMinutes(hours.totalLateMinutes);
        pr.setTotalUndertimeMinutes(hours.totalUndertimeMinutes);

        BigDecimal monthlyRate = toMonthlySalary(employee, payPeriod);

        BigDecimal dailyRate  = toDailyRate(employee);
        BigDecimal hourlyRate = dailyRate.divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP);

        BigDecimal basicPay = hours.totalRegularHours
                .multiply(hourlyRate)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setBasicPay(basicPay);

        BigDecimal otPay = hours.totalOvertimeHours
                .multiply(hourlyRate)
                .multiply(OT_RATE_REGULAR)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setOvertimePay(otPay);

        BigDecimal ndExtra = hours.totalNightDiffHours
                .multiply(hourlyRate)
                .multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);
        pr.setNightDiffPay(ndExtra);

        BigDecimal holPay = hours.totalHolidayHours
                .multiply(hourlyRate)
                .multiply(HOLIDAY_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        pr.setHolidayPay(holPay);

        BigDecimal grossPay = basicPay.add(otPay).add(ndExtra).add(holPay)
                .add(pr.getTotalAllowances());
        pr.setGrossPay(grossPay);

        BigDecimal sssContrib       = sss.calculateEmployeeContribution(monthlyRate);
        BigDecimal philHealthContrib = philHealth.calculateEmployeeContribution(monthlyRate);
        BigDecimal pagIbigContrib   = pagIbig.calculateEmployeeContribution(monthlyRate);

        pr.setSssContribution(sssContrib);
        pr.setPhilhealthContribution(philHealthContrib);
        pr.setPagibigContribution(pagIbigContrib);

        BigDecimal taxableIncome = grossPay
                .subtract(pr.getNonTaxableAllowances())
                .subtract(sssContrib)
                .subtract(philHealthContrib)
                .subtract(pagIbigContrib)
                .max(BigDecimal.ZERO);

        BigDecimal withholdingTax = bir.calculateMonthlyTax(taxableIncome);
        pr.setWithholdingTax(withholdingTax);

        BigDecimal totalDeductions = sssContrib
                .add(philHealthContrib)
                .add(pagIbigContrib)
                .add(withholdingTax)
                .add(pr.getTotalOtherDeductions());
        pr.setTotalDeductions(totalDeductions);
        pr.setNetPay(grossPay.subtract(totalDeductions).max(BigDecimal.ZERO));

        pr.setComputationDetails(buildTransparencyDetails(
                employee, hourlyRate, hours, pr, taxableIncome, monthlyRate));

        logger.info("Payroll computed for employee {}: gross={}, net={}",
                employee.getEmployeeCode(), pr.getGrossPay(), pr.getNetPay());

        return pr;
    }

    private BigDecimal toMonthlySalary(Employee emp, PayPeriod period) {
        BigDecimal rate = emp.getBaseRate();
        if (rate == null) return BigDecimal.ZERO;

        return switch (emp.getRateType()) {
            case MONTHLY -> rate;
            case DAILY   -> rate.multiply(new BigDecimal("26")); // 26 working days/month
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

    private String buildTransparencyDetails(
            Employee emp,
            BigDecimal hourlyRate,
            WorkHoursCalculationService.WorkHoursSummary hours,
            PayrollRecord pr,
            BigDecimal taxableIncome,
            BigDecimal monthlyRate) {

        return "=== Payroll Computation Breakdown ===\n"
             + String.format("Employee       : %s - %s\n", emp.getEmployeeCode(), emp.getFullName())
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
             + "\n--- Deductions ---\n"
             + sss.getComputationDetails(monthlyRate) + "\n"
             + philHealth.getComputationDetails(monthlyRate) + "\n"
             + pagIbig.getComputationDetails(monthlyRate) + "\n"
             + bir.getComputationDetails(taxableIncome) + "\n"
             + String.format("Total Deductions: ₱%,.2f\n", pr.getTotalDeductions())
             + "\n--- Summary ---\n"
             + String.format("Gross Pay  : ₱%,.2f\n", pr.getGrossPay())
             + String.format("Deductions : ₱%,.2f\n", pr.getTotalDeductions())
             + String.format("NET PAY    : ₱%,.2f\n", pr.getNetPay());
    }
}
