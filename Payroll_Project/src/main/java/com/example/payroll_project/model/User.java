package com.example.payroll_project.model;

import java.time.LocalDateTime;

/**
 * User domain model (SEC1: Authentication & Authorization)
 * Represents a system user with role-based access control
 */
public class User {
    
    public enum Role {
        ADMIN,
        USER
    }
    
    // Primary Key
    private Integer userId;
    
    // Credentials
    private String username;
    private String passwordHash;  // BCrypt hashed
    
    // Personal Information
    private String fullName;
    
    // Authorization
    private Role role;
    
    // Status
    private boolean active;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
    
    // Constructors
    public User() {
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }
    
    public User(String username, String fullName, Role role) {
        this();
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }
    
    // Business Logic
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
    
    public boolean canModifyPayroll() {
        return role == Role.ADMIN;
    }
    
    public boolean canViewReports() {
        return true; // All users can view reports
    }
    
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }
    
    // Validation
    public boolean isValid() {
        return username != null && !username.trim().isEmpty()
            && fullName != null && !fullName.trim().isEmpty()
            && role != null;
    }
    
    // Getters and Setters
    public Integer getUserId() {
        return userId;
    }
    
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    @Override
    public String toString() {
        return String.format("User[%s: %s (%s)]", username, fullName, role);
    }
}
