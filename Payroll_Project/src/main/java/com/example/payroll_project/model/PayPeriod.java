package com.example.payroll_project.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pay Period domain model (F12: Pay Period & Payroll Batch Management)
 * Represents a payroll processing period
 */
public class PayPeriod {
    
    public enum Status {
        DRAFT,
        PROCESSING,
        FINALIZED,
        PAID
    }
    
    // Primary Key
    private Integer payPeriodId;
    
    // Period details
    private String periodName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate payDate;
    
    // Status
    private Status status;
    private boolean locked;
    
    // Audit
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer finalizedBy;
    private LocalDateTime finalizedAt;
    
    // Constructors
    public PayPeriod() {
        this.status = Status.DRAFT;
        this.locked = false;
        this.createdAt = LocalDateTime.now();
    }
    
    public PayPeriod(String periodName, LocalDate startDate, LocalDate endDate) {
        this();
        this.periodName = periodName;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    // Business Logic
    public boolean canModify() {
        return !locked && status != Status.FINALIZED && status != Status.PAID;
    }
    
    public boolean canFinalize() {
        return status == Status.PROCESSING && !locked;
    }
    
    public void finalize(Integer userId) {
        this.status = Status.FINALIZED;
        this.locked = true;
        this.finalizedBy = userId;
        this.finalizedAt = LocalDateTime.now();
    }
    
    public String getDisplayName() {
        return String.format("%s (%s to %s)", periodName, startDate, endDate);
    }
    
    // Validation
    public boolean isValid() {
        return periodName != null && !periodName.trim().isEmpty()
            && startDate != null
            && endDate != null
            && !endDate.isBefore(startDate);
    }
    
    // Getters and Setters
    public Integer getPayPeriodId() {
        return payPeriodId;
    }
    
    public void setPayPeriodId(Integer payPeriodId) {
        this.payPeriodId = payPeriodId;
    }
    
    public String getPeriodName() {
        return periodName;
    }
    
    public void setPeriodName(String periodName) {
        this.periodName = periodName;
    }
    
    public LocalDate getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    
    public LocalDate getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    
    public LocalDate getPayDate() {
        return payDate;
    }
    
    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public boolean isLocked() {
        return locked;
    }
    
    public void setLocked(boolean locked) {
        this.locked = locked;
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
    
    public Integer getFinalizedBy() {
        return finalizedBy;
    }
    
    public void setFinalizedBy(Integer finalizedBy) {
        this.finalizedBy = finalizedBy;
    }
    
    public LocalDateTime getFinalizedAt() {
        return finalizedAt;
    }
    
    public void setFinalizedAt(LocalDateTime finalizedAt) {
        this.finalizedAt = finalizedAt;
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
}
