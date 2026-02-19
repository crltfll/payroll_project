-- KC-02N Payroll System Database Schema
-- Encrypted with SQLCipher (AES-256)
-- Compliant with Data Privacy Act of 2012

-- ============================================
-- USER MANAGEMENT (SEC1: Authentication)
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL, -- BCrypt hashed
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('ADMIN', 'USER')),
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- ============================================
-- EMPLOYEE MANAGEMENT (F9)
-- ============================================

CREATE TABLE IF NOT EXISTS employees (
    employee_id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_code TEXT UNIQUE NOT NULL, -- Must match FA2000 device ID
    first_name TEXT NOT NULL,
    middle_name TEXT,
    last_name TEXT NOT NULL,

    -- Contact Information
    email TEXT,
    phone_number TEXT,
    address TEXT,

    -- Employment Details
    employment_type TEXT NOT NULL CHECK(employment_type IN ('FULL_TIME', 'PART_TIME')),
    position TEXT NOT NULL,
    department TEXT,
    date_hired DATE NOT NULL,
    date_separated DATE,

    -- Compensation
    base_rate DECIMAL(10, 2) NOT NULL,
    rate_type TEXT NOT NULL CHECK(rate_type IN ('HOURLY', 'DAILY', 'MONTHLY')),

    -- Government IDs (Encrypted in application layer)
    sss_number TEXT,
    philhealth_number TEXT,
    pagibig_number TEXT,
    tin TEXT,

    -- Status
    is_active BOOLEAN DEFAULT 1,

    -- Audit
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- ============================================
-- ATTENDANCE RECORDS (CR1, F10)
-- ============================================

CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id INTEGER NOT NULL,
    attendance_date DATE NOT NULL,

    -- Time entries (FA2000 multi-punch per day)
    time_in_1 TIME,   -- Morning in
    time_out_1 TIME,  -- Lunch out
    time_in_2 TIME,   -- Lunch in
    time_out_2 TIME,  -- Evening out

    -- Calculated fields
    regular_hours DECIMAL(5, 2) DEFAULT 0,
    overtime_hours DECIMAL(5, 2) DEFAULT 0,
    night_diff_hours DECIMAL(5, 2) DEFAULT 0,
    late_minutes INTEGER DEFAULT 0,
    undertime_minutes INTEGER DEFAULT 0,

    -- Status flags
    is_absent BOOLEAN DEFAULT 0,
    is_holiday BOOLEAN DEFAULT 0,
    is_rest_day BOOLEAN DEFAULT 0,

    -- Validation flags (F3)
    has_anomaly BOOLEAN DEFAULT 0,
    anomaly_description TEXT,
    is_manually_edited BOOLEAN DEFAULT 0,

    -- Source tracking
    import_batch_id INTEGER,
    data_source TEXT DEFAULT 'FA2000_CSV',

    -- Audit
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (updated_by) REFERENCES users(user_id),

    UNIQUE(employee_id, attendance_date)
);

-- ============================================
-- SYSTEM CALENDAR (F13)
-- ============================================

