package com.example.payroll_project.dao;

import com.payroll.model.Employee;
import com.payroll.util.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Employee Data Access Object (F9: Employee Management System)
 * Handles all database operations for employees
 */
public class EmployeeDAO implements BaseDAO<Employee, Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(EmployeeDAO.class);
    private final DatabaseManager dbManager;
    
    public EmployeeDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    @Override
    public Employee create(Employee employee) throws SQLException {
        String sql = """
            INSERT INTO employees (
                employee_code, first_name, middle_name, last_name,
                email, phone_number, address,
                employment_type, position, department,
                date_hired, base_rate, rate_type,
                sss_number, philhealth_number, pagibig_number, tin,
                is_active, created_by, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setEmployeeParameters(stmt, employee);
            stmt.setBoolean(18, employee.isActive());
            stmt.setObject(19, employee.getCreatedBy());
            stmt.setObject(20, Timestamp.valueOf(employee.getCreatedAt()));
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creating employee failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    employee.setEmployeeId(generatedKeys.getInt(1));
                    logger.info("Created employee: {}", employee.getEmployeeCode());
                    return employee;
                } else {
                    throw new SQLException("Creating employee failed, no ID obtained.");
                }
            }
        }
    }
    
    @Override
    public Optional<Employee> findById(Integer id) throws SQLException {
        String sql = "SELECT * FROM employees WHERE employee_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEmployee(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Find employee by employee code (for FA2000 matching)
     */
    public Optional<Employee> findByEmployeeCode(String employeeCode) throws SQLException {
        String sql = "SELECT * FROM employees WHERE employee_code = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, employeeCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToEmployee(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<Employee> findAll() throws SQLException {
        return findAll(true); // Active only by default
    }
    
    /**
     * Find all employees with optional active filter
     */
    public List<Employee> findAll(boolean activeOnly) throws SQLException {
        String sql = activeOnly 
            ? "SELECT * FROM employees WHERE is_active = 1 ORDER BY last_name, first_name"
            : "SELECT * FROM employees ORDER BY last_name, first_name";
        
        List<Employee> employees = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                employees.add(mapResultSetToEmployee(rs));
            }
        }
        
        logger.debug("Found {} employees", employees.size());
        return employees;
    }
    
    /**
     * Search employees by name or code
     */
    public List<Employee> search(String searchTerm) throws SQLException {
        String sql = """
            SELECT * FROM employees
            WHERE (LOWER(first_name) LIKE ? 
               OR LOWER(last_name) LIKE ?
               OR LOWER(employee_code) LIKE ?)
            AND is_active = 1
            ORDER BY last_name, first_name
        """;
        
        List<Employee> employees = new ArrayList<>();
        String searchPattern = "%" + searchTerm.toLowerCase() + "%";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    employees.add(mapResultSetToEmployee(rs));
                }
            }
        }
        
        return employees;
    }
    
    /**
     * Find employees by employment type (F7: Educational Institution-Specific Features)
     */
    public List<Employee> findByEmploymentType(Employee.EmploymentType type) throws SQLException {
        String sql = """
            SELECT * FROM employees
            WHERE employment_type = ? AND is_active = 1
            ORDER BY last_name, first_name
        """;
        
        List<Employee> employees = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, type.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    employees.add(mapResultSetToEmployee(rs));
                }
            }
        }
        
        return employees;
    }
    
    /**
     * Find employees by department
     */
    public List<Employee> findByDepartment(String department) throws SQLException {
        String sql = """
            SELECT * FROM employees
            WHERE department = ? AND is_active = 1
            ORDER BY last_name, first_name
        """;
        
        List<Employee> employees = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, department);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    employees.add(mapResultSetToEmployee(rs));
                }
            }
        }
        
        return employees;
    }
    
    @Override
    public boolean update(Employee employee) throws SQLException {
        String sql = """
            UPDATE employees SET
                employee_code = ?, first_name = ?, middle_name = ?, last_name = ?,
                email = ?, phone_number = ?, address = ?,
                employment_type = ?, position = ?, department = ?,
                date_hired = ?, date_separated = ?,
                base_rate = ?, rate_type = ?,
                sss_number = ?, philhealth_number = ?, pagibig_number = ?, tin = ?,
                is_active = ?, updated_by = ?, updated_at = ?
            WHERE employee_id = ?
        """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            setEmployeeParameters(stmt, employee);
            stmt.setObject(12, employee.getDateSeparated() != null ? Date.valueOf(employee.getDateSeparated()) : null);
            stmt.setBoolean(19, employee.isActive());
            stmt.setObject(20, employee.getUpdatedBy());
            stmt.setTimestamp(21, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(22, employee.getEmployeeId());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Updated employee: {}", employee.getEmployeeCode());
                return true;
            }
            return false;
        }
    }
    
    @Override
    public boolean delete(Integer id) throws SQLException {
        // Soft delete - set is_active to false
        String sql = "UPDATE employees SET is_active = 0, updated_at = ? WHERE employee_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, id);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.info("Deleted employee (soft delete): {}", id);
                return true;
            }
            return false;
        }
    }
    
    /**
     * Hard delete - permanently remove from database
     */
    public boolean hardDelete(Integer id) throws SQLException {
        String sql = "DELETE FROM employees WHERE employee_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.warn("Hard deleted employee: {}", id);
                return true;
            }
            return false;
        }
    }
    
    @Override
    public boolean exists(Integer id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM employees WHERE employee_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if employee code already exists
     */
    public boolean employeeCodeExists(String employeeCode, Integer excludeId) throws SQLException {
        String sql = excludeId != null
            ? "SELECT COUNT(*) FROM employees WHERE employee_code = ? AND employee_id != ?"
            : "SELECT COUNT(*) FROM employees WHERE employee_code = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, employeeCode);
            if (excludeId != null) {
                stmt.setInt(2, excludeId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    @Override
    public long count() throws SQLException {
        return count(true);
    }
    
    /**
     * Count employees with optional active filter
     */
    public long count(boolean activeOnly) throws SQLException {
        String sql = activeOnly
            ? "SELECT COUNT(*) FROM employees WHERE is_active = 1"
            : "SELECT COUNT(*) FROM employees";
        
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        return 0;
    }
    
    /**
     * Set employee parameters for PreparedStatement
     */
    private void setEmployeeParameters(PreparedStatement stmt, Employee employee) throws SQLException {
        stmt.setString(1, employee.getEmployeeCode());
        stmt.setString(2, employee.getFirstName());
        stmt.setString(3, employee.getMiddleName());
        stmt.setString(4, employee.getLastName());
        stmt.setString(5, employee.getEmail());
        stmt.setString(6, employee.getPhoneNumber());
        stmt.setString(7, employee.getAddress());
        stmt.setString(8, employee.getEmploymentType().name());
        stmt.setString(9, employee.getPosition());
        stmt.setString(10, employee.getDepartment());
        stmt.setDate(11, Date.valueOf(employee.getDateHired()));
        stmt.setBigDecimal(12, employee.getBaseRate());
        stmt.setString(13, employee.getRateType().name());
        stmt.setString(14, employee.getSssNumber());
        stmt.setString(15, employee.getPhilhealthNumber());
        stmt.setString(16, employee.getPagibigNumber());
        stmt.setString(17, employee.getTin());
    }
    
    /**
     * Map ResultSet to Employee object
     */
    private Employee mapResultSetToEmployee(ResultSet rs) throws SQLException {
        Employee employee = new Employee();
        
        employee.setEmployeeId(rs.getInt("employee_id"));
        employee.setEmployeeCode(rs.getString("employee_code"));
        employee.setFirstName(rs.getString("first_name"));
        employee.setMiddleName(rs.getString("middle_name"));
        employee.setLastName(rs.getString("last_name"));
        employee.setEmail(rs.getString("email"));
        employee.setPhoneNumber(rs.getString("phone_number"));
        employee.setAddress(rs.getString("address"));
        employee.setEmploymentType(Employee.EmploymentType.valueOf(rs.getString("employment_type")));
        employee.setPosition(rs.getString("position"));
        employee.setDepartment(rs.getString("department"));
        
        Date dateHired = rs.getDate("date_hired");
        if (dateHired != null) {
            employee.setDateHired(dateHired.toLocalDate());
        }
        
        Date dateSeparated = rs.getDate("date_separated");
        if (dateSeparated != null) {
            employee.setDateSeparated(dateSeparated.toLocalDate());
        }
        
        employee.setBaseRate(rs.getBigDecimal("base_rate"));
        employee.setRateType(Employee.RateType.valueOf(rs.getString("rate_type")));
        employee.setSssNumber(rs.getString("sss_number"));
        employee.setPhilhealthNumber(rs.getString("philhealth_number"));
        employee.setPagibigNumber(rs.getString("pagibig_number"));
        employee.setTin(rs.getString("tin"));
        employee.setActive(rs.getBoolean("is_active"));
        
        Integer createdBy = (Integer) rs.getObject("created_by");
        employee.setCreatedBy(createdBy);
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            employee.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Integer updatedBy = (Integer) rs.getObject("updated_by");
        employee.setUpdatedBy(updatedBy);
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            employee.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        return employee;
    }
}
