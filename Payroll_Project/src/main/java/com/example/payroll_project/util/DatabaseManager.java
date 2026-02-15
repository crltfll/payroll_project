package com.example.payroll_project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

/**
 * Database Manager (SEC2: Data Protection)
 * Manages SQLite database connections
 * Fixed for IntelliJ IDEA compatibility
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;
    private static final String DB_NAME = "payroll.db";
    private static final String DB_DIRECTORY = "data";
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIRECTORY + "/" + DB_NAME;

    private Connection connection;

    private DatabaseManager() {
        // Private constructor for singleton
    }

    /**
     * Get singleton instance
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Initialize database
     */
    public void initialize() throws SQLException {
        try {
            // Create data directory if it doesn't exist
            File dataDir = new File(DB_DIRECTORY);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                logger.info("Created data directory: {}", DB_DIRECTORY);
            }

            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Connect to database
            connection = DriverManager.getConnection(DB_URL);

            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            logger.info("Database initialized successfully: {}", DB_URL);

            // Initialize schema
            initializeSchema();

        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found", e);
            throw new SQLException("Failed to load database driver", e);
        }
    }

    /**
     * Initialize database schema
     * Works with both file system and classpath resources (IntelliJ compatible)
     */
    private void initializeSchema() throws SQLException {
        logger.info("Initializing database schema...");

        // Try multiple locations for the schema file
        String[] possiblePaths = {
            "docs/database_schema.sql",           // Maven/command line
            "../docs/database_schema.sql",        // IntelliJ relative
            "Payroll_Project/docs/database_schema.sql", // From project root
        };

        String schema = null;
        
        // First, try to load from file system
        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                try {
                    schema = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                    logger.info("Schema loaded from file: {}", path);
                    break;
                } catch (IOException e) {
                    logger.warn("Failed to read schema from: {}", path);
                }
            }
        }
        
        // If not found in file system, try classpath (if schema is in resources)
        if (schema == null) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("database_schema.sql")) {
                if (is != null) {
                    schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    logger.info("Schema loaded from classpath");
                }
            } catch (IOException e) {
                logger.warn("Failed to read schema from classpath", e);
            }
        }

        // If still not found, create basic schema
        if (schema == null || schema.trim().isEmpty()) {
            logger.warn("Schema file not found in any location. Creating basic schema manually.");
            createBasicSchema();
            return;
        }

        try {
            // Execute the schema
            executeSchemaScript(schema);
            logger.info("Schema initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to execute schema", e);
            throw e;
        }
    }

    /**
     * Execute SQL script from string
     */
    private void executeSchemaScript(String script) throws SQLException {
        // Split by semicolon and execute each statement
        String[] statements = script.split(";");

        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                // Skip empty statements and comments
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    try {
                        stmt.execute(sql);
                    } catch (SQLException e) {
                        // Log but continue with other statements
                        logger.warn("Failed to execute statement (continuing): {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Create basic schema manually (fallback)
     */
    private void createBasicSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Users table
            stmt.execute("""
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
            """);

            // Employees table (simplified)
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

            // Attendance records table
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
                    FOREIGN KEY (created_by) REFERENCES users(user_id),
                    FOREIGN KEY (updated_by) REFERENCES users(user_id),
                    UNIQUE(employee_id, attendance_date)
                )
            """);

            // Create default admin user (password: admin123)
            // This hash is generated by BCrypt for "admin123"
            stmt.execute("""
                INSERT OR IGNORE INTO users (username, password_hash, full_name, role)
                VALUES ('admin', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LjqXqfBGDlYKUAU1S', 
                        'System Administrator', 'ADMIN')
            """);

            logger.info("Basic schema created successfully");
        }
    }

    /**
     * Get database connection
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            initialize();
        }
        return connection;
    }

    /**
     * Close database connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }

    /**
     * Execute SQL script
     */
    public void executeScript(String script) throws SQLException {
        String[] statements = script.split(";");

        try (Statement stmt = getConnection().createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    stmt.execute(sql);
                }
            }
        }
    }

    /**
     * Begin transaction
     */
    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }

    /**
     * Commit transaction
     */
    public void commit() throws SQLException {
        getConnection().commit();
        getConnection().setAutoCommit(true);
    }

    /**
     * Rollback transaction
     */
    public void rollback() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error rolling back transaction", e);
        }
    }

    /**
     * Check if database exists and is accessible
     */
    public boolean isDatabaseInitialized() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'")) {

            if (rs.next()) {
                int tableCount = rs.getInt(1);
                return tableCount > 0;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Error checking database status", e);
            return false;
        }
    }

    /**
     * Backup database
     */
    public void backupDatabase(String backupPath) throws SQLException, IOException {
        Files.copy(
                Paths.get(DB_DIRECTORY, DB_NAME),
                Paths.get(backupPath)
        );
        logger.info("Database backed up to: {}", backupPath);
    }

    /**
     * Restore database from backup
     */
    public void restoreDatabase(String backupPath) throws SQLException, IOException {
        close();

        Files.copy(
                Paths.get(backupPath),
                Paths.get(DB_DIRECTORY, DB_NAME),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );

        initialize();
        logger.info("Database restored from: {}", backupPath);
    }
}
