package com.repospace.util;

import com.repospace.database.DatabaseConnection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ActivityLogger {

    private ActivityLogger() {
    }

    public static synchronized void info(String message) {
        write("INFO ", message);
    }

    public static synchronized void error(String message, Throwable error) {
        StringWriter trace = new StringWriter();
        if (error != null) {
            error.printStackTrace(new PrintWriter(trace));
        }
        write("ERROR", message + (error == null ? "" : " | " + trace));
    }

    public static void logLoginAttempt(String identifier, boolean success) {
        String sql = "INSERT INTO login_attempts (identifier, success, attempted_at) VALUES (?, ?, ?)";
        try (Connection c = DatabaseConnection.getLoginDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifier == null ? "" : identifier);
            ps.setInt(2, success ? 1 : 0);
            ps.setString(3, DateUtil.toDb(DateUtil.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Could not record login attempt", e);
        }
        info("Login " + (success ? "succeeded" : "FAILED") + " for '" + identifier + "'");
    }

    private static void write(String level, String message) {
        try {
            Path dir = DatabaseConnection.dataDir().resolve("logs");
            Files.createDirectories(dir);
            String line = DateUtil.toDb(DateUtil.now()) + " [" + level + "] " + message + System.lineSeparator();
            Files.writeString(dir.resolve("repospace.log"), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Logging failed: " + e.getMessage());
        }
    }
}
