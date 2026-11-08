package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.FileUtil;
import com.repospace.util.SnapshotStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Commit {

    private int commitID;
    private int repoID;
    private String message;
    private String author;
    private LocalDateTime date;
    private int versionNo;

    public Commit(int repoID, String message, String author) {
        this.repoID = repoID;
        this.message = message == null ? "" : message.trim();
        this.author = author == null ? "" : author;
    }

    private Commit() {
    }

    public void commit() throws SQLException, IOException {
        if (message.isEmpty()) {
            throw new IllegalArgumentException("A commit message is required.");
        }
        Repository repo = Repository.findById(repoID);
        if (repo == null) {
            throw new IllegalStateException("The repository no longer exists.");
        }
        Path source = Paths.get(repo.getSourcePath());
        if (!Files.isDirectory(source)) {
            throw new IOException("The tracked folder no longer exists: " + source);
        }

        Commit previous = latestFor(repoID);
        Map<String, String> oldManifest = previous == null ? new TreeMap<>() : previous.loadManifest();
        Map<String, String> newManifest = SnapshotStore.snapshot(source, repo.getObjectsDir());
        if (newManifest.isEmpty()) {
            throw new IllegalStateException("The tracked folder has no files to commit.");
        }
        List<String> changes = SnapshotStore.diff(oldManifest, newManifest);
        if (changes.isEmpty()) {
            throw new IllegalStateException("Nothing to commit - no files have changed since the last commit.");
        }

        this.versionNo = previous == null ? 1 : previous.versionNo + 1;
        this.date = DateUtil.now();

        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "INSERT INTO commits (repo_id, message, author, commit_date, version_no) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, repoID);
                    ps.setString(2, message);
                    ps.setString(3, author);
                    ps.setString(4, DateUtil.toDb(date));
                    ps.setInt(5, versionNo);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        this.commitID = keys.getInt(1);
                    }
                }
                new History(repoID, commitID, versionNo, String.join("\n", changes), date).save(c);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }

        try (Connection c = DatabaseConnection.getEntryDatabaseConnection()) {
            c.setAutoCommit(false);
            String sql = "INSERT INTO entries (commit_id, repo_id, relative_path, file_hash, file_size) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (Map.Entry<String, String> e : newManifest.entrySet()) {
                    ps.setInt(1, commitID);
                    ps.setInt(2, repoID);
                    ps.setString(3, e.getKey());
                    ps.setString(4, e.getValue());
                    ps.setLong(5, Files.size(repo.getObjectsDir().resolve(e.getValue())));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            removeCommitRecords();
            throw ex;
        }
        ActivityLogger.info("Commit v" + versionNo + " on repository #" + repoID + " by " + author + ": " + message);
    }

    private void removeCommitRecords() {
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM commits WHERE id = ?")) {
            ps.setInt(1, commitID);
            ps.executeUpdate();
        } catch (SQLException e) {
            ActivityLogger.error("Could not clean up failed commit #" + commitID, e);
        }
    }

    public String viewDetails() throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("Version v").append(versionNo).append("  (commit #").append(commitID).append(")\n");
        sb.append("Author : ").append(author).append('\n');
        sb.append("Date   : ").append(DateUtil.format(date)).append('\n');
        sb.append("Message: ").append(message).append("\n\n");
        sb.append("Changes in this commit:\n");
        for (History h : History.findByCommit(commitID)) {
            sb.append(h.getChanges()).append('\n');
        }
        Map<String, String> manifest = loadManifest();
        sb.append("\nFiles in this version (").append(manifest.size()).append("):\n");
        for (String path : manifest.keySet()) {
            sb.append("  ").append(path).append('\n');
        }
        return sb.toString();
    }

    public Map<String, String> loadManifest() throws SQLException {
        Map<String, String> manifest = new TreeMap<>();
        try (Connection c = DatabaseConnection.getEntryDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT relative_path, file_hash FROM entries WHERE commit_id = ?")) {
            ps.setInt(1, commitID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    manifest.put(rs.getString("relative_path"), rs.getString("file_hash"));
                }
            }
        }
        return manifest;
    }

    public Map<String, Long> loadFileSizes() throws SQLException {
        Map<String, Long> sizes = new TreeMap<>();
        try (Connection c = DatabaseConnection.getEntryDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT relative_path, file_size FROM entries WHERE commit_id = ?")) {
            ps.setInt(1, commitID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sizes.put(rs.getString("relative_path"), rs.getLong("file_size"));
                }
            }
        }
        return sizes;
    }

    public int restore(Path target) throws SQLException, IOException {
        Repository repo = Repository.findById(repoID);
        if (repo == null) {
            throw new IllegalStateException("The repository no longer exists.");
        }
        int count = SnapshotStore.restore(loadManifest(), repo.getObjectsDir(), target);
        ActivityLogger.info("Version v" + versionNo + " of repository #" + repoID + " restored to " + target);
        return count;
    }

    public int exportZip(Path zipFile) throws SQLException, IOException {
        Repository repo = Repository.findById(repoID);
        if (repo == null) {
            throw new IllegalStateException("The repository no longer exists.");
        }
        int count = SnapshotStore.exportZip(loadManifest(), repo.getObjectsDir(), zipFile);
        ActivityLogger.info("Version v" + versionNo + " of repository #" + repoID + " saved as " + zipFile);
        return count;
    }

    public byte[] readFile(String relativePath, long maxBytes) throws SQLException, IOException {
        return readFile(loadManifest(), relativePath, maxBytes);
    }

    public byte[] readFile(Map<String, String> manifest, String relativePath, long maxBytes) throws SQLException, IOException {
        String hash = manifest.get(relativePath);
        if (hash == null) {
            return null;
        }
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IOException("Corrupt snapshot entry for " + relativePath);
        }
        Repository repo = Repository.findById(repoID);
        if (repo == null) {
            throw new IllegalStateException("The repository no longer exists.");
        }
        Path blob = repo.getObjectsDir().resolve(hash);
        if (!Files.exists(blob)) {
            throw new IOException("Stored copy of " + relativePath + " is missing");
        }
        if (Files.size(blob) > maxBytes) {
            throw new IOException(relativePath + " is too large to show here.");
        }
        return Files.readAllBytes(blob);
    }

    public static Commit findById(int commitID) throws SQLException {
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, repo_id, message, author, commit_date, version_no FROM commits WHERE id = ?")) {
            ps.setInt(1, commitID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Commit commit = new Commit();
                    commit.commitID = rs.getInt("id");
                    commit.repoID = rs.getInt("repo_id");
                    commit.message = rs.getString("message");
                    commit.author = rs.getString("author");
                    commit.date = DateUtil.fromDb(rs.getString("commit_date"));
                    commit.versionNo = rs.getInt("version_no");
                    return commit;
                }
            }
        }
        return null;
    }

    public static Commit latestFor(int repoID) throws SQLException {
        List<Commit> all = findByRepo(repoID);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<Commit> findByRepo(int repoID) throws SQLException {
        List<Commit> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, repo_id, message, author, commit_date, version_no FROM commits WHERE repo_id = ? ORDER BY version_no DESC")) {
            ps.setInt(1, repoID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Commit commit = new Commit();
                    commit.commitID = rs.getInt("id");
                    commit.repoID = rs.getInt("repo_id");
                    commit.message = rs.getString("message");
                    commit.author = rs.getString("author");
                    commit.date = DateUtil.fromDb(rs.getString("commit_date"));
                    commit.versionNo = rs.getInt("version_no");
                    list.add(commit);
                }
            }
        }
        return list;
    }

    public static int countRecentForOwner(int ownerID, int days) throws SQLException {
        String since = DateUtil.toDb(DateUtil.now().minusDays(days));
        String sql = "SELECT COUNT(*) FROM commits c JOIN repositories r ON r.id = c.repo_id WHERE r.owner_id = ? AND c.commit_date >= ?";
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ownerID);
            ps.setString(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int getCommitID() {
        return commitID;
    }

    public int getRepoID() {
        return repoID;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public int getVersionNo() {
        return versionNo;
    }
}
