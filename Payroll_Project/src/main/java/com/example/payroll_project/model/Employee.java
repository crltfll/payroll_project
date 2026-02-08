package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Employee domain model (F9: Employee Management System)
 * Represents an employee in the payroll system
 */
public class Employee {
    
    // Enums
    public enum EmploymentType {
        FULL_TIME,
        PART_TIME,
        TEACHING_STAFF,
        NON_TEACHING_STAFF
    }
    
    public enum RateType {
        HOURLY,
        DAILY,
        MONTHLY
    }
    
    // Primary Key
    private Integer employeeId;
    
    // Basic Information
    private String employeeCode;  // For FA2000 device matching
    private String firstName;
    private String middleName;
    private String lastName;
    
    // Contact Information
    private String email;
    private String phoneNumber;
    private String address;
    
    // Employment Details
    private EmploymentType employmentType;
    private String position;
    private String department;
    private LocalDate dateHired;
    private LocalDate dateSeparated;
    
    // Compensation
    private BigDecimal baseRate;
    private RateType rateType;
    
    // Government IDs (will be encrypted)
    private String sssNumber;
    private String philhealthNumber;
    private String pagibigNumber;
    private String tin;
    
    // Status
    private boolean active;
    
    // Audit fields
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    
    // Constructors
    public Employee() {
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }
    
    public Employee(String employeeCode, String firstName, String lastName) {
        this();
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    // Full name helper
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        sb.append(firstName);
        if (middleName != null && !middleName.isEmpty()) {
            sb.append(" ").append(middleName);
        }
        sb.append(" ").append(lastName);
        return sb.toString();
    }
    
    // Display name for UI
    public String getDisplayName() {
        return String.format("%s - %s", employeeCode, getFullName());
    }
    
    // Validation
    public boolean isValid() {
        return employeeCode != null && !employeeCode.trim().isEmpty()
            && firstName != null && !firstName.trim().isEmpty()
            && lastName != null && !lastName.trim().isEmpty()
            && employmentType != null
            && position != null && !position.trim().isEmpty()
            && baseRate != null && baseRate.compareTo(BigDecimal.ZERO) > 0
            && rateType != null
            && dateHired != null;
    }
    
    // Getters and Setters
    public Integer getEmployeeId() {
        return employeeId;
    }
    
    public void setEmployeeId(Integer employeeId) {
        this.employeeId = employeeId;
    }
    
    public String getEmployeeCode() {
        return employeeCode;
    }
    
    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getMiddleName() {
        return middleName;
    }
    
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public EmploymentType getEmploymentType() {
        return employmentType;
    }
    
    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }
    
    public String getPosition() {
        return position;
    }
    
    public void setPosition(String position) {
        this.position = position;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public LocalDate getDateHired() {
        return dateHired;
    }
    
    public void setDateHired(LocalDate dateHired) {
        this.dateHired = dateHired;
    }
    
    public LocalDate getDateSeparated() {
        return dateSeparated;
    }
    
    public void setDateSeparated(LocalDate dateSeparated) {
        this.dateSeparated = dateSeparated;
    }
    
    public BigDecimal getBaseRate() {
        return baseRate;
    }
    
    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }
    
    public RateType getRateType() {
        return rateType;
    }
    
    public void setRateType(RateType rateType) {
        this.rateType = rateType;
    }
    
    public String getSssNumber() {
        return sssNumber;
    }
    
    public void setSssNumber(String sssNumber) {
        this.sssNumber = sssNumber;
    }
    
    public String getPhilhealthNumber() {
        return philhealthNumber;
    }
    
    public void setPhilhealthNumber(String philhealthNumber) {
        this.philhealthNumber = philhealthNumber;
    }
    
    public String getPagibigNumber() {
        return pagibigNumber;
    }
    
    public void setPagibigNumber(String pagibigNumber) {
        this.pagibigNumber = pagibigNumber;
    }
    
    public String getTin() {
        return tin;
    }
    
    public void setTin(String tin) {
        this.tin = tin;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
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
        return String.format("Employee[%s: %s]", employeeCode, getFullName());
    }
}
