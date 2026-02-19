package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PhilHealth Contribution Calculation Service (CR3.2)
 * Based on RA 11223 (Universal Health Care Law) – 2025–2026 rate.
 *
 * Premium rate : 5.0% of basic monthly salary
 * Employee share: 2.5%  |  Employer share: 2.5%
 * Salary floor  : ₱10,000  (minimum contribution base)
 * Salary ceiling: ₱100,000 (maximum contribution base)
 */
public class PhilHealthCalculationService {

    private static final BigDecimal PREMIUM_RATE   = new BigDecimal("0.05");
    private static final BigDecimal EMPLOYEE_RATE  = new BigDecimal("0.025");
    private static final BigDecimal EMPLOYER_RATE  = new BigDecimal("0.025");
    private static final BigDecimal SALARY_FLOOR   = new BigDecimal("10000");
    private static final BigDecimal SALARY_CEILING = new BigDecimal("100000");

    // -----------------------------------------------------------------------

    /**
     * Employee's share of PhilHealth premium (2.5%).
     */
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
            return SALARY_FLOOR;
        }
        if (salary.compareTo(SALARY_FLOOR) < 0) return SALARY_FLOOR;
        if (salary.compareTo(SALARY_CEILING) > 0) return SALARY_CEILING;
        return salary;
    }

    /** Transparency detail string (F1) */
    public String getComputationDetails(BigDecimal monthlySalary) {
        BigDecimal base         = effectiveBase(monthlySalary);
        BigDecimal contribution = calculateEmployeeContribution(monthlySalary);
        return String.format("PhilHealth: ₱%,.2f × 2.5%% = ₱%,.2f", base, contribution);
    }
}
