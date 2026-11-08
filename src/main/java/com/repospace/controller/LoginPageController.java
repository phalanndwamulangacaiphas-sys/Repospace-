package com.repospace.controller;

import com.repospace.Repospace;
import com.repospace.model.Settings;
import com.repospace.model.User;
import com.repospace.util.ActivityLogger;
import com.repospace.util.Session;
import java.sql.SQLException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginPageController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;

    private String email;
    private String password;
    private boolean isAuthenticated;
    private User authenticatedUser;
    private boolean databaseFailed;

    @FXML
    private void handleLoginButtonAction(ActionEvent event) {
        if (!validateInput()) {
            return;
        }
        if (authenticate()) {
            createSession();
            switchToHomePage(event);
        } else if (!databaseFailed) {
            showAlert("Login failed", "Invalid username/e-mail or password.");
        }
    }

    @FXML
    private void handleRegisterLinkAction(ActionEvent event) {
        Repospace.switchScene("register.fxml", "Repospace - Create account");
    }

    public boolean validateInput() {
        email = usernameField.getText() == null ? "" : usernameField.getText().trim();
        password = passwordField.getText() == null ? "" : passwordField.getText();
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Missing details", "Please enter your username (or e-mail) and your password.");
            return false;
        }
        return true;
    }

    public boolean authenticate() {
        isAuthenticated = authenticateUser(email, password);
        return isAuthenticated;
    }

    public boolean authenticateUser(String identifier, String plainPassword) {
        databaseFailed = false;
        try {
            User user = new User();
            boolean ok = user.login(identifier, plainPassword);
            ActivityLogger.logLoginAttempt(identifier, ok);
            if (ok) {
                authenticatedUser = user;
            }
            return ok;
        } catch (SQLException e) {
            databaseFailed = true;
            ActivityLogger.error("Login failed because of a database error", e);
            showAlert("Database error", e.getMessage());
            return false;
        }
    }

    public void createSession() {
        Session.setCurrentUser(authenticatedUser);
        try {
            Session.setTheme(Settings.forUser(authenticatedUser.getUserID()).getTheme());
        } catch (SQLException e) {
            ActivityLogger.error("Could not load the theme setting", e);
        }
        passwordField.clear();
        password = null;
    }

    public void switchToHomePage(ActionEvent event) {
        Repospace.switchScene("home.fxml", "Repospace - Home");
    }

    public void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
