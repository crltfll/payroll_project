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

/**
 * Employee Form Controller for Add/Edit Employee
 * Handles employee creation and modification (F9)
 *
 * FIXES APPLIED:
 * 1. getText() NPE — replaced every raw .getText().trim() with null-safe
 *    helper getText(field) so a missing fx:id never crashes the app.
 * 2. setEmployee() NPE — all setText() calls now guard against null getters
 *    (e.g. getBaseRate() returning null after DB corruption).
 * 3. baseRateField.setText() — uses BigDecimal safely; defaults to empty
 *    string so the user is prompted to enter a valid rate.
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
    private Employee employee;
    private boolean saveClicked = false;

    // -----------------------------------------------------------------------
    // Null-safe helpers
    // -----------------------------------------------------------------------

    /** Returns trimmed text or "" when the field or its value is null. */
    private String getText(TextField field) {
        if (field == null) return "";
        String val = field.getText();
        return val != null ? val.trim() : "";
    }

    /** Returns trimmed text or "" for a TextArea. */
    private String getText(TextArea area) {
        if (area == null) return "";
        String val = area.getText();
        return val != null ? val.trim() : "";
    }

    /** Sets text safely — converts null value to empty string. */
    private void setText(TextField field, String value) {
        if (field != null) field.setText(value != null ? value : "");
    }

    /** Sets text safely for a TextArea. */
    private void setText(TextArea area, String value) {
        if (area != null) area.setText(value != null ? value : "");
    }

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    @FXML
    public void initialize() {
        employeeDAO = new EmployeeDAO();

        employmentTypeCombo.getItems().addAll(Employee.EmploymentType.values());
        rateTypeCombo.getItems().addAll(Employee.RateType.values());

        employmentTypeCombo.setValue(Employee.EmploymentType.FULL_TIME);
        rateTypeCombo.setValue(Employee.RateType.MONTHLY);
        activeCheckbox.setSelected(true);

        errorLabel.setVisible(false);
    }

    // -----------------------------------------------------------------------
    // Set employee for editing
    // -----------------------------------------------------------------------

    public void setEmployee(Employee employee) {
        this.employee = employee;

        if (employee != null) {
            setText(employeeCodeField,      employee.getEmployeeCode());
            setText(firstNameField,         employee.getFirstName());
            setText(middleNameField,        employee.getMiddleName());
            setText(lastNameField,          employee.getLastName());
            setText(emailField,             employee.getEmail());
            setText(phoneNumberField,       employee.getPhoneNumber());
            setText(addressArea,            employee.getAddress());
            setText(positionField,          employee.getPosition());
            setText(departmentField,        employee.getDepartment());
            setText(sssNumberField,         employee.getSssNumber());
            setText(philhealthNumberField,  employee.getPhilhealthNumber());
            setText(pagibigNumberField,     employee.getPagibigNumber());
            setText(tinField,               employee.getTin());

            // Base rate — guard against null or ZERO left over from DB fix
            BigDecimal rate = employee.getBaseRate();
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                setText(baseRateField, rate.toPlainString());
            } else {
                setText(baseRateField, ""); // force user to re-enter correct rate
            }

            if (employee.getEmploymentType() != null)
                employmentTypeCombo.setValue(employee.getEmploymentType());

            if (employee.getRateType() != null)
                rateTypeCombo.setValue(employee.getRateType());

            if (employee.getDateHired() != null)
                dateHiredPicker.setValue(employee.getDateHired());

            activeCheckbox.setSelected(employee.isActive());
        }
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    @FXML
    private void handleSave() {
        if (validateInput()) {
            try {
                if (employee == null) {
                    employee = new Employee();
                }

                employee.setEmployeeCode(getText(employeeCodeField));
                employee.setFirstName(getText(firstNameField));
                employee.setMiddleName(getText(middleNameField));
                employee.setLastName(getText(lastNameField));
                employee.setEmail(getText(emailField));
                employee.setPhoneNumber(getText(phoneNumberField));
                employee.setAddress(getText(addressArea));
                employee.setEmploymentType(employmentTypeCombo.getValue());
                employee.setPosition(getText(positionField));
                employee.setDepartment(getText(departmentField));
                employee.setDateHired(dateHiredPicker.getValue());
                employee.setBaseRate(new BigDecimal(getText(baseRateField)));
                employee.setRateType(rateTypeCombo.getValue());
                employee.setSssNumber(getText(sssNumberField));
                employee.setPhilhealthNumber(getText(philhealthNumberField));
                employee.setPagibigNumber(getText(pagibigNumberField));
                employee.setTin(getText(tinField));
                employee.setActive(activeCheckbox.isSelected());

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

    // -----------------------------------------------------------------------
    // Cancel
    // -----------------------------------------------------------------------

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private boolean validateInput() {
        StringBuilder errors = new StringBuilder();

        if (getText(employeeCodeField).isEmpty()) {
            errors.append("• Employee code is required\n");
        }

        if (getText(firstNameField).isEmpty()) {
            errors.append("• First name is required\n");
        }

        if (getText(positionField).isEmpty()) {
            errors.append("• Position is required\n");
        }

        if (dateHiredPicker.getValue() == null) {
            errors.append("• Date hired is required\n");
        }

        String rateText = getText(baseRateField);
        if (rateText.isEmpty()) {
            errors.append("• Base rate is required\n");
        } else {
            try {
                BigDecimal rate = new BigDecimal(rateText);
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

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

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
