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
 * Database Manager — fixed to create a NEW connection per getConnection() call.
 *
 * ROOT CAUSE OF ALL "stmt pointer is closed" AND REPEATED RE-INIT ERRORS:
 * The original implementation stored a single shared Connection instance.
 * When multiple threads called getConnection() concurrently (e.g. employee
 * cache + attendance load + payroll load all starting at once), one thread
 * would find the connection "closed" or in a bad state and call initialize()
 * again — which ran the full schema script again on every single DAO call.
 *
 * FIX: getConnection() now always opens a fresh connection from the JDBC URL.
 * Each DAO already wraps connections in try-with-resources, so they are closed
 * properly after each query. SQLite handles concurrent reads fine this way.
 *
 * initialize() still runs once at startup to create tables and seed data.
 */
public class DatabaseManager {

    private static final Logger logger =
            LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;

    private static final String DB_DIRECTORY = "data";
    private static final String DB_NAME      = "payroll.db";
    private static final String DB_URL       =
            "jdbc:sqlite:" + DB_DIRECTORY + "/" + DB_NAME;

    /** True after initialize() has run successfully at least once. */
    private boolean initialized = false;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // ── One-time startup init ─────────────────────────────────────────────

    /**
     * Called once from PayrollApplication.start().
     * Creates the data directory, loads the JDBC driver, runs the schema
     * script, and marks this manager as initialized.
     */
    public void initialize() throws SQLException {
        try {
            File dataDir = new File(DB_DIRECTORY);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                logger.info("Created data directory: {}", DB_DIRECTORY);
            }

            Class.forName("org.sqlite.JDBC");
            logger.info("Database URL: {}", DB_URL);

            // Run schema once using a dedicated connection
            try (Connection c = DriverManager.getConnection(DB_URL)) {
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA foreign_keys = ON");
                    s.execute("PRAGMA journal_mode = WAL"); // better concurrency
                }
                initializeSchema(c);
            }

