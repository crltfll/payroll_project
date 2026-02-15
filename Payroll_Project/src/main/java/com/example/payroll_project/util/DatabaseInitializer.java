package com.example.payroll_project.util;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseInitializer {
    
    private static final String DB_URL = "jdbc:sqlite:data/payroll.db";
    
    public static void main(String[] args) {
        try {
            System.out.println("=== KC-02N Payroll System - Database Initializer ===");
            System.out.println();
            
            // Load SQLite driver
            Class.forName("org.sqlite.JDBC");
            
            // Connect to database
            Connection conn = DriverManager.getConnection(DB_URL);
            System.out.println("✓ Connected to database: " + DB_URL);
            
            // Check if users table exists
            boolean usersTableExists = checkUsersTable(conn);
            if (!usersTableExists) {
                System.out.println("✗ Users table does not exist!");
                System.out.println("  Creating users table...");
                createUsersTable(conn);
            }
            
            // Delete existing admin user (if any)
            System.out.println("\n→ Removing any existing admin user...");
            deleteAdminUser(conn);
            
            // Create new admin user
            System.out.println("→ Creating admin user...");
            createAdminUser(conn);
            
            // Verify admin user
            System.out.println("\n→ Verifying admin user...");
            verifyAdminUser(conn);
            
            conn.close();
            
            System.out.println("\n=== Setup Complete! ===");
            System.out.println("\nYou can now login with:");
            System.out.println("  Username: admin");
            System.out.println("  Password: admin123");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("\n✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean checkUsersTable(Connection conn) throws Exception {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='users'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();
        boolean exists = rs.next();
        rs.close();
        stmt.close();
        return exists;
    }
    
    private static void createUsersTable(Connection conn) throws Exception {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                full_name TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('ADMIN', 'USER')),
                is_active BOOLEAN DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login TIMESTAMP
            )
        """;
        
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.execute();
        stmt.close();
        System.out.println("  ✓ Users table created");
    }
    
    private static void deleteAdminUser(Connection conn) throws Exception {
        String sql = "DELETE FROM users WHERE username = 'admin'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        int deleted = stmt.executeUpdate();
        stmt.close();
        
        if (deleted > 0) {
            System.out.println("  ✓ Removed " + deleted + " existing admin user(s)");
        } else {
            System.out.println("  ℹ No existing admin user found");
        }
    }
    
    private static void createAdminUser(Connection conn) throws Exception {
        // Generate BCrypt hash for 'admin123'
        String password = "admin123";
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        
        String sql = """
            INSERT INTO users (username, password_hash, full_name, role, is_active)
            VALUES (?, ?, ?, ?, ?)
        """;
        
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, "admin");
        stmt.setString(2, passwordHash);
        stmt.setString(3, "System Administrator");
        stmt.setString(4, "ADMIN");
        stmt.setBoolean(5, true);
        
        stmt.executeUpdate();
        stmt.close();
        
        System.out.println("  ✓ Admin user created successfully");
        System.out.println("  ℹ Password hash: " + passwordHash.substring(0, 20) + "...");
    }
    
    private static void verifyAdminUser(Connection conn) throws Exception {
        String sql = "SELECT user_id, username, full_name, role, is_active FROM users WHERE username = 'admin'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            System.out.println("  ✓ Admin user verified:");
            System.out.println("    - User ID: " + rs.getInt("user_id"));
            System.out.println("    - Username: " + rs.getString("username"));
            System.out.println("    - Full Name: " + rs.getString("full_name"));
            System.out.println("    - Role: " + rs.getString("role"));
            System.out.println("    - Active: " + rs.getBoolean("is_active"));
        } else {
            System.out.println("  ✗ Admin user NOT found after creation!");
        }
        
        rs.close();
        stmt.close();
    }
}
