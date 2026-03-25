package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PhilHealth Contribution Calculation Service (CR3.2)
 * Based on RA 11223 (Universal Health Care Law) – 2025–2026 rate.
 *
 */
public class PhilHealthCalculationService {

    private static final BigDecimal EMPLOYEE_RATE  = new BigDecimal("0.025");
    private static final BigDecimal EMPLOYER_RATE  = new BigDecimal("0.025");
    private static final BigDecimal SALARY_FLOOR   = new BigDecimal("10000");
    private static final BigDecimal SALARY_CEILING = new BigDecimal("100000");


    public BigDecimal calculateEmployeeContribution(BigDecimal monthlySalary) {
        BigDecimal base = effectiveBase(monthlySalary);
        return base.multiply(EMPLOYEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateEmployerContribution(BigDecimal monthlySalary) {
        BigDecimal base = effectiveBase(monthlySalary);
        return base.multiply(EMPLOYER_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveBase(BigDecimal salary) {
        if (salary == null || salary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (salary.compareTo(SALARY_FLOOR) < 0) return SALARY_FLOOR;
        if (salary.compareTo(SALARY_CEILING) > 0) return SALARY_CEILING;
        return salary;
    }

    public String getComputationDetails(BigDecimal monthlySalary) {
        BigDecimal base         = effectiveBase(monthlySalary);
        BigDecimal contribution = calculateEmployeeContribution(monthlySalary);
        return String.format("PhilHealth: ₱%,.2f × 2.5%% = ₱%,.2f", base, contribution);
    }
}
