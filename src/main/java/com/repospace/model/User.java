package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.PasswordUtil;
import com.repospace.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class User {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private int userID;
    private String username;
    private String email;
    private String password;

    public User() {
    }

    public User(int userID, String username, String email) {
        this.userID = userID;
        this.username = username;
        this.email = email;
    }

    public boolean login(String email, String password) throws SQLException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            return false;
        }
        String sql = "SELECT id, username, email, password_hash, salt FROM users WHERE lower(email) = lower(?) OR lower(username) = lower(?)";
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            String identifier = email.trim();
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && PasswordUtil.verify(password, rs.getString("salt"), rs.getString("password_hash"))) {
                    this.userID = rs.getInt("id");
                    this.username = rs.getString("username");
                    this.email = rs.getString("email");
                    this.password = rs.getString("password_hash");
                    return true;
                }
            }
        }
        return false;
    }

    public void logout() {
        ActivityLogger.info("User '" + username + "' logged out");
        Session.clear();
    }

    public void updateProfile() throws SQLException {
        validateUsernameAndEmail(username, email);
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            String check = "SELECT 1 FROM users WHERE (lower(username) = lower(?) OR lower(email) = lower(?)) AND id <> ?";
            try (PreparedStatement ps = c.prepareStatement(check)) {
                ps.setString(1, username);
                ps.setString(2, email);
                ps.setInt(3, userID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("That username or e-mail is already in use.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET username = ?, email = ? WHERE id = ?")) {
                ps.setString(1, username.trim());
                ps.setString(2, email.trim());
                ps.setInt(3, userID);
                ps.executeUpdate();
            }
        }
        ActivityLogger.info("User #" + userID + " updated their account details");
    }

    public static User register(String username, String email, String password) throws SQLException {
        validateUsernameAndEmail(username, email);
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("The password must be at least 8 characters long.");
        }
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(password, salt);
        int newId;
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE lower(username) = lower(?) OR lower(email) = lower(?)")) {
                ps.setString(1, username.trim());
                ps.setString(2, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("That username or e-mail is already registered.");
                    }
                }
            }
            String insert = "INSERT INTO users (username, email, password_hash, salt, created_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username.trim());
                ps.setString(2, email.trim());
                ps.setString(3, hash);
                ps.setString(4, salt);
                ps.setString(5, DateUtil.toDb(DateUtil.now()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    newId = keys.getInt(1);
                }
            }
        }
        Profile.forUser(newId);
        Settings.forUser(newId);
        ActivityLogger.info("New user registered: '" + username.trim() + "'");
        User user = new User(newId, username.trim(), email.trim());
        user.password = hash;
        return user;
    }

    public static User findById(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, username, email, password_hash FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"));
                    user.password = rs.getString("password_hash");
                    return user;
                }
            }
        }
        return null;
    }

    public static User findByUsername(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return null;
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, username FROM users WHERE lower(username) = lower(?)")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getInt("id"), rs.getString("username"), "");
                }
            }
        }
        return null;
    }

    public static List<User> searchUsers(String text) throws SQLException {
        List<User> found = new ArrayList<>();
        String term = text == null ? "" : text.trim();
        if (term.isEmpty()) {
            return found;
        }
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, username FROM users WHERE username LIKE ? ESCAPE '\\' ORDER BY username COLLATE NOCASE LIMIT 50")) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(new User(rs.getInt("id"), rs.getString("username"), ""));
                }
            }
        }
        return found;
    }

    private static void validateUsernameAndEmail(String username, String email) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("The username must be 3-30 characters with NO spaces: only letters, digits, '.', '_' or '-' (for example: khuthadzo.makuya).");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Please enter a valid e-mail address.");
        }
    }

    public int getUserID() {
        return userID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }
}
