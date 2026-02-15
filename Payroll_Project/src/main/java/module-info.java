module com.example.payroll_project {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.slf4j;
    requires jbcrypt;
    requires org.apache.commons.csv;


    opens com.example.payroll_project to javafx.fxml;
    exports com.example.payroll_project;
    exports com.example.payroll_project.controller;
    opens com.example.payroll_project.controller to javafx.fxml;
}