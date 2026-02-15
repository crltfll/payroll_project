package com.example.payroll_project.service;

import com.example.payroll_project.model.User;
import com.example.payroll_project.security.PasswordUtil;
import com.example.payroll_project.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User Service (SEC1: Authentication & Authorization)
 * Handles user authentication and management
 */
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final DatabaseManager dbManager;
    
    public UserService() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    /**
     * Authenticate user
     */
    public Optional<User> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String passwordHash = rs.getString("password_hash");
                    
                    // Verify password
                    if (PasswordUtil.verifyPassword(password, passwordHash)) {
                        User user = mapResultSetToUser(rs);
                        
                        // Update last login
                        updateLastLogin(user.getUserId());
                        
                        return Optional.of(user);
                    }
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Update last login timestamp
     */
    private void updateLastLogin(Integer userId) {
        String sql = "UPDATE users SET last_login = ? WHERE user_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, userId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Failed to update last login", e);
        }
    }
    
    /**
     * Map ResultSet to User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(User.Role.valueOf(rs.getString("role")));
        user.setActive(rs.getBoolean("is_active"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toLocalDateTime());
        }
        
        return user;
    }
}
