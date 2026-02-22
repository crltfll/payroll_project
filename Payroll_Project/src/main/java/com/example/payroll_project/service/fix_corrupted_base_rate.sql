-- ============================================================
-- EMERGENCY DATA FIX: Corrupted base_rate column
-- ============================================================
-- Root cause: The old update() method had a parameter position
-- shift due to date_separated being present in UPDATE but not
-- CREATE. This caused rate_type (e.g. "DAILY") to be written
-- into the base_rate column, corrupting the numeric field.
--
-- Run this script ONCE against your SQLite database file
-- (e.g.:  sqlite3 payroll.db < fix_corrupted_base_rate.sql)
-- ============================================================

-- Step 1: See exactly which rows are corrupted
SELECT employee_id, employee_code, first_name, last_name,
       base_rate, rate_type
FROM employees
WHERE base_rate NOT GLOB '[0-9]*'   -- base_rate is not a number
   OR base_rate IS NULL;

-- Step 2: Fix each corrupted row.
--   • Restore base_rate to 0.00 (you will re-enter the correct
--     amount from the Employee form after this fix).
--   • Restore rate_type using the value that was mistakenly
--     written into base_rate.
UPDATE employees
SET
    -- The text that was wrongly stored in base_rate IS the rate_type value
    rate_type  = base_rate,       -- e.g. 'DAILY'
    base_rate  = '0.00',          -- reset to safe numeric default
    updated_at = CURRENT_TIMESTAMP
WHERE base_rate NOT GLOB '[0-9]*'; -- only touch corrupted rows

-- Step 3: Verify the fix
SELECT employee_id, employee_code, first_name, last_name,
       base_rate, rate_type
FROM employees
ORDER BY employee_id;
