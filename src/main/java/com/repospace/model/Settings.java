package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class Settings implements Persistable {

    public static final List<String> THEMES = List.of("Light", "Dark");
    public static final List<String> LANGUAGES = List.of("English", "Afrikaans", "isiXhosa");

    private int settingsID;
    private int userID;
    private String theme = "Light";
    private String language = "English";
    private boolean notifications = true;

    public static Settings forUser(int userID) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            Settings found = load(c, userID);
            if (found != null) {
                return found;
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO settings (user_id, theme, language, notifications) VALUES (?, 'Light', 'English', 1)")) {
                ps.setInt(1, userID);
                ps.executeUpdate();
            }
            return load(c, userID);
        }
    }

    private static Settings load(Connection c, int userID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, user_id, theme, language, notifications FROM settings WHERE user_id = ?")) {
            ps.setInt(1, userID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Settings s = new Settings();
                    s.settingsID = rs.getInt("id");
                    s.userID = rs.getInt("user_id");
                    s.theme = rs.getString("theme");
                    s.language = rs.getString("language");
                    s.notifications = rs.getInt("notifications") == 1;
                    return s;
                }
            }
        }
        return null;
    }

    public void updateSettings() throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE settings SET theme = ?, language = ?, notifications = ? WHERE id = ?")) {
            ps.setString(1, theme);
            ps.setString(2, language);
            ps.setInt(3, notifications ? 1 : 0);
            ps.setInt(4, settingsID);
            ps.executeUpdate();
        }
    }

    public void changeTheme(String newTheme) throws SQLException {
        if (!THEMES.contains(newTheme)) {
            throw new IllegalArgumentException("Unknown theme: " + newTheme);
        }
        this.theme = newTheme;
        updateSettings();
    }

    public void changeLanguage(String newLanguage) throws SQLException {
        if (!LANGUAGES.contains(newLanguage)) {
            throw new IllegalArgumentException("Unknown language: " + newLanguage);
        }
        this.language = newLanguage;
        updateSettings();
    }

    public void toggleNotifications() throws SQLException {
        this.notifications = !this.notifications;
        updateSettings();
    }

    @Override
    public int getId() {
        return settingsID;
    }

    @Override
    public void save() throws SQLException {
        updateSettings();
    }

    public int getSettingsID() {
        return settingsID;
    }

    public int getUserID() {
        return userID;
    }

    public String getTheme() {
        return theme;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isNotifications() {
        return notifications;
    }

    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }
}
