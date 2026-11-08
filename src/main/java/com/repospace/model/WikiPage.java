package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WikiPage {

    public static final int MAX_BODY = 20000;

    private int pageID;
    private int repoID;
    private String title;
    private String body;
    private int revision;
    private String updatedBy;
    private LocalDateTime updatedDate;

    private WikiPage() {
    }

    public static WikiPage save(Repository repo, User editor, String title, String body) throws SQLException {
        repo.requireOwner(editor.getUserID());
        String t = title == null ? "" : title.trim();
        String b = body == null ? "" : body;
        if (t.isEmpty() || t.length() > 80 || t.contains("[") || t.contains("]")) {
            throw new IllegalArgumentException("A wiki page needs a title of 1-80 characters without [ or ].");
        }
        if (b.length() > MAX_BODY) {
            throw new IllegalArgumentException("A wiki page can be at most " + MAX_BODY + " characters.");
        }
        LocalDateTime now = DateUtil.now();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement("UPDATE wiki_pages SET body = ?, revision = revision + 1, updated_by = ?, updated_date = ? WHERE repo_id = ? AND title = ?")) {
                ps.setString(1, b);
                ps.setString(2, editor.getUsername());
                ps.setString(3, DateUtil.toDb(now));
                ps.setInt(4, repo.getRepoID());
                ps.setString(5, t);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO wiki_pages (repo_id, title, body, revision, updated_by, updated_date) VALUES (?, ?, ?, 1, ?, ?)")) {
                    ps.setInt(1, repo.getRepoID());
                    ps.setString(2, t);
                    ps.setString(3, b);
                    ps.setString(4, editor.getUsername());
                    ps.setString(5, DateUtil.toDb(now));
                    ps.executeUpdate();
                }
            }
        }
        return find(repo, editor.getUserID(), t);
    }

    public static List<WikiPage> findByRepo(Repository repo, int viewerID) throws SQLException {
        repo.requireViewable(viewerID);
        List<WikiPage> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, repo_id, title, body, revision, updated_by, updated_date FROM wiki_pages WHERE repo_id = ? ORDER BY title COLLATE NOCASE")) {
            ps.setInt(1, repo.getRepoID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WikiPage p = new WikiPage();
                    p.pageID = rs.getInt("id");
                    p.repoID = rs.getInt("repo_id");
                    p.title = rs.getString("title");
                    p.body = rs.getString("body");
                    p.revision = rs.getInt("revision");
                    p.updatedBy = rs.getString("updated_by");
                    p.updatedDate = DateUtil.fromDb(rs.getString("updated_date"));
                    list.add(p);
                }
            }
        }
        return list;
    }

    public static WikiPage find(Repository repo, int viewerID, String title) throws SQLException {
        for (WikiPage p : findByRepo(repo, viewerID)) {
            if (p.title.equalsIgnoreCase(title == null ? "" : title.trim())) {
                return p;
            }
        }
        return null;
    }

    public void delete(Repository repo, int userID) throws SQLException {
        repo.requireOwner(userID);
        if (repo.getRepoID() != repoID) {
            throw new IllegalArgumentException("This page belongs to another repository.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM wiki_pages WHERE id = ? AND repo_id = ?")) {
            ps.setInt(1, pageID);
            ps.setInt(2, repoID);
            ps.executeUpdate();
        }
    }

    public int getPageID() {
        return pageID;
    }

    public int getRepoID() {
        return repoID;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public int getRevision() {
        return revision;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }
}
