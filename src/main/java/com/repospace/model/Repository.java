package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.FileUtil;
import com.repospace.util.Markdown;
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

public class Repository {

    public static final List<String> VISIBILITIES = List.of("private", "public");
    public static final int MAX_TOPICS = 10;
    public static final int MAX_DESCRIPTION = 350;
    public static final int MAX_README = 20000;

    private static final String COLUMNS = "id, owner_id, name, description, visibility, source_path, website, topics, readme, pages_enabled, created_date";

    private int repoID;
    private int ownerID;
    private String name;
    private String description;
    private String visibility;
    private String sourcePath;
    private String website = "";
    private String topics = "";
    private String readme = "";
    private boolean pagesEnabled;
    private LocalDateTime createdDate;

    public Repository(int ownerID, String name, String description, String visibility, String sourcePath) {
        this.ownerID = ownerID;
        this.name = name == null ? "" : name.trim();
        this.description = description == null ? "" : description.trim();
        this.visibility = visibility == null ? "private" : visibility.trim().toLowerCase();
        this.sourcePath = sourcePath == null ? "" : sourcePath.trim();
    }

    private Repository() {
    }

    public void createRepository() throws SQLException, IOException {
        if (name.isEmpty() || name.length() > 60) {
            throw new IllegalArgumentException("A repository needs a name of 1-60 characters.");
        }
        if (!VISIBILITIES.contains(visibility)) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'.");
        }
        if (sourcePath.isEmpty() || !Files.isDirectory(Paths.get(sourcePath))) {
            throw new IllegalArgumentException("Please choose an existing folder to track.");
        }
        this.createdDate = DateUtil.now();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM repositories WHERE owner_id = ? AND lower(name) = lower(?)")) {
                ps.setInt(1, ownerID);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("You already have a repository called '" + name + "'.");
                    }
                }
            }
            String sql = "INSERT INTO repositories (owner_id, name, description, visibility, source_path, created_date) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ownerID);
                ps.setString(2, name);
                ps.setString(3, description);
                ps.setString(4, visibility);
                ps.setString(5, Paths.get(sourcePath).toAbsolutePath().toString());
                ps.setString(6, DateUtil.toDb(createdDate));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    this.repoID = keys.getInt(1);
                }
            }
        }
        Files.createDirectories(getObjectsDir());
        ActivityLogger.info("Repository created: '" + name + "' (#" + repoID + ") tracking " + sourcePath);
    }

    public void deleteRepository() throws SQLException, IOException {
        try (Connection c = DatabaseConnection.getEntryDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM entries WHERE repo_id = ?")) {
            ps.setInt(1, repoID);
            ps.executeUpdate();
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM repositories WHERE id = ? AND owner_id = ?")) {
            ps.setInt(1, repoID);
            ps.setInt(2, ownerID);
            ps.executeUpdate();
        }
        FileUtil.deleteRecursively(getRepoDir());
        ActivityLogger.info("Repository deleted: '" + name + "' (#" + repoID + ")");
    }

    public void cloneRepository(Path destination) throws SQLException, IOException {
        Commit latest = Commit.latestFor(repoID);
        if (latest == null) {
            throw new IllegalStateException("This repository has no commits yet, so there is nothing to clone.");
        }
        if (!FileUtil.isMissingOrEmptyFolder(destination)) {
            throw new IllegalStateException("The destination folder must be empty or not exist yet.");
        }
        int files = latest.restore(destination);
        ActivityLogger.info("Repository '" + name + "' cloned (" + files + " files) to " + destination);
    }

    public boolean isPublic() {
        return "public".equals(visibility);
    }

    public boolean canBeViewedBy(int userID) {
        return isOwnedBy(userID) || isPublic();
    }

    public void changeVisibility(String newVisibility) throws SQLException {
        String value = newVisibility == null ? "" : newVisibility.trim().toLowerCase();
        if (!VISIBILITIES.contains(value)) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE repositories SET visibility = ? WHERE id = ? AND owner_id = ?")) {
            ps.setString(1, value);
            ps.setInt(2, repoID);
            ps.setInt(3, ownerID);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Only the owner can change this repository.");
            }
        }
        this.visibility = value;
        if ("private".equals(value) && pagesEnabled) {
            try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE repositories SET pages_enabled = 0 WHERE id = ? AND owner_id = ?")) {
                ps.setInt(1, repoID);
                ps.setInt(2, ownerID);
                ps.executeUpdate();
            }
            this.pagesEnabled = false;
        }
        ActivityLogger.info("Repository '" + name + "' (#" + repoID + ") is now " + value);
    }

    public int download(int requesterID, Path destination) throws SQLException, IOException {
        if (!canBeViewedBy(requesterID)) {
            throw new IllegalStateException("'" + name + "' is private - only its owner can download it.");
        }
        Commit latest = Commit.latestFor(repoID);
        if (latest == null) {
            throw new IllegalStateException("'" + name + "' has no saved versions yet, so there is nothing to download.");
        }
        Path last = destination.getFileName();
        int files;
        if (last != null && last.toString().toLowerCase().endsWith(".zip")) {
            files = latest.exportZip(destination);
        } else {
            if (!FileUtil.isMissingOrEmptyFolder(destination)) {
                throw new IllegalStateException("The destination folder must be empty or not exist yet.");
            }
            files = latest.restore(destination);
        }
        ActivityLogger.info("Repository '" + name + "' (#" + repoID + ") downloaded by user #" + requesterID + " to " + destination);
        return files;
    }


    public List<String> getTopicList() {
        List<String> list = new ArrayList<>();
        for (String t : topics.split(",")) {
            if (!t.isBlank()) {
                list.add(t.trim());
            }
        }
        return list;
    }

    public void updateAbout(int requesterID, String newDescription, String newWebsite, String newTopics) throws SQLException {
        requireOwner(requesterID);
        String desc = newDescription == null ? "" : newDescription.trim();
        if (desc.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("The description can be at most " + MAX_DESCRIPTION + " characters.");
        }
        String site = newWebsite == null ? "" : newWebsite.trim();
        if (!site.isEmpty() && (site.length() > 200 || !Markdown.isSafeUrl(site) || site.toLowerCase().startsWith("mailto:") || site.matches(".*\\s.*"))) {
            throw new IllegalArgumentException("The website must be a web address that starts with http:// or https://");
        }
        String normalisedTopics = normaliseTopics(newTopics);
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE repositories SET description = ?, website = ?, topics = ? WHERE id = ? AND owner_id = ?")) {
            ps.setString(1, desc);
            ps.setString(2, site);
            ps.setString(3, normalisedTopics);
            ps.setInt(4, repoID);
            ps.setInt(5, ownerID);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Only the owner can change this repository.");
            }
        }
        this.description = desc;
        this.website = site;
        this.topics = normalisedTopics;
    }

    static String normaliseTopics(String text) {
        List<String> clean = new ArrayList<>();
        if (text != null) {
            for (String raw : text.split("[,;]")) {
                String t = raw.trim().toLowerCase().replaceAll("\\s+", "-");
                if (t.isEmpty()) {
                    continue;
                }
                if (!t.matches("[a-z0-9][a-z0-9-]{0,24}")) {
                    throw new IllegalArgumentException("'" + raw.trim() + "' is not a valid topic. Use up to 25 letters, digits or '-'.");
                }
                if (!clean.contains(t)) {
                    clean.add(t);
                }
            }
        }
        if (clean.size() > MAX_TOPICS) {
            throw new IllegalArgumentException("A repository can have at most " + MAX_TOPICS + " topics.");
        }
        return String.join(",", clean);
    }

    public void saveReadme(int requesterID, String text) throws SQLException {
        requireOwner(requesterID);
        String value = text == null ? "" : text;
        if (value.length() > MAX_README) {
            throw new IllegalArgumentException("The README can be at most " + MAX_README + " characters.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE repositories SET readme = ? WHERE id = ? AND owner_id = ?")) {
            ps.setString(1, value);
            ps.setInt(2, repoID);
            ps.setInt(3, ownerID);
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Only the owner can change this repository.");
            }
        }
        this.readme = value;
    }

    public String getReadme() {
        return readme;
    }

    public String committedReadme() throws SQLException, IOException {
        Commit latest = Commit.latestFor(repoID);
        if (latest == null) {
            return null;
        }
        Map<String, String> manifest = latest.loadManifest();
        for (String path : manifest.keySet()) {
            if (path.equalsIgnoreCase("README.md")) {
                byte[] bytes = latest.readFile(manifest, path, 1_000_000);
                return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public String readmeFor(int viewerID) throws SQLException, IOException {
        requireViewable(viewerID);
        String fromFiles = committedReadme();
        return fromFiles != null ? fromFiles : readme;
    }

    public boolean readmeComesFromFiles() throws SQLException, IOException {
        return committedReadme() != null;
    }

    public boolean isPagesEnabled() {
        return pagesEnabled;
    }

    public void setPagesEnabled(int requesterID, boolean enabled) throws SQLException {
        requireOwner(requesterID);
        if (enabled && !isPublic()) {
            throw new IllegalStateException("A Pages site is public - make the repository public first.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE repositories SET pages_enabled = ? WHERE id = ? AND owner_id = ?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, repoID);
            ps.setInt(3, ownerID);
            ps.executeUpdate();
        }
        this.pagesEnabled = enabled;
        ActivityLogger.info("Pages for repository '" + name + "' (#" + repoID + ") turned " + (enabled ? "on" : "off"));
    }

    void requireOwner(int userID) {
        if (!isOwnedBy(userID)) {
            throw new IllegalStateException("Only the owner can change this repository.");
        }
    }

    void requireViewable(int userID) {
        if (!canBeViewedBy(userID)) {
            throw new IllegalStateException("'" + name + "' is private - only its owner can see it.");
        }
    }

    public static List<Repository> searchPublic(String text) throws SQLException {
        String q = text == null ? "" : text.trim().toLowerCase();
        if (q.isEmpty()) {
            return new ArrayList<>();
        }
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Repository> list = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM repositories WHERE visibility = 'public' AND "
                + "(lower(name) LIKE ? ESCAPE '\\' OR lower(description) LIKE ? ESCAPE '\\' OR (',' || topics || ',') LIKE ? ESCAPE '\\') "
                + "ORDER BY name COLLATE NOCASE LIMIT 50";
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        }
        return list;
    }

    public static List<Repository> findPagesSites() throws SQLException {
        return query("SELECT " + COLUMNS + " FROM repositories WHERE pages_enabled = 1 AND visibility = 'public' ORDER BY id DESC");
    }

    public static Repository findByOwnerAndName(int ownerID, String repoName) throws SQLException {
        List<Repository> found = queryText("SELECT " + COLUMNS + " FROM repositories WHERE owner_id = ? AND lower(name) = lower(?)", ownerID, repoName);
        return found.isEmpty() ? null : found.get(0);
    }

    public String getWebsite() {
        return website;
    }

    public String getTopics() {
        return topics;
    }

    public List<String> viewFiles() throws SQLException {
        Commit latest = Commit.latestFor(repoID);
        if (latest == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(latest.loadManifest().keySet());
    }

    public boolean isOwnedBy(int userID) {
        return ownerID == userID;
    }

    public Path getRepoDir() {
        return DatabaseConnection.dataDir().resolve("repos").resolve(String.valueOf(repoID));
    }

    public Path getObjectsDir() {
        return getRepoDir().resolve("objects");
    }

    public static Repository findById(int id) throws SQLException {
        List<Repository> list = query("SELECT " + COLUMNS + " FROM repositories WHERE id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<Repository> findVisibleTo(int userID) throws SQLException {
        return query("SELECT " + COLUMNS + " FROM repositories WHERE owner_id = ? OR visibility = 'public' ORDER BY (owner_id = ?) DESC, id DESC", userID, userID);
    }

    public static List<Repository> findPublicByOwner(int ownerID) throws SQLException {
        return query("SELECT " + COLUMNS + " FROM repositories WHERE owner_id = ? AND visibility = 'public' ORDER BY id DESC", ownerID);
    }

    public static List<Repository> findPublicNotOwnedBy(int userID) throws SQLException {
        return query("SELECT " + COLUMNS + " FROM repositories WHERE owner_id <> ? AND visibility = 'public' ORDER BY id DESC", userID);
    }

    public static List<Repository> findByOwner(int userID) throws SQLException {
        return query("SELECT " + COLUMNS + " FROM repositories WHERE owner_id = ? ORDER BY id DESC", userID);
    }

    private static List<Repository> query(String sql, int... params) throws SQLException {
        List<Repository> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        }
        return list;
    }

    private static List<Repository> queryText(String sql, int number, String text) throws SQLException {
        List<Repository> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, number);
            ps.setString(2, text);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        }
        return list;
    }

    private static Repository read(ResultSet rs) throws SQLException {
        Repository r = new Repository();
        r.repoID = rs.getInt("id");
        r.ownerID = rs.getInt("owner_id");
        r.name = rs.getString("name");
        r.description = rs.getString("description");
        r.visibility = rs.getString("visibility");
        r.sourcePath = rs.getString("source_path");
        r.website = rs.getString("website");
        r.topics = rs.getString("topics");
        r.readme = rs.getString("readme");
        r.pagesEnabled = rs.getInt("pages_enabled") == 1;
        r.createdDate = DateUtil.fromDb(rs.getString("created_date"));
        return r;
    }

    public Map<String, String> latestManifest() throws SQLException {
        Commit latest = Commit.latestFor(repoID);
        return latest == null ? new java.util.TreeMap<>() : latest.loadManifest();
    }

    public List<String> pendingChanges() throws SQLException, IOException {
        Path source = Paths.get(sourcePath);
        Map<String, String> current = new java.util.TreeMap<>();
        for (Path file : FileUtil.listFilesRecursive(source)) {
            current.put(source.relativize(file).toString().replace('\\', '/'), FileUtil.sha256(file));
        }
        return SnapshotStore.diff(latestManifest(), current);
    }

    public int getRepoID() {
        return repoID;
    }

    public int getOwnerID() {
        return ownerID;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
