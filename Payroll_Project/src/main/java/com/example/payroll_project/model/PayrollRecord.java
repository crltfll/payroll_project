package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payroll Record domain model (CR4: Gross Pay and Net Pay Calculation, F12)
 * Represents computed payroll for an employee in a specific pay period
 */
public class PayrollRecord {
    
    // Primary Key
    private Integer payrollId;
    
    // Foreign Keys
    private Integer payPeriodId;
    private Integer employeeId;
    
    // Hours worked (CR2)
    private BigDecimal totalRegularHours;
    private BigDecimal totalOvertimeHours;
    private BigDecimal totalNightDiffHours;
    private BigDecimal totalHolidayHours;
    private BigDecimal totalRestDayHours;
    
    // Attendance summary
    private Integer daysWorked;
    private Integer daysAbsent;
    private Integer totalLateMinutes;
    private Integer totalUndertimeMinutes;
    
    // Gross earnings
    private BigDecimal basicPay;
    private BigDecimal overtimePay;
    private BigDecimal nightDiffPay;
    private BigDecimal holidayPay;
    
    // Allowances (F4)
    private BigDecimal totalAllowances;
    private BigDecimal taxableAllowances;
    private BigDecimal nonTaxableAllowances;
    
    private BigDecimal grossPay;
    
    // Statutory deductions (CR3)
    private BigDecimal sssContribution;
    private BigDecimal philhealthContribution;
    private BigDecimal pagibigContribution;
    private BigDecimal withholdingTax;
    
    // Other deductions (F4)
    private BigDecimal totalOtherDeductions;
    
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    
    // Computation details (F1 - Transparency)
    private String computationDetails; // JSON
    
    // Status
    private boolean finalized;
    private boolean payslipGenerated;
    private String payslipPath;
    
    // Audit
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    
    // Constructors
    public PayrollRecord() {
        this.totalRegularHours = BigDecimal.ZERO;
        this.totalOvertimeHours = BigDecimal.ZERO;
        this.totalNightDiffHours = BigDecimal.ZERO;
        this.totalHolidayHours = BigDecimal.ZERO;
        this.totalRestDayHours = BigDecimal.ZERO;
        
        this.daysWorked = 0;
        this.daysAbsent = 0;
        this.totalLateMinutes = 0;
        this.totalUndertimeMinutes = 0;
        
        this.basicPay = BigDecimal.ZERO;
        this.overtimePay = BigDecimal.ZERO;
        this.nightDiffPay = BigDecimal.ZERO;
        this.holidayPay = BigDecimal.ZERO;
        
        this.totalAllowances = BigDecimal.ZERO;
        this.taxableAllowances = BigDecimal.ZERO;
        this.nonTaxableAllowances = BigDecimal.ZERO;
        this.grossPay = BigDecimal.ZERO;
        
        this.sssContribution = BigDecimal.ZERO;
        this.philhealthContribution = BigDecimal.ZERO;
        this.pagibigContribution = BigDecimal.ZERO;
        this.withholdingTax = BigDecimal.ZERO;
        this.totalOtherDeductions = BigDecimal.ZERO;
        this.totalDeductions = BigDecimal.ZERO;
        this.netPay = BigDecimal.ZERO;
        
        this.finalized = false;
        this.payslipGenerated = false;
        this.createdAt = LocalDateTime.now();
    }
    
    public PayrollRecord(Integer payPeriodId, Integer employeeId) {
        this();
        this.payPeriodId = payPeriodId;
        this.employeeId = employeeId;
    }
    
    // Business Logic Methods
    
    /**
     * Calculate gross pay from all earnings
     */
    public void calculateGrossPay() {
        this.grossPay = basicPay
            .add(overtimePay)
            .add(nightDiffPay)
            .add(holidayPay)
            .add(totalAllowances);
    }
    
    /**
     * Calculate total deductions
     */
    public void calculateTotalDeductions() {
        this.totalDeductions = sssContribution
            .add(philhealthContribution)
            .add(pagibigContribution)
            .add(withholdingTax)
            .add(totalOtherDeductions);
    }
    
    /**
     * Calculate net pay
     */
    public void calculateNetPay() {
        calculateGrossPay();
        calculateTotalDeductions();
        this.netPay = grossPay.subtract(totalDeductions);
    }
    
    /**
     * Get taxable income for tax computation
     */
    public BigDecimal getTaxableIncome() {
        // Gross pay minus non-taxable allowances and statutory contributions
        return grossPay
            .subtract(nonTaxableAllowances)
            .subtract(sssContribution)
            .subtract(philhealthContribution)
            .subtract(pagibigContribution);
    }
    
    /**
     * Check if payroll can be finalized
     */
    public boolean canFinalize() {
        return !finalized && netPay != null && netPay.compareTo(BigDecimal.ZERO) >= 0;
    }
    
    /**
     * Get summary for display
     */
    public String getSummary() {
        return String.format("Gross: ₱%.2f | Deductions: ₱%.2f | Net: ₱%.2f",
            grossPay, totalDeductions, netPay);
    }
    
    // Getters and Setters
    public Integer getPayrollId() {
        return payrollId;
    }
    
    public void setPayrollId(Integer payrollId) {
        this.payrollId = payrollId;
    }
    
    public Integer getPayPeriodId() {
        return payPeriodId;
    }
    
    public void setPayPeriodId(Integer payPeriodId) {
        this.payPeriodId = payPeriodId;
    }
    
