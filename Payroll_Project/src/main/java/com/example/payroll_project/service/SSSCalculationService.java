package com.example.payroll_project.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SSS Contribution Calculation Service (CR3.1)
 * Based on RA 11199 (Social Security Act of 2018) – 2024 schedule.
 *
 * Total rate : 14%   (Employee 4.5% + Employer 9.5%)
 * MSC range  : ₱4,000 – ₱30,000
 */
public class SSSCalculationService {

    private static final BigDecimal EMPLOYEE_RATE = new BigDecimal("0.045");
    private static final BigDecimal EMPLOYER_RATE = new BigDecimal("0.095");

    /**
     * MSC lookup table: { salaryFrom (inclusive), salaryTo (inclusive), MSC }
     * All values in pesos (integer for speed; no cents in this table).
     */
    private static final int[][] MSC_TABLE = {
        {     0,  4249,  4000},
        {  4250,  4749,  4500},
        {  4750,  5249,  5000},
        {  5250,  5749,  5500},
        {  5750,  6249,  6000},
        {  6250,  6749,  6500},
        {  6750,  7249,  7000},
        {  7250,  7749,  7500},
        {  7750,  8249,  8000},
        {  8250,  8749,  8500},
        {  8750,  9249,  9000},
        {  9250,  9749,  9500},
        {  9750, 10249, 10000},
        { 10250, 10749, 10500},
        { 10750, 11249, 11000},
        { 11250, 11749, 11500},
        { 11750, 12249, 12000},
        { 12250, 12749, 12500},
        { 12750, 13249, 13000},
        { 13250, 13749, 13500},
        { 13750, 14249, 14000},
        { 14250, 14749, 14500},
        { 14750, 15249, 15000},
        { 15250, 15749, 15500},
        { 15750, 16249, 16000},
        { 16250, 16749, 16500},
        { 16750, 17249, 17000},
        { 17250, 17749, 17500},
        { 17750, 18249, 18000},
        { 18250, 18749, 18500},
        { 18750, 19249, 19000},
        { 19250, 19749, 19500},
        { 19750, 20249, 20000},
        { 20250, 20749, 20500},
        { 20750, 21249, 21000},
        { 21250, 21749, 21500},
        { 21750, 22249, 22000},
        { 22250, 22749, 22500},
        { 22750, 23249, 23000},
        { 23250, 23749, 23500},
        { 23750, 24249, 24000},
        { 24250, 24749, 24500},
        { 24750, 25249, 25000},
        { 25250, 25749, 25500},
        { 25750, 26249, 26000},
        { 26250, 26749, 26500},
        { 26750, 27249, 27000},
        { 27250, 27749, 27500},
        { 27750, 28249, 28000},
        { 28250, 28749, 28500},
        { 28750, 29249, 29000},
        { 29250, 29749, 29500},
        { 29750, Integer.MAX_VALUE, 30000}
    };

    // -----------------------------------------------------------------------

    public BigDecimal calculateEmployeeContribution(BigDecimal monthlySalary) {
        return getMSC(monthlySalary).multiply(EMPLOYEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateEmployerContribution(BigDecimal monthlySalary) {
        return getMSC(monthlySalary).multiply(EMPLOYER_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getMSC(BigDecimal monthlySalary) {
        if (monthlySalary == null || monthlySalary.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("4000");
        }
        int salary = monthlySalary.intValue();
        for (int[] row : MSC_TABLE) {
            if (salary >= row[0] && salary <= row[1]) {
                return new BigDecimal(row[2]);
            }
        }
        return new BigDecimal("30000"); // cap
    }

    /** Transparency detail string (F1) */
    public String getComputationDetails(BigDecimal monthlySalary) {
        BigDecimal msc          = getMSC(monthlySalary);
        BigDecimal contribution = calculateEmployeeContribution(monthlySalary);
        return String.format("SSS: MSC ₱%,.2f × 4.5%% = ₱%,.2f", msc, contribution);
    }
}
