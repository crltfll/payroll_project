package com.example.payroll_project.controller;

import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.model.Employee;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Employee Form Controller for Add/Edit Employee
 * Handles employee creation and modification (F9)
 */
public class EmployeeFormController {
    
    private static final Logger logger = LoggerFactory.getLogger(EmployeeFormController.class);
    
    @FXML private TextField employeeCodeField;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneNumberField;
    @FXML private TextArea addressArea;
    @FXML private ComboBox<Employee.EmploymentType> employmentTypeCombo;
    @FXML private TextField positionField;
    @FXML private TextField departmentField;
    @FXML private DatePicker dateHiredPicker;
    @FXML private TextField baseRateField;
    @FXML private ComboBox<Employee.RateType> rateTypeCombo;
    @FXML private TextField sssNumberField;
    @FXML private TextField philhealthNumberField;
    @FXML private TextField pagibigNumberField;
    @FXML private TextField tinField;
    @FXML private CheckBox activeCheckbox;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Label errorLabel;
    
    private EmployeeDAO employeeDAO;
    private Employee employee; // null for add, set for edit
    private boolean saveClicked = false;
    
    @FXML
    public void initialize() {
        employeeDAO = new EmployeeDAO();
        
        // Initialize combo boxes
        employmentTypeCombo.getItems().addAll(Employee.EmploymentType.values());
        rateTypeCombo.getItems().addAll(Employee.RateType.values());
        
        // Set defaults
        employmentTypeCombo.setValue(Employee.EmploymentType.FULL_TIME);
        rateTypeCombo.setValue(Employee.RateType.MONTHLY);
        activeCheckbox.setSelected(true);
        
        errorLabel.setVisible(false);
    }
    
    /**
     * Set employee for editing
     */
    public void setEmployee(Employee employee) {
        this.employee = employee;
        
        if (employee != null) {
            // Populate fields for editing
            employeeCodeField.setText(employee.getEmployeeCode());
            firstNameField.setText(employee.getFirstName());
            middleNameField.setText(employee.getMiddleName());
            lastNameField.setText(employee.getLastName());
            emailField.setText(employee.getEmail());
            phoneNumberField.setText(employee.getPhoneNumber());
            addressArea.setText(employee.getAddress());
            employmentTypeCombo.setValue(employee.getEmploymentType());
            positionField.setText(employee.getPosition());
            departmentField.setText(employee.getDepartment());
            dateHiredPicker.setValue(employee.getDateHired());
            baseRateField.setText(employee.getBaseRate().toString());
            rateTypeCombo.setValue(employee.getRateType());
            sssNumberField.setText(employee.getSssNumber());
            philhealthNumberField.setText(employee.getPhilhealthNumber());
            pagibigNumberField.setText(employee.getPagibigNumber());
            tinField.setText(employee.getTin());
            activeCheckbox.setSelected(employee.isActive());
        }
    }
    
    @FXML
    private void handleSave() {
        if (validateInput()) {
            try {
                if (employee == null) {
                    // Create new employee
                    employee = new Employee();
                }
                
                // Set employee fields
                employee.setEmployeeCode(employeeCodeField.getText().trim());
                employee.setFirstName(firstNameField.getText().trim());
                employee.setMiddleName(middleNameField.getText().trim());
                employee.setLastName(lastNameField.getText().trim());
                employee.setEmail(emailField.getText().trim());
                employee.setPhoneNumber(phoneNumberField.getText().trim());
                employee.setAddress(addressArea.getText().trim());
                employee.setEmploymentType(employmentTypeCombo.getValue());
                employee.setPosition(positionField.getText().trim());
                employee.setDepartment(departmentField.getText().trim());
                employee.setDateHired(dateHiredPicker.getValue());
                employee.setBaseRate(new BigDecimal(baseRateField.getText().trim()));
                employee.setRateType(rateTypeCombo.getValue());
                employee.setSssNumber(sssNumberField.getText().trim());
                employee.setPhilhealthNumber(philhealthNumberField.getText().trim());
                employee.setPagibigNumber(pagibigNumberField.getText().trim());
                employee.setTin(tinField.getText().trim());
                employee.setActive(activeCheckbox.isSelected());
                
                // Save to database
                if (employee.getEmployeeId() == null) {
                    employeeDAO.create(employee);
                    logger.info("Created new employee: {}", employee.getEmployeeCode());
                } else {
                    employeeDAO.update(employee);
                    logger.info("Updated employee: {}", employee.getEmployeeCode());
                }
                
                saveClicked = true;
                closeDialog();
                
            } catch (SQLException e) {
                logger.error("Failed to save employee", e);
                showError("Failed to save employee: " + e.getMessage());
            } catch (NumberFormatException e) {
                showError("Invalid base rate. Please enter a valid number.");
            }
        }
    }
    
    @FXML
    private void handleCancel() {
        closeDialog();
    }
    
    private boolean validateInput() {
        StringBuilder errors = new StringBuilder();
        
        if (employeeCodeField.getText().trim().isEmpty()) {
            errors.append("• Employee code is required\n");
        }
        
        if (firstNameField.getText().trim().isEmpty()) {
            errors.append("• First name is required\n");
        }
        
        if (positionField.getText().trim().isEmpty()) {
            errors.append("• Position is required\n");
        }
        
        if (dateHiredPicker.getValue() == null) {
            errors.append("• Date hired is required\n");
        }
        
        if (baseRateField.getText().trim().isEmpty()) {
            errors.append("• Base rate is required\n");
        } else {
            try {
                BigDecimal rate = new BigDecimal(baseRateField.getText().trim());
                if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.append("• Base rate must be greater than zero\n");
                }
            } catch (NumberFormatException e) {
                errors.append("• Base rate must be a valid number\n");
            }
        }
        
        if (employmentTypeCombo.getValue() == null) {
            errors.append("• Employment type is required\n");
        }
        
        if (rateTypeCombo.getValue() == null) {
            errors.append("• Rate type is required\n");
        }
        
        if (errors.length() > 0) {
            showError(errors.toString());
            return false;
        }
        
        return true;
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
    
    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
    
    public boolean isSaveClicked() {
        return saveClicked;
    }
}
