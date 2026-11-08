package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Release {

    private int releaseID;
    private int repoID;
    private int commitID;
    private String tag;
    private String title;
    private String notes;
    private boolean prerelease;
    private LocalDateTime createdDate;

    private Release() {
    }

    public static Release publish(Repository repo, int userID, String tag, String title, String notes, boolean prerelease) throws SQLException {
        repo.requireOwner(userID);
        String t = tag == null ? "" : tag.trim();
        if (!t.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,29}")) {
            throw new IllegalArgumentException("The tag must be 1-30 letters, digits, '.', '_' or '-' (for example v1.0.0).");
        }
        String n = notes == null ? "" : notes.trim();
        if (n.length() > 10000) {
            throw new IllegalArgumentException("The release notes can be at most 10000 characters.");
        }
        String name = title == null || title.isBlank() ? t : title.trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("The release title can be at most 100 characters.");
        }
        Commit latest = Commit.latestFor(repo.getRepoID());
        if (latest == null) {
            throw new IllegalStateException("Save at least one version (commit) before making a release.");
        }
        Release r = new Release();
        r.repoID = repo.getRepoID();
        r.commitID = latest.getCommitID();
        r.tag = t;
        r.title = name;
        r.notes = n;
        r.prerelease = prerelease;
        r.createdDate = DateUtil.now();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM releases WHERE repo_id = ? AND lower(tag) = lower(?)")) {
                ps.setInt(1, r.repoID);
                ps.setString(2, t);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("There is already a release with the tag '" + t + "'.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO releases (repo_id, commit_id, tag, title, notes, prerelease, created_date) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, r.repoID);
                ps.setInt(2, r.commitID);
                ps.setString(3, t);
                ps.setString(4, name);
                ps.setString(5, n);
                ps.setInt(6, prerelease ? 1 : 0);
                ps.setString(7, DateUtil.toDb(r.createdDate));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    r.releaseID = keys.getInt(1);
                }
            }
        }
        ActivityLogger.info("Release " + t + " published for repository #" + r.repoID);
        return r;
    }

    public static List<Release> findByRepo(Repository repo, int viewerID) throws SQLException {
        repo.requireViewable(viewerID);
        List<Release> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, repo_id, commit_id, tag, title, notes, prerelease, created_date FROM releases WHERE repo_id = ? ORDER BY id DESC")) {
            ps.setInt(1, repo.getRepoID());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        }
        return list;
    }

    public static Release latest(Repository repo, int viewerID) throws SQLException {
        for (Release r : findByRepo(repo, viewerID)) {
            if (!r.prerelease) {
                return r;
            }
        }
        return null;
    }

    public static int countForOwner(int ownerID, boolean publicOnly) throws SQLException {
        String sql = "SELECT COUNT(*) FROM releases e JOIN repositories r ON r.id = e.repo_id WHERE r.owner_id = ?" + (publicOnly ? " AND r.visibility = 'public'" : "");
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ownerID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static Release read(ResultSet rs) throws SQLException {
        Release r = new Release();
        r.releaseID = rs.getInt("id");
        r.repoID = rs.getInt("repo_id");
        r.commitID = rs.getInt("commit_id");
        r.tag = rs.getString("tag");
        r.title = rs.getString("title");
        r.notes = rs.getString("notes");
        r.prerelease = rs.getInt("prerelease") == 1;
        r.createdDate = DateUtil.fromDb(rs.getString("created_date"));
        return r;
    }

    public Commit getCommit() throws SQLException {
        return Commit.findById(commitID);
    }

    public int downloadZip(Repository repo, int requesterID, Path zipFile) throws SQLException, IOException {
        repo.requireViewable(requesterID);
        if (repo.getRepoID() != repoID) {
            throw new IllegalArgumentException("This release belongs to another repository.");
        }
        Commit commit = getCommit();
        if (commit == null) {
            throw new IllegalStateException("The saved version of this release no longer exists.");
        }
        return commit.exportZip(zipFile);
    }

    public void delete(Repository repo, int userID) throws SQLException {
        repo.requireOwner(userID);
        if (repo.getRepoID() != repoID) {
            throw new IllegalArgumentException("This release belongs to another repository.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM releases WHERE id = ? AND repo_id = ?")) {
            ps.setInt(1, releaseID);
            ps.setInt(2, repoID);
            ps.executeUpdate();
        }
    }

    public int getReleaseID() {
        return releaseID;
    }

    public int getRepoID() {
        return repoID;
    }

    public int getCommitID() {
        return commitID;
    }

    public String getTag() {
        return tag;
    }

    public String getTitle() {
        return title;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
