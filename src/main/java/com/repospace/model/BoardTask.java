package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BoardTask {

    public static final List<String> STATUSES = List.of("To do", "In progress", "Done");

    private int taskID;
    private int projectID;
    private Integer repoID;
    private String title;
    private String details;
    private String status = "To do";
    private LocalDateTime createdDate;

    private BoardTask() {
    }

    public static BoardTask add(Projects project, int userID, String title, String details, Integer linkedRepoID) throws SQLException {
        requireOwner(project, userID);
        String t = title == null ? "" : title.trim();
        String d = details == null ? "" : details.trim();
        if (t.isEmpty() || t.length() > 100) {
            throw new IllegalArgumentException("A card needs a title of 1-100 characters.");
        }
        if (d.length() > 1000) {
            throw new IllegalArgumentException("The details can be at most 1000 characters.");
        }
        if (linkedRepoID != null) {
            Repository repo = Repository.findById(linkedRepoID);
            if (repo == null || !repo.isOwnedBy(project.getUserID())) {
                throw new IllegalArgumentException("A card can only be linked to one of your own repositories.");
            }
        }
        BoardTask task = new BoardTask();
        task.projectID = project.getProjectID();
        task.repoID = linkedRepoID;
        task.title = t;
        task.details = d;
        task.createdDate = DateUtil.now();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO board_tasks (project_id, repo_id, title, details, status, created_date) VALUES (?, ?, ?, ?, 'To do', ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, task.projectID);
            if (linkedRepoID == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, linkedRepoID);
            }
            ps.setString(3, t);
            ps.setString(4, d);
            ps.setString(5, DateUtil.toDb(task.createdDate));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                task.taskID = keys.getInt(1);
            }
        }
        return task;
    }

    public static List<BoardTask> forProject(Projects project, int viewerID) throws SQLException {
        if (!project.canBeViewedBy(viewerID)) {
            throw new IllegalStateException("This project is private - only its owner can see the board.");
        }
        List<BoardTask> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, project_id, repo_id, title, details, status, created_date FROM board_tasks WHERE project_id = ? ORDER BY id")) {
            ps.setInt(1, project.getProjectID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BoardTask t = new BoardTask();
                    t.taskID = rs.getInt("id");
                    t.projectID = rs.getInt("project_id");
                    int linked = rs.getInt("repo_id");
                    t.repoID = rs.wasNull() ? null : linked;
                    t.title = rs.getString("title");
                    t.details = rs.getString("details");
                    t.status = rs.getString("status");
                    t.createdDate = DateUtil.fromDb(rs.getString("created_date"));
                    list.add(t);
                }
            }
        }
        return list;
    }

    public void move(Projects project, int userID, String newStatus) throws SQLException {
        requireOwner(project, userID);
        checkProject(project);
        if (!STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Unknown column: " + newStatus);
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE board_tasks SET status = ? WHERE id = ? AND project_id = ?")) {
            ps.setString(1, newStatus);
            ps.setInt(2, taskID);
            ps.setInt(3, projectID);
            ps.executeUpdate();
        }
        this.status = newStatus;
    }

    public void delete(Projects project, int userID) throws SQLException {
        requireOwner(project, userID);
        checkProject(project);
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM board_tasks WHERE id = ? AND project_id = ?")) {
            ps.setInt(1, taskID);
            ps.setInt(2, projectID);
            ps.executeUpdate();
        }
    }

    public static int percentDone(List<BoardTask> tasks) {
        if (tasks.isEmpty()) {
            return 0;
        }
        long done = tasks.stream().filter(t -> "Done".equals(t.status)).count();
        return (int) Math.round(100.0 * done / tasks.size());
    }

    private static void requireOwner(Projects project, int userID) {
        if (project == null || project.getUserID() != userID) {
            throw new IllegalStateException("Only the owner of the project can change its board.");
        }
    }

    private void checkProject(Projects project) {
        if (project.getProjectID() != projectID) {
            throw new IllegalArgumentException("This card belongs to another project.");
        }
    }

    public int getTaskID() {
        return taskID;
    }

    public int getProjectID() {
        return projectID;
    }

    public Integer getRepoID() {
        return repoID;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