CREATE TABLE IF NOT EXISTS calendar_events (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_name TEXT NOT NULL,
    event_date DATE NOT NULL,
    event_type TEXT NOT NULL CHECK(event_type IN (
        'REGULAR_HOLIDAY',
        'SPECIAL_HOLIDAY',
        'COMPANY_EVENT',
        'PAYROLL_DEADLINE'
    )),
    premium_rate DECIMAL(5, 2),
    is_recurring BOOLEAN DEFAULT 0,
    recurrence_pattern TEXT,
    is_system_event BOOLEAN DEFAULT 0,
    is_observed BOOLEAN DEFAULT 1,
    color_code TEXT,
    description TEXT,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- ============================================
-- ALLOWANCES & DEDUCTIONS (F11)
-- ============================================

CREATE TABLE IF NOT EXISTS allowance_types (
    allowance_type_id INTEGER PRIMARY KEY AUTOINCREMENT,
    allowance_name TEXT UNIQUE NOT NULL,
    is_taxable BOOLEAN DEFAULT 1,
    is_active BOOLEAN DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS deduction_types (
    deduction_type_id INTEGER PRIMARY KEY AUTOINCREMENT,
    deduction_name TEXT UNIQUE NOT NULL,
    is_statutory BOOLEAN DEFAULT 0,
    is_active BOOLEAN DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employee_allowances (
    allowance_id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id INTEGER NOT NULL,
    allowance_type_id INTEGER NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    frequency TEXT CHECK(frequency IN ('ONE_TIME', 'RECURRING')),
    effective_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT 1,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
    FOREIGN KEY (allowance_type_id) REFERENCES allowance_types(allowance_type_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS employee_deductions (
    deduction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id INTEGER NOT NULL,
    deduction_type_id INTEGER NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    frequency TEXT CHECK(frequency IN ('ONE_TIME', 'RECURRING')),
    effective_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT 1,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
    FOREIGN KEY (deduction_type_id) REFERENCES deduction_types(deduction_type_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- ============================================
-- STATUTORY TABLES (F14, CR3)
-- ============================================

CREATE TABLE IF NOT EXISTS sss_contribution_table (
    sss_table_id INTEGER PRIMARY KEY AUTOINCREMENT,
    effective_date DATE NOT NULL,
    msc_from DECIMAL(10, 2) NOT NULL,
    msc_to DECIMAL(10, 2) NOT NULL,
    msc DECIMAL(10, 2) NOT NULL,
    employee_contribution DECIMAL(10, 2) NOT NULL,
    employer_contribution DECIMAL(10, 2) NOT NULL,
    total_contribution DECIMAL(10, 2) NOT NULL,
    is_active BOOLEAN DEFAULT 1
);

CREATE TABLE IF NOT EXISTS philhealth_contribution_table (
    philhealth_table_id INTEGER PRIMARY KEY AUTOINCREMENT,
    effective_date DATE NOT NULL,
    salary_floor DECIMAL(10, 2) NOT NULL,
    salary_ceiling DECIMAL(10, 2) NOT NULL,
    premium_rate DECIMAL(5, 4) NOT NULL,
    employee_share DECIMAL(5, 4) NOT NULL,
    employer_share DECIMAL(5, 4) NOT NULL,
    is_active BOOLEAN DEFAULT 1
);

CREATE TABLE IF NOT EXISTS pagibig_contribution_table (
    pagibig_table_id INTEGER PRIMARY KEY AUTOINCREMENT,
    effective_date DATE NOT NULL,
    salary_from DECIMAL(10, 2) NOT NULL,
    salary_to DECIMAL(10, 2) NOT NULL,
    employee_rate DECIMAL(5, 4) NOT NULL,
    employer_rate DECIMAL(5, 4) NOT NULL,
    employee_max DECIMAL(10, 2),
    is_active BOOLEAN DEFAULT 1
);

CREATE TABLE IF NOT EXISTS bir_tax_table (
    bir_table_id INTEGER PRIMARY KEY AUTOINCREMENT,
    effective_date DATE NOT NULL,
    compensation_level TEXT NOT NULL,
    annual_income_from DECIMAL(12, 2) NOT NULL,
    annual_income_to DECIMAL(12, 2),
    base_tax DECIMAL(12, 2) NOT NULL,
    tax_rate DECIMAL(5, 4) NOT NULL,
    excess_over DECIMAL(12, 2) NOT NULL,
    is_active BOOLEAN DEFAULT 1
);

-- ============================================
-- PAYROLL PROCESSING (F12, CR4, CR6)
-- ============================================

CREATE TABLE IF NOT EXISTS pay_periods (
    pay_period_id INTEGER PRIMARY KEY AUTOINCREMENT,
    period_name TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    pay_date DATE,
    status TEXT DEFAULT 'DRAFT' CHECK(status IN ('DRAFT', 'PROCESSING', 'FINALIZED', 'PAID')),
    is_locked BOOLEAN DEFAULT 0,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finalized_by INTEGER,
    finalized_at TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (finalized_by) REFERENCES users(user_id),
    UNIQUE(start_date, end_date)
);

CREATE TABLE IF NOT EXISTS payroll_records (
    payroll_id INTEGER PRIMARY KEY AUTOINCREMENT,
    pay_period_id INTEGER NOT NULL,
    employee_id INTEGER NOT NULL,
    total_regular_hours DECIMAL(6, 2) DEFAULT 0,
    total_overtime_hours DECIMAL(6, 2) DEFAULT 0,
    total_night_diff_hours DECIMAL(6, 2) DEFAULT 0,
    total_holiday_hours DECIMAL(6, 2) DEFAULT 0,
    total_rest_day_hours DECIMAL(6, 2) DEFAULT 0,
    days_worked INTEGER DEFAULT 0,
    days_absent INTEGER DEFAULT 0,
    total_late_minutes INTEGER DEFAULT 0,
    total_undertime_minutes INTEGER DEFAULT 0,
    basic_pay DECIMAL(12, 2) DEFAULT 0,
    overtime_pay DECIMAL(12, 2) DEFAULT 0,
    night_diff_pay DECIMAL(12, 2) DEFAULT 0,
    holiday_pay DECIMAL(12, 2) DEFAULT 0,
    total_allowances DECIMAL(12, 2) DEFAULT 0,
    taxable_allowances DECIMAL(12, 2) DEFAULT 0,
    non_taxable_allowances DECIMAL(12, 2) DEFAULT 0,
    gross_pay DECIMAL(12, 2) DEFAULT 0,
    sss_contribution DECIMAL(10, 2) DEFAULT 0,
    philhealth_contribution DECIMAL(10, 2) DEFAULT 0,
    pagibig_contribution DECIMAL(10, 2) DEFAULT 0,
    withholding_tax DECIMAL(10, 2) DEFAULT 0,
    total_other_deductions DECIMAL(12, 2) DEFAULT 0,
    total_deductions DECIMAL(12, 2) DEFAULT 0,
    net_pay DECIMAL(12, 2) DEFAULT 0,
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
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (updated_by) REFERENCES users(user_id),
    UNIQUE(pay_period_id, employee_id)
);

CREATE TABLE IF NOT EXISTS payroll_allowances (
    payroll_allowance_id INTEGER PRIMARY KEY AUTOINCREMENT,
    payroll_id INTEGER NOT NULL,
    allowance_type_id INTEGER NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    is_taxable BOOLEAN DEFAULT 1,
    FOREIGN KEY (payroll_id) REFERENCES payroll_records(payroll_id),
    FOREIGN KEY (allowance_type_id) REFERENCES allowance_types(allowance_type_id)
);

CREATE TABLE IF NOT EXISTS payroll_deductions (
    payroll_deduction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    payroll_id INTEGER NOT NULL,
    deduction_type_id INTEGER NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    is_statutory BOOLEAN DEFAULT 0,
    FOREIGN KEY (payroll_id) REFERENCES payroll_records(payroll_id),
    FOREIGN KEY (deduction_type_id) REFERENCES deduction_types(deduction_type_id)
);

-- ============================================
-- AUDIT TRAIL (F6, SEC3)
-- ============================================

CREATE TABLE IF NOT EXISTS audit_log (
    audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    table_name TEXT NOT NULL,
    record_id INTEGER NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT')),
    old_values TEXT,
    new_values TEXT,
    ip_address TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- ============================================
-- CSV IMPORT BATCHES (CR1)
-- ============================================

CREATE TABLE IF NOT EXISTS import_batches (
    import_batch_id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_name TEXT NOT NULL,
    file_path TEXT,
    import_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    records_imported INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    status TEXT DEFAULT 'SUCCESS' CHECK(status IN ('SUCCESS', 'PARTIAL', 'FAILED')),
    error_log TEXT,
    imported_by INTEGER,
    FOREIGN KEY (imported_by) REFERENCES users(user_id)
);

-- ============================================
-- INDEXES
-- ============================================

CREATE INDEX IF NOT EXISTS idx_employees_code ON employees(employee_code);
CREATE INDEX IF NOT EXISTS idx_employees_active ON employees(is_active);
CREATE INDEX IF NOT EXISTS idx_attendance_employee_date ON attendance_records(employee_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance_records(attendance_date);
CREATE INDEX IF NOT EXISTS idx_payroll_period_employee ON payroll_records(pay_period_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_table ON audit_log(table_name, record_id);
CREATE INDEX IF NOT EXISTS idx_calendar_date ON calendar_events(event_date);

-- ============================================
-- DEFAULT DATA
-- ============================================

-- Default admin user (password: admin123 — change after first login)
INSERT OR IGNORE INTO users (username, password_hash, full_name, role)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Administrator', 'ADMIN');

-- Default allowance types
INSERT OR IGNORE INTO allowance_types (allowance_name, is_taxable, description) VALUES
('Meal Allowance',          0, 'Daily meal allowance'),
('Transportation Allowance',0, 'Daily transportation allowance'),
('Housing Allowance',       1, 'Monthly housing allowance'),
('Performance Bonus',       1, 'Performance-based bonus'),
('Cola',                    0, 'Cost of living allowance');

-- Default deduction types (statutory)
INSERT OR IGNORE INTO deduction_types (deduction_name, is_statutory, description) VALUES
('SSS Contribution',        1, 'Social Security System contribution'),
('PhilHealth Contribution', 1, 'Philippine Health Insurance Corporation contribution'),
('Pag-IBIG Contribution',   1, 'Home Development Mutual Fund contribution'),
('Withholding Tax',         1, 'BIR withholding tax');

-- Default deduction types (non-statutory)
INSERT OR IGNORE INTO deduction_types (deduction_name, is_statutory, description) VALUES
('Cash Advance',   0, 'Cash advance deduction'),
('Loan Repayment', 0, 'Loan repayment'),
('Uniform Fee',    0, 'Uniform purchase deduction'),
('Other Deduction',0, 'Miscellaneous deduction');

-- Philippine Holidays 2026
INSERT OR IGNORE INTO calendar_events (event_name, event_date, event_type, premium_rate, is_system_event) VALUES
('New Year''s Day',      '2026-01-01', 'REGULAR_HOLIDAY', 2.0, 1),
('Maundy Thursday',      '2026-04-02', 'REGULAR_HOLIDAY', 2.0, 1),
('Good Friday',          '2026-04-03', 'REGULAR_HOLIDAY', 2.0, 1),
('Araw ng Kagitingan',   '2026-04-09', 'REGULAR_HOLIDAY', 2.0, 1),
('Labor Day',            '2026-05-01', 'REGULAR_HOLIDAY', 2.0, 1),
('Independence Day',     '2026-06-12', 'REGULAR_HOLIDAY', 2.0, 1),
('Ninoy Aquino Day',     '2026-08-21', 'SPECIAL_HOLIDAY', 1.3, 1),
('National Heroes Day',  '2026-08-31', 'REGULAR_HOLIDAY', 2.0, 1),
('Bonifacio Day',        '2026-11-30', 'REGULAR_HOLIDAY', 2.0, 1),
('Christmas Day',        '2026-12-25', 'REGULAR_HOLIDAY', 2.0, 1),
('Rizal Day',            '2026-12-30', 'REGULAR_HOLIDAY', 2.0, 1);
