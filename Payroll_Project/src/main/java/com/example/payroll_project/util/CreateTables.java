package com.example.payroll_project.util;

import java.sql.Connection;
import java.sql.Statement;

/**
 * CreateTables - Utility to create all database tables
 * Run this if you get "no such table" errors
 */
public class CreateTables {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== KC-02N Payroll System - Table Creator ===\n");
            
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initialize();
            
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON");
            System.out.println("✓ Foreign keys enabled\n");
            
            // Create employees table
            System.out.println("Creating employees table...");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    employee_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_code TEXT UNIQUE NOT NULL,
                    first_name TEXT NOT NULL,
                    middle_name TEXT,
                    last_name TEXT NOT NULL,
                    email TEXT,
                    phone_number TEXT,
                    address TEXT,
                    employment_type TEXT NOT NULL,
                    position TEXT NOT NULL,
                    department TEXT,
                    date_hired DATE NOT NULL,
                    date_separated DATE,
                    base_rate DECIMAL(10, 2) NOT NULL,
                    rate_type TEXT NOT NULL,
                    sss_number TEXT,
                    philhealth_number TEXT,
                    pagibig_number TEXT,
                    tin TEXT,
                    is_active BOOLEAN DEFAULT 1,
                    created_by INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by INTEGER,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            System.out.println("  ✓ Employees table created");
            
            // Create attendance_records table
            System.out.println("Creating attendance_records table...");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS attendance_records (
                    attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id INTEGER NOT NULL,
                    attendance_date DATE NOT NULL,
                    time_in_1 TIME,
                    time_out_1 TIME,
                    time_in_2 TIME,
                    time_out_2 TIME,
                    regular_hours DECIMAL(5, 2) DEFAULT 0,
                    overtime_hours DECIMAL(5, 2) DEFAULT 0,
                    night_diff_hours DECIMAL(5, 2) DEFAULT 0,
                    late_minutes INTEGER DEFAULT 0,
                    undertime_minutes INTEGER DEFAULT 0,
                    is_absent BOOLEAN DEFAULT 0,
                    is_holiday BOOLEAN DEFAULT 0,
                    is_rest_day BOOLEAN DEFAULT 0,
                    has_anomaly BOOLEAN DEFAULT 0,
                    anomaly_description TEXT,
                    is_manually_edited BOOLEAN DEFAULT 0,
                    import_batch_id INTEGER,
                    data_source TEXT DEFAULT 'FA2000_CSV',
                    created_by INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by INTEGER,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
                    UNIQUE(employee_id, attendance_date)
                )
            """);
            System.out.println("  ✓ Attendance_records table created");
            
            // Create payroll tables
            System.out.println("Creating payroll tables...");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pay_periods (
                    pay_period_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    period_name TEXT NOT NULL,
                    start_date DATE NOT NULL,
                    end_date DATE NOT NULL,
                    pay_date DATE,
                    status TEXT DEFAULT 'DRAFT',
                    is_locked BOOLEAN DEFAULT 0,
                    created_by INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    finalized_by INTEGER,
                    finalized_at TIMESTAMP,
                    UNIQUE(start_date, end_date)
                )
            """);
            System.out.println("  ✓ Pay_periods table created");
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payroll_records (
                    payroll_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pay_period_id INTEGER NOT NULL,
                    employee_id INTEGER NOT NULL,
                    total_regular_hours DECIMAL(6, 2) DEFAULT 0,
                    total_overtime_hours DECIMAL(6, 2) DEFAULT 0,
                    total_night_diff_hours DECIMAL(6, 2) DEFAULT 0,
                    days_worked INTEGER DEFAULT 0,
                    days_absent INTEGER DEFAULT 0,
                    basic_pay DECIMAL(12, 2) DEFAULT 0,
                    overtime_pay DECIMAL(12, 2) DEFAULT 0,
                    night_diff_pay DECIMAL(12, 2) DEFAULT 0,
                    gross_pay DECIMAL(12, 2) DEFAULT 0,
                    sss_contribution DECIMAL(10, 2) DEFAULT 0,
                    philhealth_contribution DECIMAL(10, 2) DEFAULT 0,
                    pagibig_contribution DECIMAL(10, 2) DEFAULT 0,
                    withholding_tax DECIMAL(10, 2) DEFAULT 0,
                    total_deductions DECIMAL(12, 2) DEFAULT 0,
                    net_pay DECIMAL(12, 2) DEFAULT 0,
                    is_finalized BOOLEAN DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (pay_period_id) REFERENCES pay_periods(pay_period_id),
                    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
                    UNIQUE(pay_period_id, employee_id)
                )
            """);
            System.out.println("  ✓ Payroll_records table created");
            
            // Create audit log
            System.out.println("Creating audit_log table...");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    table_name TEXT NOT NULL,
                    record_id INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    old_values TEXT,
                    new_values TEXT,
                    ip_address TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id)
                )
            """);
            System.out.println("  ✓ Audit_log table created");
            
            stmt.close();
            
            // Verify tables
            System.out.println("\n=== Verifying Tables ===");
            Statement verifyStmt = conn.createStatement();
            var rs = verifyStmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            );
            
            int count = 0;
            while (rs.next()) {
                System.out.println("  ✓ " + rs.getString("name"));
                count++;
            }
            
            System.out.println("\n=== Success! ===");
            System.out.println("Created " + count + " tables successfully.");
            System.out.println("\nYou can now:");
            System.out.println("  1. Add employees");
            System.out.println("  2. Import attendance data");
            System.out.println("  3. Process payroll");
            
        } catch (Exception e) {
            System.err.println("\n✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
