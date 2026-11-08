package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Projects implements Persistable {

    public static final List<String> STATUSES = List.of("Planning", "In progress", "On hold", "Completed");
    public static final List<String> VISIBILITIES = List.of("private", "public");

    private int projectID;
    private int userID;
    private String name;
    private String description;
    private LocalDateTime createdDate;
    private String status = "Planning";
    private String visibility = "private";

    public Projects(int userID, String name, String description) {
        this.userID = userID;
        this.name = name == null ? "" : name.trim();
        this.description = description == null ? "" : description.trim();
    }

    public Projects(int userID, String name, String description, String visibility) {
        this(userID, name, description);
        this.visibility = checkVisibility(visibility);
    }

    private Projects() {
    }

    public void createProject() throws SQLException {
        validateName();
        this.createdDate = DateUtil.now();
        String sql = "INSERT INTO projects (user_id, name, description, created_date, status, visibility) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userID);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, DateUtil.toDb(createdDate));
            ps.setString(5, status);
            ps.setString(6, visibility);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                this.projectID = keys.getInt(1);
            }
        }
        ActivityLogger.info("Project created: '" + name + "' (#" + projectID + ")");
    }

    private static String checkVisibility(String value) {
        String v = value == null || value.isBlank() ? "private" : value.trim().toLowerCase();
        if (!VISIBILITIES.contains(v)) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'.");
        }
        return v;
    }

    private void validateName() {
        if (name.isEmpty() || name.length() > 60) {
            throw new IllegalArgumentException("A project needs a name of 1-60 characters.");
        }
    }

    @Override
    public int getId() {
        return projectID;
    }

    @Override
    public void save() throws SQLException {
        if (projectID == 0) {
            createProject();
            return;
        }
        validateName();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE projects SET name = ?, description = ?, status = ?, visibility = ? WHERE id = ? AND user_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, status);
            ps.setString(4, visibility);
            ps.setInt(5, projectID);
            ps.setInt(6, userID);
            ps.executeUpdate();
        }
    }

    public void viewProjects() {
        System.out.println("#" + projectID + "  " + name + "  [" + status + ", " + visibility + "]  created " + DateUtil.format(createdDate));
        if (!description.isEmpty()) {
            System.out.println("     " + description);
        }
    }

    public void deleteProject() throws SQLException {
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM projects WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, projectID);
            ps.setInt(2, userID);
            ps.executeUpdate();
        }
        ActivityLogger.info("Project deleted: '" + name + "' (#" + projectID + ")");
    }

    public void updateStatus(String newStatus) throws SQLException {
        if (!STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Unknown status: " + newStatus);
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE projects SET status = ? WHERE id = ? AND user_id = ?")) {
            ps.setString(1, newStatus);
            ps.setInt(2, projectID);
            ps.setInt(3, userID);
            ps.executeUpdate();
        }
        this.status = newStatus;
    }

    public void changeVisibility(String newVisibility) throws SQLException {
        String value = checkVisibility(newVisibility);
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE projects SET visibility = ? WHERE id = ? AND user_id = ?")) {
            ps.setString(1, value);
            ps.setInt(2, projectID);
            ps.setInt(3, userID);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Only the owner can change this project.");
            }
        }
        this.visibility = value;
        ActivityLogger.info("Project '" + name + "' (#" + projectID + ") is now " + value);
    }

    public static List<Projects> findByUser(int userID) throws SQLException {
        return query("SELECT id, user_id, name, description, created_date, status, visibility FROM projects WHERE user_id = ? ORDER BY id DESC", userID);
    }

    public static List<Projects> findPublicByUser(int userID) throws SQLException {
        return query("SELECT id, user_id, name, description, created_date, status, visibility FROM projects WHERE user_id = ? AND visibility = 'public' ORDER BY id DESC", userID);
    }

    public static Projects findById(int id) throws SQLException {
        List<Projects> list = query("SELECT id, user_id, name, description, created_date, status, visibility FROM projects WHERE id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean canBeViewedBy(int viewerID) {
        return viewerID == userID || isPublic();
    }

    private static List<Projects> query(String sql, int userID) throws SQLException {
        List<Projects> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Projects p = new Projects();
                    p.projectID = rs.getInt("id");
                    p.userID = rs.getInt("user_id");
                    p.name = rs.getString("name");
                    p.description = rs.getString("description");
                    p.createdDate = DateUtil.fromDb(rs.getString("created_date"));
                    p.status = rs.getString("status");
                    p.visibility = rs.getString("visibility");
                    list.add(p);
                }
            }
        }
        return list;
    }

    public int getProjectID() {
        return projectID;
    }

    public int getUserID() {
        return userID;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public String getStatus() {
        return status;
    }

    public String getVisibility() {
        return visibility;
    }

    public boolean isPublic() {
        return "public".equals(visibility);
    }
}
