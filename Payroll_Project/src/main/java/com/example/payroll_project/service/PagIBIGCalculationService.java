package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * HDMF / Pag-IBIG Contribution Calculation Service (CR3.3)
 * Based on HDMF Circular No. 460.
 *
 */
public class PagIBIGCalculationService {

    private static final BigDecimal EMPLOYEE_RATE   = new BigDecimal("0.02");
    private static final BigDecimal EMPLOYER_RATE   = new BigDecimal("0.02");
    private static final BigDecimal MAX_BASE        = new BigDecimal("10000");
    private static final BigDecimal MAX_EMPLOYEE_C  = new BigDecimal("200");

    public BigDecimal calculateEmployeeContribution(BigDecimal monthlySalary) {
        if (monthlySalary == null || monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = monthlySalary.min(MAX_BASE);
        BigDecimal contrib = base.multiply(EMPLOYEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return contrib.min(MAX_EMPLOYEE_C);
    }

    public BigDecimal calculateEmployerContribution(BigDecimal monthlySalary) {
        if (monthlySalary == null || monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = monthlySalary.min(MAX_BASE);
        return base.multiply(EMPLOYER_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public String getComputationDetails(BigDecimal monthlySalary) {
        BigDecimal base         = (monthlySalary != null ? monthlySalary.min(MAX_BASE) : BigDecimal.ZERO);
        BigDecimal contribution = calculateEmployeeContribution(monthlySalary);
        return String.format("Pag-IBIG: ₱%,.2f × 2%% = ₱%,.2f (cap ₱200)", base, contribution);
    }
}