    public Integer getEmployeeId() {
        return employeeId;
    }
    
    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }
    
    public BigDecimal getTotalRegularHours() {
        return totalRegularHours;
    }
    
    public void setTotalRegularHours(BigDecimal totalRegularHours) {
        this.totalRegularHours = totalRegularHours;
    }
    
    public BigDecimal getTotalOvertimeHours() {
        return totalOvertimeHours;
    }
    
    public void setTotalOvertimeHours(BigDecimal totalOvertimeHours) {
        this.totalOvertimeHours = totalOvertimeHours;
    }
    
    public BigDecimal getTotalNightDiffHours() {
        return totalNightDiffHours;
    }
    
    public void setTotalNightDiffHours(BigDecimal totalNightDiffHours) {
        this.totalNightDiffHours = totalNightDiffHours;
    }
    
    public BigDecimal getTotalHolidayHours() {
        return totalHolidayHours;
    }
    
    public void setTotalHolidayHours(BigDecimal totalHolidayHours) {
        this.totalHolidayHours = totalHolidayHours;
    }
    
    public BigDecimal getTotalRestDayHours() {
        return totalRestDayHours;
    }
    
    public void setTotalRestDayHours(BigDecimal totalRestDayHours) {
        this.totalRestDayHours = totalRestDayHours;
    }
    
    public Integer getDaysWorked() {
        return daysWorked;
    }
    
    public void setDaysWorked(Integer daysWorked) {
        this.daysWorked = daysWorked;
    }
    
    public Integer getDaysAbsent() {
        return daysAbsent;
    }
    
    public void setDaysAbsent(Integer daysAbsent) {
        this.daysAbsent = daysAbsent;
    }
    
    public Integer getTotalLateMinutes() {
        return totalLateMinutes;
    }
    
    public void setTotalLateMinutes(Integer totalLateMinutes) {
        this.totalLateMinutes = totalLateMinutes;
    }
    
    public Integer getTotalUndertimeMinutes() {
        return totalUndertimeMinutes;
    }
    
    public void setTotalUndertimeMinutes(Integer totalUndertimeMinutes) {
        this.totalUndertimeMinutes = totalUndertimeMinutes;
    }
    
    public BigDecimal getBasicPay() {
        return basicPay;
    }
    
    public void setBasicPay(BigDecimal basicPay) {
        this.basicPay = basicPay;
    }
    
    public BigDecimal getOvertimePay() {
        return overtimePay;
    }
    
    public void setOvertimePay(BigDecimal overtimePay) {
        this.overtimePay = overtimePay;
    }
    
    public BigDecimal getNightDiffPay() {
        return nightDiffPay;
    }
    
    public void setNightDiffPay(BigDecimal nightDiffPay) {
        this.nightDiffPay = nightDiffPay;
    }
    
    public BigDecimal getHolidayPay() {
        return holidayPay;
    }
    
    public void setHolidayPay(BigDecimal holidayPay) {
        this.holidayPay = holidayPay;
    }
    
    public BigDecimal getTotalAllowances() {
        return totalAllowances;
    }
    
    public void setTotalAllowances(BigDecimal totalAllowances) {
        this.totalAllowances = totalAllowances;
    }
    
    public BigDecimal getTaxableAllowances() {
        return taxableAllowances;
    }
    
    public void setTaxableAllowances(BigDecimal taxableAllowances) {
        this.taxableAllowances = taxableAllowances;
    }
    
    public BigDecimal getNonTaxableAllowances() {
        return nonTaxableAllowances;
    }
    
    public void setNonTaxableAllowances(BigDecimal nonTaxableAllowances) {
        this.nonTaxableAllowances = nonTaxableAllowances;
    }
    
    public BigDecimal getGrossPay() {
        return grossPay;
    }
    
    public void setGrossPay(BigDecimal grossPay) {
        this.grossPay = grossPay;
    }
    
    public BigDecimal getSssContribution() {
        return sssContribution;
    }
    
    public void setSssContribution(BigDecimal sssContribution) {
        this.sssContribution = sssContribution;
    }
    
    public BigDecimal getPhilhealthContribution() {
        return philhealthContribution;
    }
    
    public void setPhilhealthContribution(BigDecimal philhealthContribution) {
        this.philhealthContribution = philhealthContribution;
    }
    
    public BigDecimal getPagibigContribution() {
        return pagibigContribution;
    }
    
    public void setPagibigContribution(BigDecimal pagibigContribution) {
        this.pagibigContribution = pagibigContribution;
    }
    
    public BigDecimal getWithholdingTax() {
        return withholdingTax;
    }
    
    public void setWithholdingTax(BigDecimal withholdingTax) {
        this.withholdingTax = withholdingTax;
    }
    
    public BigDecimal getTotalOtherDeductions() {
        return totalOtherDeductions;
    }
    
    public void setTotalOtherDeductions(BigDecimal totalOtherDeductions) {
        this.totalOtherDeductions = totalOtherDeductions;
    }
    
    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }
    
    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }
    
    public BigDecimal getNetPay() {
        return netPay;
    }
    
    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }
    
    public String getComputationDetails() {
        return computationDetails;
    }
    
    public void setComputationDetails(String computationDetails) {
        this.computationDetails = computationDetails;
    }
    
    public boolean isFinalized() {
        return finalized;
    }
    
    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }
    
    public boolean isPayslipGenerated() {
        return payslipGenerated;
    }
    
    public void setPayslipGenerated(boolean payslipGenerated) {
        this.payslipGenerated = payslipGenerated;
    }
    
    public String getPayslipPath() {
        return payslipPath;
    }
    
    public void setPayslipPath(String payslipPath) {
        this.payslipPath = payslipPath;
    }
    
    public Integer getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Integer getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @Override
    public String toString() {
        return String.format("Payroll[Employee: %d, Period: %d, Net: ₱%.2f]",
            employeeId, payPeriodId, netPay);
    }
}