            initialized = true;
            logger.info("Database initialized successfully: {}", DB_URL);

        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }

    // ── Per-call connection ───────────────────────────────────────────────

    /**
     * Returns a brand-new JDBC connection each time it is called.
     * The caller is responsible for closing it (use try-with-resources).
     *
     * This eliminates ALL "stmt pointer is closed" errors caused by threads
     * sharing a single connection, and stops the repeated schema re-init
     * that was triggered whenever the shared connection appeared closed.
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            // Safety net: if called before initialize() (shouldn't happen
            // in normal flow), run init now.
            initialize();
        }
        try {
            Connection c = DriverManager.getConnection(DB_URL);
            // Enable foreign keys and WAL on every connection
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
            }
            return c;
        } catch (Exception e) {
            throw new SQLException(
                    "Failed to open database connection: " + e.getMessage(), e);
        }
    }

    // ── Schema initialization (runs once at startup) ──────────────────────

    private void initializeSchema(Connection c) throws SQLException {
        logger.info("Initializing database schema...");
        String schema = loadSchemaFromMultipleSources();

        if (schema != null && !schema.trim().isEmpty()) {
            try {
                executeSchemaScript(c, schema);
                logger.info("Schema initialized successfully from file");
                return;
            } catch (SQLException e) {
                logger.error("Failed to execute schema from file", e);
            }
        }

        logger.warn("Using fallback schema creation");
        createBasicSchema(c);
    }

    private String loadSchemaFromMultipleSources() {
        String[] paths = {
            "docs/database_schema.sql",
            "../docs/database_schema.sql",
            "Payroll_Project/docs/database_schema.sql",
            "src/main/resources/database_schema.sql"
        };
        for (String path : paths) {
            try {
                if (Files.exists(Paths.get(path))) {
                    String content = new String(
                            Files.readAllBytes(Paths.get(path)),
                            StandardCharsets.UTF_8);
                    logger.info("Schema loaded from file: {}", path);
                    return content;
                }
            } catch (IOException e) {
                logger.debug("Could not read schema from: {}", path);
            }
        }
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("database_schema.sql")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                logger.info("Schema loaded from classpath");
                return content;
            }
        } catch (IOException e) {
            logger.debug("Could not read schema from classpath");
        }
        return null;
    }

    private void executeSchemaScript(Connection c, String script)
            throws SQLException {
        String[] statements = script.split(";");
        try (Statement s = c.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    try {
                        s.execute(sql);
                    } catch (SQLException e) {
                        // Log and continue — handles "already exists" etc.
                        logger.warn("Statement execution warning (continuing): {}",
                                e.getMessage());
                    }
                }
            }
        }
    }

    private void createBasicSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('ADMIN','USER')),
                    is_active BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_login TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    employee_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_code TEXT UNIQUE NOT NULL,
                    first_name TEXT NOT NULL,
                    middle_name TEXT,
                    last_name TEXT NOT NULL,
                    email TEXT, phone_number TEXT, address TEXT,
                    employment_type TEXT NOT NULL,
                    position TEXT NOT NULL,
                    department TEXT,
                    date_hired DATE NOT NULL,
                    date_separated DATE,
                    base_rate DECIMAL(10,2) NOT NULL,
                    rate_type TEXT NOT NULL,
                    sss_number TEXT, philhealth_number TEXT,
                    pagibig_number TEXT, tin TEXT,
                    is_active BOOLEAN DEFAULT 1,
                    created_by INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by INTEGER,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS attendance_records (
                    attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id INTEGER NOT NULL,
                    attendance_date DATE NOT NULL,
                    time_in_1 TIME, time_out_1 TIME,
                    time_in_2 TIME, time_out_2 TIME,
                    regular_hours DECIMAL(5,2) DEFAULT 0,
                    overtime_hours DECIMAL(5,2) DEFAULT 0,
                    night_diff_hours DECIMAL(5,2) DEFAULT 0,
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
            s.execute("""
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
            s.execute("""
                CREATE TABLE IF NOT EXISTS payroll_records (
                    payroll_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pay_period_id INTEGER NOT NULL,
                    employee_id INTEGER NOT NULL,
                    total_regular_hours DECIMAL(6,2) DEFAULT 0,
                    total_overtime_hours DECIMAL(6,2) DEFAULT 0,
                    total_night_diff_hours DECIMAL(6,2) DEFAULT 0,
                    total_holiday_hours DECIMAL(6,2) DEFAULT 0,
                    total_rest_day_hours DECIMAL(6,2) DEFAULT 0,
                    days_worked INTEGER DEFAULT 0,
                    days_absent INTEGER DEFAULT 0,
                    total_late_minutes INTEGER DEFAULT 0,
                    total_undertime_minutes INTEGER DEFAULT 0,
                    basic_pay DECIMAL(12,2) DEFAULT 0,
                    overtime_pay DECIMAL(12,2) DEFAULT 0,
                    night_diff_pay DECIMAL(12,2) DEFAULT 0,
                    holiday_pay DECIMAL(12,2) DEFAULT 0,
                    total_allowances DECIMAL(12,2) DEFAULT 0,
                    taxable_allowances DECIMAL(12,2) DEFAULT 0,
                    non_taxable_allowances DECIMAL(12,2) DEFAULT 0,
                    gross_pay DECIMAL(12,2) DEFAULT 0,
                    sss_contribution DECIMAL(10,2) DEFAULT 0,
                    philhealth_contribution DECIMAL(10,2) DEFAULT 0,
                    pagibig_contribution DECIMAL(10,2) DEFAULT 0,
                    withholding_tax DECIMAL(10,2) DEFAULT 0,
                    total_other_deductions DECIMAL(12,2) DEFAULT 0,
                    total_deductions DECIMAL(12,2) DEFAULT 0,
                    net_pay DECIMAL(12,2) DEFAULT 0,
                    computation_details TEXT,
                    is_finalized BOOLEAN DEFAULT 0,
                    payslip_generated BOOLEAN DEFAULT 0,
                    payslip_path TEXT,
                    created_by INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by INTEGER,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (pay_period_id) REFERENCES pay_periods(pay_period_id),
                    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
                    UNIQUE(pay_period_id, employee_id)
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    table_name TEXT NOT NULL,
                    record_id INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    old_values TEXT, new_values TEXT,
                    ip_address TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(user_id)
                )
            """);
            s.execute("""
                INSERT OR IGNORE INTO users
                    (username, password_hash, full_name, role)
                VALUES ('admin',
                    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LjqXqfBGDlYKUAU1S',
                    'System Administrator', 'ADMIN')
            """);
            logger.info("Basic schema created successfully");
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * No-op — connections are now per-call and closed by their callers.
     * Kept so existing PayrollApplication.stop() calls still compile.
     */
    public void close() {
        logger.info("DatabaseManager.close() called — no persistent connection to close");
    }

    // ── Transaction helpers ───────────────────────────────────────────────
    // These are kept for any callers that use them, but each requires the
    // caller to pass in the connection they want to control.

    public void beginTransaction(Connection c) throws SQLException {
        c.setAutoCommit(false);
    }

    public void commit(Connection c) throws SQLException {
        c.commit();
        c.setAutoCommit(true);
    }

    public void rollback(Connection c) {
        try {
            if (c != null && !c.isClosed()) {
                c.rollback();
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Rollback error", e);
        }
    }

    /**
     * Legacy no-arg rollback kept for backward compatibility.
     * Does nothing — callers should use rollback(Connection).
     */
    public void rollback() {
        logger.warn("rollback() called without a connection — no-op. "
                + "Use rollback(Connection) instead.");
    }

    public boolean isDatabaseInitialized() {
        try (Connection c = getConnection();
             Statement s  = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table'")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.error("Error checking database status", e);
            return false;
        }
    }
}
