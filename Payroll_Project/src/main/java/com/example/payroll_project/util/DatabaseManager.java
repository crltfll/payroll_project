package com.example.payroll_project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

/**
 * Database Manager (SEC2: Data Protection)
 * Manages SQLite database connections
 *
 * NOTE: This version uses standard SQLite. For production encryption (AES-256),
 * you can integrate SQLCipher separately or use application-level encryption.
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
     */
    private void initializeSchema() throws SQLException {
        logger.info("Initializing database schema...");

        String schemaFile = "docs/schema.sql";

        if (!Files.exists(Paths.get(schemaFile))) {
            logger.warn("Schema file not found: {}. Creating basic tables manually.", schemaFile);
            createBasicSchema();
            return;
        }

        try {
            String schema = new String(Files.readAllBytes(Paths.get(schemaFile)));

            // Split by semicolon and execute each statement
            String[] statements = schema.split(";");

            try (Statement stmt = connection.createStatement()) {
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty() && !sql.startsWith("--")) {
                        stmt.execute(sql);
                    }
                }
            }

            logger.info("Schema initialized from file: {}", schemaFile);

        } catch (IOException e) {
            logger.error("Failed to read schema file", e);
            throw new SQLException("Failed to initialize schema", e);
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

            // Create default admin user (password: admin123)
            stmt.execute("""
                INSERT OR IGNORE INTO users (username, password_hash, full_name, role)
                VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
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