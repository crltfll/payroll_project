package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BIR Withholding Tax Calculation Service (CR3.4)
 * Based on RA 10963 (TRAIN Law) – 2023-present revised table.
 *
 * Annual tax brackets:
 *   0       – 250,000 :   0%
 *   250,001 – 400,000 :  15% of excess over 250,000
 *   400,001 – 800,000 :  ₱22,500 + 20% of excess over 400,000
 *   800,001 – 2,000,000: ₱102,500 + 25% of excess over 800,000
 *   2,000,001 – 8,000,000: ₱402,500 + 30% of excess over 2,000,000
 *   8,000,001+             : ₱2,202,500 + 35% of excess over 8,000,000
 *
 * Taxable income = Gross income − non-taxable allowances − SSS − PhilHealth − Pag-IBIG
 */
public class BIRTaxCalculationService {

    /**
     * Annual tax brackets: { lowerBound, baseTax, rate, excessOver }
     * All amounts in pesos as doubles for lookup convenience.
     */
    private static final double[][] BRACKETS = {
        {      250_000,         0,     0.00,         0},
        {      400_000,         0,     0.15,   250_000},
        {      800_000,    22_500,     0.20,   400_000},
        {    2_000_000,   102_500,     0.25,   800_000},
        {    8_000_000,   402_500,     0.30, 2_000_000},
        {Double.MAX_VALUE, 2_202_500, 0.35, 8_000_000},
    };

    // -----------------------------------------------------------------------

    /**
     * Calculate monthly withholding tax from a monthly taxable income.
     *
     * @param monthlyTaxableIncome  gross - non-taxable allowances - SSS - PhilHealth - Pag-IBIG
     *                               (for a MONTHLY pay period)
     * @return monthly tax to withhold
     */
    public BigDecimal calculateMonthlyTax(BigDecimal monthlyTaxableIncome) {
        if (monthlyTaxableIncome == null
                || monthlyTaxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Annualise for bracket lookup, then de-annualise
        double annual     = monthlyTaxableIncome.doubleValue() * 12;
        double annualTax  = computeAnnualTax(annual);
        double monthlyTax = annualTax / 12;

        return BigDecimal.valueOf(monthlyTax).setScale(2, RoundingMode.HALF_UP)
                         .max(BigDecimal.ZERO);
    }

    /**
     * Calculate semi-monthly withholding tax.
     */
    public BigDecimal calculateSemiMonthlyTax(BigDecimal semiMonthlyTaxableIncome) {
        if (semiMonthlyTaxableIncome == null
                || semiMonthlyTaxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double annual    = semiMonthlyTaxableIncome.doubleValue() * 24;
        double annualTax = computeAnnualTax(annual);
        double periodTax = annualTax / 24;

        return BigDecimal.valueOf(periodTax).setScale(2, RoundingMode.HALF_UP)
                         .max(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------------

    private double computeAnnualTax(double annualIncome) {
        if (annualIncome <= 250_000) return 0;

        for (int i = BRACKETS.length - 1; i >= 0; i--) {
            if (annualIncome > BRACKETS[i][0]) {
                double excess = annualIncome - BRACKETS[i][3];
                return BRACKETS[i][1] + excess * BRACKETS[i][2];
            }
        }
        return 0;
    }

    /** Transparency detail string (F1) */
    public String getComputationDetails(BigDecimal monthlyTaxableIncome) {
        if (monthlyTaxableIncome == null) return "BIR Tax: ₱0.00";
        double annual    = monthlyTaxableIncome.doubleValue() * 12;
        double annualTax = computeAnnualTax(annual);
        String bracket   = getBracketDescription(annual);
        BigDecimal tax   = calculateMonthlyTax(monthlyTaxableIncome);
        return String.format(
            "BIR Tax: Taxable ₱%,.2f/mo (Annual ₱%,.2f) → %s → Monthly ₱%,.2f",
            monthlyTaxableIncome, annual, bracket, tax);
    }

    private String getBracketDescription(double annual) {
        if (annual <= 250_000)                    return "₱0 (exempt)";
        if (annual <= 400_000)                    return "15% of excess over ₱250,000";
        if (annual <= 800_000)                    return "₱22,500 + 20% of excess over ₱400,000";
        if (annual <= 2_000_000)                  return "₱102,500 + 25% of excess over ₱800,000";
        if (annual <= 8_000_000)                  return "₱402,500 + 30% of excess over ₱2,000,000";
        return "₱2,202,500 + 35% of excess over ₱8,000,000";
    }
}
