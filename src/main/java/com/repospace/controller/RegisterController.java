package com.repospace.controller;

import com.repospace.Repospace;
import com.repospace.model.User;
import java.sql.SQLException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmField;
    @FXML
    private Label messageLabel;

    @FXML
    private void handleRegister(ActionEvent event) {
        if (!passwordField.getText().equals(confirmField.getText())) {
            messageLabel.setText("The two passwords do not match.");
            return;
        }
        try {
            User created = User.register(usernameField.getText(), emailField.getText(), passwordField.getText());
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Account '" + created.getUsername() + "' was created. You can now log in.");
            alert.setHeaderText("Welcome to Repospace");
            alert.showAndWait();
            Repospace.switchScene("login.fxml", "Repospace - Log in");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        Repospace.switchScene("login.fxml", "Repospace - Log in");
    }
}
