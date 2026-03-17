package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BIR Withholding Tax Calculation Service (CR3.4)
 * Based on RA 10963 (TRAIN Law) – 2023-present revised table.
 *
 * Tax is always annualised and then divided back to the period.
 * This ensures consistency regardless of pay frequency.
 *
 * IMPORTANT: The input to each method is the TAXABLE INCOME for that period
 * (not gross pay). Taxable income = gross − non-taxable allowances − SSS −
 * PhilHealth − Pag-IBIG contributions (already computed by PayrollService).
 */
public class BIRTaxCalculationService {

    // TRAIN Law annual brackets: { upperLimit, baseTax, rate, excessOver }
    private static final double[][] BRACKETS = {
        {      250_000,         0,     0.00,         0},
        {      400_000,         0,     0.15,   250_000},
        {      800_000,    22_500,     0.20,   400_000},
        {    2_000_000,   102_500,     0.25,   800_000},
        {    8_000_000,   402_500,     0.30, 2_000_000},
        {Double.MAX_VALUE, 2_202_500, 0.35, 8_000_000},
    };

    // ── Public API ─────────────────────────────────────────────────────────

    /** Monthly pay period (×12 annualisation). */
    public BigDecimal calculateMonthlyTax(BigDecimal monthlyTaxableIncome) {
        return annualisedTax(monthlyTaxableIncome, 12);
    }

    /** Semi-monthly pay period (×24 annualisation). */
    public BigDecimal calculateSemiMonthlyTax(BigDecimal semiMonthlyTaxableIncome) {
        return annualisedTax(semiMonthlyTaxableIncome, 24);
    }

    /** Weekly pay period (×52 annualisation). */
    public BigDecimal calculateWeeklyTax(BigDecimal weeklyTaxableIncome) {
        return annualisedTax(weeklyTaxableIncome, 52);
    }

    // ── Core computation ───────────────────────────────────────────────────

    private BigDecimal annualisedTax(BigDecimal periodIncome, int periodsPerYear) {
        if (periodIncome == null || periodIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double annual    = periodIncome.doubleValue() * periodsPerYear;
        double annualTax = computeAnnualTax(annual);
        double periodTax = annualTax / periodsPerYear;
        return BigDecimal.valueOf(periodTax).setScale(2, RoundingMode.HALF_UP)
                         .max(BigDecimal.ZERO);
    }

    private double computeAnnualTax(double annualIncome) {
        if (annualIncome <= 250_000) return 0;
        for (int i = BRACKETS.length - 1; i >= 0; i--) {
            if (annualIncome > BRACKETS[i][0]) {
                return BRACKETS[i][1] + (annualIncome - BRACKETS[i][3]) * BRACKETS[i][2];
            }
        }
        return 0;
    }

    // ── Transparency text ──────────────────────────────────────────────────

    public String getComputationDetails(BigDecimal periodTaxableIncome) {
        if (periodTaxableIncome == null) return "BIR Tax: ₱0.00";
        // Show the monthly-equivalent for reference (PayrollService appends the period label)
        double annual    = periodTaxableIncome.doubleValue() * 12;
        String bracket   = getBracketDescription(annual);
        BigDecimal tax   = calculateMonthlyTax(periodTaxableIncome);
        return String.format("BIR Tax: Taxable ₱%,.2f/period → %s → ₱%,.2f/period",
                periodTaxableIncome, bracket, tax);
    }

    private String getBracketDescription(double annual) {
        if (annual <= 250_000) return "₱0 (exempt)";
        if (annual <= 400_000) return "15% of excess over ₱250,000";
        if (annual <= 800_000) return "₱22,500 + 20% of excess over ₱400,000";
        if (annual <= 2_000_000) return "₱102,500 + 25% of excess over ₱800,000";
        if (annual <= 8_000_000) return "₱402,500 + 30% of excess over ₱2,000,000";
        return "₱2,202,500 + 35% of excess over ₱8,000,000";
    }
}
