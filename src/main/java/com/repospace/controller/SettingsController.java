package com.repospace.controller;

import com.repospace.Repospace;
import com.repospace.model.Settings;
import com.repospace.util.Session;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

public class SettingsController {

    @FXML
    private ComboBox<String> themeBox;
    @FXML
    private ComboBox<String> languageBox;
    @FXML
    private CheckBox notificationsBox;
    @FXML
    private Label messageLabel;

    private Settings settings;

    @FXML
    private void initialize() {
        themeBox.getItems().setAll(Settings.THEMES);
        languageBox.getItems().setAll(Settings.LANGUAGES);
        try {
            settings = Settings.forUser(Session.getCurrentUser().getUserID());
            themeBox.setValue(settings.getTheme());
            languageBox.setValue(settings.getLanguage());
            notificationsBox.setSelected(settings.isNotifications());
        } catch (SQLException e) {
            messageLabel.setText("Could not load settings: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        try {
            settings.changeTheme(themeBox.getValue());
            settings.changeLanguage(languageBox.getValue());
            settings.setNotifications(notificationsBox.isSelected());
            settings.updateSettings();
            Session.setTheme(settings.getTheme());
            Repospace.applyTheme(themeBox.getScene());
            messageLabel.setText("Settings saved.");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}
