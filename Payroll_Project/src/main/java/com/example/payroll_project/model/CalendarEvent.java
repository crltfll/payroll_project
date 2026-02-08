package com.example.payroll_project.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Calendar Event domain model (F13: System Calendar Management)
 * Represents holidays, company events, and payroll deadlines
 */
public class CalendarEvent {
    
    public enum EventType {
        REGULAR_HOLIDAY,
        SPECIAL_HOLIDAY,
        COMPANY_EVENT,
        ACADEMIC_DATE,
        PAYROLL_DEADLINE
    }
    
    // Primary Key
    private Integer eventId;
    
    // Event details
    private String eventName;
    private LocalDate eventDate;
    private EventType eventType;
    
    // Premium rates for holidays
    private BigDecimal premiumRate;  // e.g., 2.0 for double pay
    
    // Recurrence
    private boolean recurring;
    private String recurrencePattern; // 'YEARLY', 'MONTHLY', etc.
    
    // Protection
    private boolean systemEvent;  // Cannot be deleted
    private boolean observed;
    
    // Visual
    private String colorCode;
    private String description;
    
    // Audit
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    
    // Constructors
    public CalendarEvent() {
        this.recurring = false;
        this.systemEvent = false;
        this.observed = true;
        this.createdAt = LocalDateTime.now();
    }
    
    public CalendarEvent(String eventName, LocalDate eventDate, EventType eventType) {
        this();
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventType = eventType;
    }
    
    // Business Logic
    public boolean isHoliday() {
        return eventType == EventType.REGULAR_HOLIDAY || eventType == EventType.SPECIAL_HOLIDAY;
    }
    
    public boolean appliesToPayroll() {
        return isHoliday() && observed && premiumRate != null;
    }
    
    public boolean canDelete() {
        return !systemEvent;
    }
    
    public BigDecimal getEffectivePremiumRate() {
        if (!appliesToPayroll()) {
            return BigDecimal.ONE;
        }
        return premiumRate != null ? premiumRate : BigDecimal.ONE;
    }
    
    public String getDisplayName() {
        return String.format("%s (%s)", eventName, eventDate);
    }
    
    // Get color for UI display
    public String getDisplayColor() {
        if (colorCode != null && !colorCode.isEmpty()) {
            return colorCode;
        }
        
        // Default colors by type
        switch (eventType) {
            case REGULAR_HOLIDAY:
                return "#e74c3c"; // Red
            case SPECIAL_HOLIDAY:
                return "#f39c12"; // Orange
            case COMPANY_EVENT:
                return "#3498db"; // Blue
            case ACADEMIC_DATE:
                return "#9b59b6"; // Purple
            case PAYROLL_DEADLINE:
                return "#1abc9c"; // Teal
            default:
                return "#95a5a6"; // Gray
        }
    }
    
    // Validation
    public boolean isValid() {
        return eventName != null && !eventName.trim().isEmpty()
            && eventDate != null
            && eventType != null;
    }
    
    // Getters and Setters
    public Integer getEventId() {
        return eventId;
    }
    
    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }
    
    public String getEventName() {
        return eventName;
    }
    
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }
    
    public LocalDate getEventDate() {
        return eventDate;
    }
    
    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
    
    public BigDecimal getPremiumRate() {
        return premiumRate;
    }
    
    public void setPremiumRate(BigDecimal premiumRate) {
        this.premiumRate = premiumRate;
    }
    
    public boolean isRecurring() {
        return recurring;
    }
    
    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }
    
    public String getRecurrencePattern() {
        return recurrencePattern;
    }
    
    public void setRecurrencePattern(String recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }
    
    public boolean isSystemEvent() {
        return systemEvent;
    }
    
    public void setSystemEvent(boolean systemEvent) {
        this.systemEvent = systemEvent;
    }
    
    public boolean isObserved() {
        return observed;
    }
    
    public void setObserved(boolean observed) {
        this.observed = observed;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
        return getDisplayName();
    }
}
