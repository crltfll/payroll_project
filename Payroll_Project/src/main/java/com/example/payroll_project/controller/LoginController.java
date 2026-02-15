package com.example.payroll_project.controller;

import com.example.payroll_project.PayrollApplication;
import com.example.payroll_project.model.User;
import com.example.payroll_project.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Login Controller (SEC1: Authentication & Authorization)
 * 
 */
public class LoginController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Label errorLabel;
    
    @FXML
    private ProgressIndicator loadingIndicator;
    
    @FXML
    private CheckBox rememberMeCheckBox;
    
    private UserService userService;
    private static User currentUser;
    
    @FXML
    public void initialize() {
        userService = new UserService();
        loadingIndicator.setVisible(false);
        errorLabel.setVisible(false);
        
        // Enter key to login
        passwordField.setOnKeyPressed(this::handleKeyPress);
        usernameField.setOnKeyPressed(this::handleKeyPress);
        
        // Focus on username field
        javafx.application.Platform.runLater(() -> usernameField.requestFocus());
    }
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        // Validation
        if (username.isEmpty()) {
            showError("Please enter your username");
            usernameField.requestFocus();
            return;
        }
        
        if (password.isEmpty()) {
            showError("Please enter your password");
            passwordField.requestFocus();
            return;
        }
        
        // Show loading
        setLoading(true);
        errorLabel.setVisible(false);
        
        // Authenticate in background thread
        new Thread(() -> {
            try {
                Optional<User> userOpt = userService.authenticate(username, password);
                
                javafx.application.Platform.runLater(() -> {
                    setLoading(false);
                    
                    if (userOpt.isPresent()) {
                        currentUser = userOpt.get();
                        logger.info("User logged in: {}", currentUser.getUsername());
                        
                        try {
                            PayrollApplication.showDashboard();
                        } catch (IOException e) {
                            logger.error("Failed to load dashboard", e);
                            showError("Failed to load dashboard: " + e.getMessage());
                        }
                    } else {
                        showError("Invalid username or password");
                        passwordField.clear();
                        passwordField.requestFocus();
                    }
                });
                
            } catch (Exception e) {
                logger.error("Login error", e);
                javafx.application.Platform.runLater(() -> {
                    setLoading(false);
                    showError("Login failed: " + e.getMessage());
                });
            }
        }).start();
    }
    
    @FXML
    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            handleLogin();
        }
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
    
    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loginButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
    }
    
    public static User getCurrentUser() {
        return currentUser;
    }
    
    public static void logout() {
        currentUser = null;
        try {
            PayrollApplication.showLoginScreen();
        } catch (IOException e) {
            Logger logger = LoggerFactory.getLogger(LoginController.class);
            logger.error("Failed to show login screen", e);
        }
    }
}
