package com.example.payroll_project.controller;

import com.example.payroll_project.dao.EmployeeDAO;
import com.example.payroll_project.model.Employee;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Employees Controller (F9: Employee Management System)
 * Handles employee CRUD operations and display
 */
public class EmployeesController {
    
    private static final Logger logger = LoggerFactory.getLogger(EmployeesController.class);
    private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    @FXML
    private TextField searchField;
    
    @FXML
    private ComboBox<Employee.EmploymentType> employmentTypeFilter;
    
    @FXML
    private ComboBox<String> departmentFilter;
    
    @FXML
    private CheckBox activeOnlyCheckbox;
    
    @FXML
    private Label employeeCountLabel;
    
    @FXML
    private TableView<Employee> employeeTable;
    
    @FXML
    private TableColumn<Employee, String> codeColumn;
    
    @FXML
    private TableColumn<Employee, String> nameColumn;
    
    @FXML
    private TableColumn<Employee, String> positionColumn;
    
    @FXML
    private TableColumn<Employee, String> departmentColumn;
    
    @FXML
    private TableColumn<Employee, String> employmentTypeColumn;
    
    @FXML
    private TableColumn<Employee, String> baseRateColumn;
    
    @FXML
    private TableColumn<Employee, String> dateHiredColumn;
    
    @FXML
    private TableColumn<Employee, String> statusColumn;
    
    @FXML
    private TableColumn<Employee, Void> actionsColumn;
    
    @FXML
    private Label pageLabel;
    
    private EmployeeDAO employeeDAO;
    private ObservableList<Employee> employees;
    
    @FXML
    public void initialize() {
        employeeDAO = new EmployeeDAO();
        employees = FXCollections.observableArrayList();
        
        // Initialize filters
        initializeFilters();
        
        // Setup table columns
        setupTableColumns();
        
        // Load employees
        loadEmployees();
    }
    
    /**
     * Initialize filter combo boxes
     */
    private void initializeFilters() {
        // Employment type filter
        employmentTypeFilter.getItems().addAll(Employee.EmploymentType.values());
        
        // Department filter - would be loaded from database in production
        departmentFilter.getItems().addAll(
            "All Departments",
            "Administration",
            "Faculty",
            "IT Department",
            "Finance",
            "Human Resources"
        );
        departmentFilter.setValue("All Departments");
    }
    
    /**
     * Setup table columns
     */
    private void setupTableColumns() {
        // Employee Code
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("employeeCode"));
        
        // Full Name
        nameColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFullName())
        );
        
        // Position
        positionColumn.setCellValueFactory(new PropertyValueFactory<>("position"));
        
        // Department
        departmentColumn.setCellValueFactory(new PropertyValueFactory<>("department"));
        
        // Employment Type
        employmentTypeColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getEmploymentType().name()
                .replace("_", " "))
        );
        
        // Base Rate
        baseRateColumn.setCellValueFactory(cellData -> {
            Employee emp = cellData.getValue();
            String rate = currencyFormat.format(emp.getBaseRate()) + 
                         "/" + emp.getRateType().name().toLowerCase();
            return new SimpleStringProperty(rate);
        });
        
        // Date Hired
        dateHiredColumn.setCellValueFactory(cellData -> {
            LocalDate date = cellData.getValue().getDateHired();
            return new SimpleStringProperty(date != null ? date.format(dateFormatter) : "");
        });
        
        // Status
        statusColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().isActive() ? "Active" : "Inactive")
        );
        statusColumn.setCellFactory(column -> new TableCell<Employee, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if (item.equals("Active")) {
                        badge.getStyleClass().add("badge-success");
                    } else {
                        badge.getStyleClass().add("badge-error");
                    }
                    setGraphic(badge);
                }
            }
        });
        
        // Actions
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final HBox pane = new HBox(5, editButton, deleteButton);
            
            {
                editButton.getStyleClass().add("button-secondary");
                editButton.setStyle("-fx-padding: 4px 12px; -fx-font-size: 11px;");
                deleteButton.getStyleClass().add("button-danger");
                deleteButton.setStyle("-fx-padding: 4px 12px; -fx-font-size: 11px;");
                
                editButton.setOnAction(event -> {
                    Employee employee = getTableView().getItems().get(getIndex());
                    handleEditEmployee(employee);
                });
                
                deleteButton.setOnAction(event -> {
                    Employee employee = getTableView().getItems().get(getIndex());
                    handleDeleteEmployee(employee);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }
    
    /**
     * Load employees from database
     */
    private void loadEmployees() {
        new Thread(() -> {
            try {
                boolean activeOnly = activeOnlyCheckbox.isSelected();
                List<Employee> employeeList = employeeDAO.findAll(activeOnly);
                
                Platform.runLater(() -> {
                    employees.setAll(employeeList);
                    employeeTable.setItems(employees);
                    updateEmployeeCount();
                });
                
            } catch (SQLException e) {
                logger.error("Failed to load employees", e);
                Platform.runLater(() -> showErrorAlert("Database Error", 
                    "Failed to load employees: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Update employee count label
     */
    private void updateEmployeeCount() {
        int count = employees.size();
        employeeCountLabel.setText(count + " employee" + (count != 1 ? "s" : ""));
    }
    
    @FXML
    private void handleSearch() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadEmployees();
            return;
        }
        
        new Thread(() -> {
            try {
                List<Employee> results = employeeDAO.search(searchTerm);
                
                Platform.runLater(() -> {
                    employees.setAll(results);
                    employeeTable.setItems(employees);
                    updateEmployeeCount();
                });
                
            } catch (SQLException e) {
                logger.error("Search failed", e);
            }
        }).start();
    }
    
    @FXML
    private void handleFilter() {
        // Implementation would filter based on selected filters
        loadEmployees();
    }
    
    @FXML
    private void handleClearFilters() {
        searchField.clear();
        employmentTypeFilter.setValue(null);
        departmentFilter.setValue("All Departments");
        activeOnlyCheckbox.setSelected(true);
        loadEmployees();
    }
    
    @FXML
    private void handleAddEmployee() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/employee-form.fxml")
            );
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm()
            );
            
            Stage stage = new Stage();
            stage.setTitle("Add Employee");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();
            
            // Refresh after closing
            loadEmployees();
            
        } catch (IOException e) {
            logger.error("Failed to open add employee form", e);
            showErrorAlert("Error", "Failed to open form: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleEditEmployee(Employee employee) {
        // Implementation would open edit form
        showInfoAlert("Edit Employee", "Edit functionality will open form for: " + 
                     employee.getFullName());
    }
    
    @FXML
    private void handleDeleteEmployee(Employee employee) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Employee");
        confirmation.setHeaderText("Delete " + employee.getFullName() + "?");
        confirmation.setContentText("This will deactivate the employee. This action can be undone.");
        
        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        employeeDAO.delete(employee.getEmployeeId());
                        Platform.runLater(() -> {
                            showInfoAlert("Success", "Employee deactivated successfully");
                            loadEmployees();
                        });
                    } catch (SQLException e) {
                        logger.error("Failed to delete employee", e);
                        Platform.runLater(() -> 
                            showErrorAlert("Error", "Failed to delete employee: " + e.getMessage())
                        );
                    }
                }).start();
            }
        });
    }
    
    @FXML
    private void handleExport() {
        showInfoAlert("Export", "Export functionality will be implemented");
    }
    
    @FXML
    private void handleRefresh() {
        loadEmployees();
    }
    
    @FXML
    private void handlePreviousPage() {
        // Pagination implementation
        //TODO
    }
    
    @FXML
    private void handleNextPage() {
        // Pagination implementation
        //TODO
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
