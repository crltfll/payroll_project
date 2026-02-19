package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Employee domain model (F9: Employee Management System)
 */
public class Employee {

    public enum EmploymentType {
        FULL_TIME,
        PART_TIME
    }

    public enum RateType {
        HOURLY,
        DAILY,
        MONTHLY
    }

    // Primary Key
    private Integer employeeId;

    // Basic Information
    private String employeeCode;  // Must match FA2000 device ID
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

    // Government IDs
    private String sssNumber;
    private String philhealthNumber;
    private String pagibigNumber;
    private String tin;

    // Status
    private boolean active;

    // Audit
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;

    // Constructors
    public Employee() {
        this.active    = true;
        this.createdAt = LocalDateTime.now();
    }

    public Employee(String employeeCode, String firstName, String lastName) {
        this();
        this.employeeCode = employeeCode;
        this.firstName    = firstName;
        this.lastName     = lastName;
    }

    // Helper methods
    public String getFullName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (middleName != null && !middleName.isBlank())
            sb.append(" ").append(middleName);
        sb.append(" ").append(lastName);
        return sb.toString();
    }

    public String getDisplayName() {
        return employeeCode + " – " + getFullName();
    }

    public boolean isValid() {
        return employeeCode  != null && !employeeCode.isBlank()
            && firstName     != null && !firstName.isBlank()
            && lastName      != null && !lastName.isBlank()
            && employmentType != null
            && position      != null && !position.isBlank()
            && baseRate      != null && baseRate.compareTo(BigDecimal.ZERO) > 0
            && rateType      != null
            && dateHired     != null;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Integer getEmployeeId()               { return employeeId; }
    public void setEmployeeId(Integer v)         { this.employeeId = v; }

    public String getEmployeeCode()              { return employeeCode; }
    public void setEmployeeCode(String v)        { this.employeeCode = v; }

    public String getFirstName()                 { return firstName; }
    public void setFirstName(String v)           { this.firstName = v; }

    public String getMiddleName()                { return middleName; }
    public void setMiddleName(String v)          { this.middleName = v; }

    public String getLastName()                  { return lastName; }
    public void setLastName(String v)            { this.lastName = v; }

    public String getEmail()                     { return email; }
    public void setEmail(String v)               { this.email = v; }

    public String getPhoneNumber()               { return phoneNumber; }
    public void setPhoneNumber(String v)         { this.phoneNumber = v; }

    public String getAddress()                   { return address; }
    public void setAddress(String v)             { this.address = v; }

    public EmploymentType getEmploymentType()    { return employmentType; }
    public void setEmploymentType(EmploymentType v) { this.employmentType = v; }

    public String getPosition()                  { return position; }
    public void setPosition(String v)            { this.position = v; }

    public String getDepartment()                { return department; }
    public void setDepartment(String v)          { this.department = v; }

    public LocalDate getDateHired()              { return dateHired; }
    public void setDateHired(LocalDate v)        { this.dateHired = v; }

    public LocalDate getDateSeparated()          { return dateSeparated; }
    public void setDateSeparated(LocalDate v)    { this.dateSeparated = v; }

    public BigDecimal getBaseRate()              { return baseRate; }
    public void setBaseRate(BigDecimal v)        { this.baseRate = v; }

    public RateType getRateType()               { return rateType; }
    public void setRateType(RateType v)         { this.rateType = v; }

    public String getSssNumber()                 { return sssNumber; }
    public void setSssNumber(String v)           { this.sssNumber = v; }

    public String getPhilhealthNumber()          { return philhealthNumber; }
    public void setPhilhealthNumber(String v)    { this.philhealthNumber = v; }

    public String getPagibigNumber()             { return pagibigNumber; }
    public void setPagibigNumber(String v)       { this.pagibigNumber = v; }

    public String getTin()                       { return tin; }
    public void setTin(String v)                 { this.tin = v; }

    public boolean isActive()                    { return active; }
    public void setActive(boolean v)             { this.active = v; }

    public Integer getCreatedBy()                { return createdBy; }
    public void setCreatedBy(Integer v)          { this.createdBy = v; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }

    public Integer getUpdatedBy()                { return updatedBy; }
    public void setUpdatedBy(Integer v)          { this.updatedBy = v; }

    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v)    { this.updatedAt = v; }

    @Override
    public String toString() {
        return "Employee[" + employeeCode + ": " + getFullName() + "]";
    }
}
