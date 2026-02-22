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

/**
 * Employees Controller (F9: Employee Management System)
 * Handles CRUD operations with working edit functionality.
 */
public class EmployeesController {

    private static final Logger logger = LoggerFactory.getLogger(EmployeesController.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @FXML private TextField searchField;
    @FXML private ComboBox<Employee.EmploymentType> employmentTypeFilter;
    @FXML private ComboBox<String> departmentFilter;
    @FXML private CheckBox activeOnlyCheckbox;
    @FXML private Label employeeCountLabel;
    @FXML private TableView<Employee> employeeTable;
    @FXML private TableColumn<Employee, String> codeColumn;
    @FXML private TableColumn<Employee, String> nameColumn;
    @FXML private TableColumn<Employee, String> positionColumn;
    @FXML private TableColumn<Employee, String> departmentColumn;
    @FXML private TableColumn<Employee, String> employmentTypeColumn;
    @FXML private TableColumn<Employee, String> baseRateColumn;
    @FXML private TableColumn<Employee, String> dateHiredColumn;
    @FXML private TableColumn<Employee, String> statusColumn;
    @FXML private TableColumn<Employee, Void>   actionsColumn;
    @FXML private Label pageLabel;

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ObservableList<Employee> employees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        initializeFilters();
        setupTableColumns();
        loadEmployees();
    }

    private void initializeFilters() {
        employmentTypeFilter.getItems().addAll(Employee.EmploymentType.values());
        departmentFilter.getItems().addAll(
                "All Departments", "Administration", "Faculty",
                "IT Department", "Finance", "Human Resources");
        departmentFilter.setValue("All Departments");
    }

    private void setupTableColumns() {
        codeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmployeeCode()));
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        positionColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPosition()));
        departmentColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepartment()));
        employmentTypeColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmploymentType().name().replace("_", " ")));
        baseRateColumn.setCellValueFactory(c -> {
            Employee e = c.getValue();
            return new SimpleStringProperty(
                    CURRENCY.format(e.getBaseRate()) + "/" + e.getRateType().name().toLowerCase());
        });
        dateHiredColumn.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getDateHired();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "");
        });

        // Status badge
        statusColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Inactive"));
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().addAll("badge",
                        "Active".equals(item) ? "badge-success" : "badge-error");
                setGraphic(badge);
            }
        });

        // Actions column with working Edit
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");

            {
                editBtn.getStyleClass().add("button-secondary");
                editBtn.setStyle("-fx-padding:4px 12px;-fx-font-size:11px;");
                deleteBtn.getStyleClass().add("button-danger");
                deleteBtn.setStyle("-fx-padding:4px 12px;-fx-font-size:11px;");

                editBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    openEmployeeForm(emp);
                });
                deleteBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    handleDeleteEmployee(emp);
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : new HBox(5, editBtn, deleteBtn));
            }
        });
    }

    // -----------------------------------------------------------------------
    // CRUD operations
    // -----------------------------------------------------------------------

    @FXML
    private void handleAddEmployee() {
        openEmployeeForm(null);
    }

    /**
     * Opens the employee form dialog.
     * @param employee null = add new, non-null = edit existing
     */
    private void openEmployeeForm(Employee employee) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/employee-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            EmployeeFormController ctrl = loader.getController();
            if (employee != null) {
                ctrl.setEmployee(employee);
            }

            Stage stage = new Stage();
            stage.setTitle(employee == null ? "Add Employee" : "Edit Employee – " + employee.getFullName());
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            // Refresh table regardless (controller auto-saves on "Save")
            loadEmployees();

        } catch (IOException e) {
            logger.error("Failed to open employee form", e);
            showErrorAlert("Error", "Failed to open form: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteEmployee(Employee employee) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Employee");
        confirm.setHeaderText("Deactivate " + employee.getFullName() + "?");
        confirm.setContentText("The employee record will be deactivated (soft delete). "
                + "Payroll history is preserved.");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        employeeDAO.delete(employee.getEmployeeId());
                        Platform.runLater(() -> {
                            showInfoAlert("Success", employee.getFullName() + " deactivated.");
                            loadEmployees();
                        });
                    } catch (SQLException ex) {
                        logger.error("Delete failed", ex);
                        Platform.runLater(() ->
                                showErrorAlert("Error", "Failed to deactivate: " + ex.getMessage()));
                    }
                }).start();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Loading & filtering
    // -----------------------------------------------------------------------

    private void loadEmployees() {
        new Thread(() -> {
            try {
                boolean activeOnly = activeOnlyCheckbox.isSelected();
                List<Employee> list = employeeDAO.findAll(activeOnly);
                Platform.runLater(() -> {
                    employees.setAll(list);
                    employeeTable.setItems(employees);
                    updateCount();
                });
            } catch (SQLException e) {
                logger.error("Load failed", e);
                Platform.runLater(() ->
                        showErrorAlert("Database Error", "Failed to load employees: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleSearch() {
        String term = searchField.getText().trim();
        if (term.isEmpty()) { loadEmployees(); return; }
        new Thread(() -> {
            try {
                List<Employee> results = employeeDAO.search(term);
                Platform.runLater(() -> {
                    employees.setAll(results);
                    employeeTable.setItems(employees);
                    updateCount();
                });
            } catch (SQLException e) {
                logger.error("Search failed", e);
            }
        }).start();
    }

    @FXML private void handleFilter()       { loadEmployees(); }
    @FXML private void handleRefresh()      { loadEmployees(); }
    @FXML private void handlePreviousPage() { /* pagination – TODO */ }
    @FXML private void handleNextPage()     { /* pagination – TODO */ }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        employmentTypeFilter.setValue(null);
        departmentFilter.setValue("All Departments");
        activeOnlyCheckbox.setSelected(true);
        loadEmployees();
    }

    @FXML
    private void handleExport() {
        showInfoAlert("Export", "Export to CSV will be implemented in a future update.");
    }

    private void updateCount() {
        int n = employees.size();
        employeeCountLabel.setText(n + " employee" + (n != 1 ? "s" : ""));
        pageLabel.setText("Page 1 of 1");
    }

    // -----------------------------------------------------------------------
    private void showErrorAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private void showInfoAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
