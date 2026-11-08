package com.repospace.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String USERS_DB = "users.db";
    private static final String LOGIN_DB = "login.db";
    private static final String SOFTWARE_DB = "software_programme.db";
    private static final String ENTRY_DB = "entry.db";

    private DatabaseConnection() {
    }

    public static Path dataDir() {
        return Paths.get(System.getProperty("repospace.data", "data"));
    }

    private static Connection open(String fileName) throws SQLException {
        try {
            Files.createDirectories(dataDir());
        } catch (IOException e) {
            throw new SQLException("Cannot create the data folder: " + e.getMessage(), e);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dataDir().resolve(fileName));
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static Connection getConnection() throws SQLException {
        return getUsersDatabaseConnection();
    }

    public static Connection getUsersDatabaseConnection() throws SQLException {
        return open(USERS_DB);
    }

    public static Connection getSoftwareProgrammeDatabaseConnection() throws SQLException {
        return open(SOFTWARE_DB);
    }

    public static Connection getLoginDatabaseConnection() throws SQLException {
        return open(LOGIN_DB);
    }

    public static Connection getEntryDatabaseConnection() throws SQLException {
        return open(ENTRY_DB);
    }

    public static void initialise() throws SQLException {
        try (Connection c = getUsersDatabaseConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "email TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "password_hash TEXT NOT NULL, "
                    + "salt TEXT NOT NULL, "
                    + "created_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS profiles ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE, "
                    + "bio TEXT NOT NULL DEFAULT '', "
                    + "profile_picture TEXT NOT NULL DEFAULT '', "
                    + "visibility TEXT NOT NULL DEFAULT 'public', "
                    + "readme TEXT NOT NULL DEFAULT '', "
                    + "status TEXT NOT NULL DEFAULT '', "
                    + "join_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS settings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE, "
                    + "theme TEXT NOT NULL DEFAULT 'Light', "
                    + "language TEXT NOT NULL DEFAULT 'English', "
                    + "notifications INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE IF NOT EXISTS sponsor_tiers ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "name TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, "
                    + "description TEXT NOT NULL DEFAULT '')");
            st.execute("CREATE TABLE IF NOT EXISTS sponsorships ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "sponsor_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "sponsored_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "tier_id INTEGER NOT NULL REFERENCES sponsor_tiers(id) ON DELETE CASCADE, "
                    + "amount INTEGER NOT NULL, "
                    + "message TEXT NOT NULL DEFAULT '', "
                    + "created_date TEXT NOT NULL, "
                    + "active INTEGER NOT NULL DEFAULT 1)");
        }
        try (Connection c = getLoginDatabaseConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS login_attempts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "identifier TEXT NOT NULL, "
                    + "success INTEGER NOT NULL, "
                    + "attempted_at TEXT NOT NULL)");
        }
        try (Connection c = getSoftwareProgrammeDatabaseConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS projects ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL, "
                    + "name TEXT NOT NULL, "
                    + "description TEXT NOT NULL DEFAULT '', "
                    + "created_date TEXT NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'Planning', "
                    + "visibility TEXT NOT NULL DEFAULT 'private')");
            st.execute("CREATE TABLE IF NOT EXISTS repositories ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "owner_id INTEGER NOT NULL, "
                    + "name TEXT NOT NULL, "
                    + "description TEXT NOT NULL DEFAULT '', "
                    + "visibility TEXT NOT NULL DEFAULT 'private', "
                    + "source_path TEXT NOT NULL, "
                    + "website TEXT NOT NULL DEFAULT '', "
                    + "topics TEXT NOT NULL DEFAULT '', "
                    + "readme TEXT NOT NULL DEFAULT '', "
                    + "pages_enabled INTEGER NOT NULL DEFAULT 0, "
                    + "created_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS commits ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "message TEXT NOT NULL, "
                    + "author TEXT NOT NULL, "
                    + "commit_date TEXT NOT NULL, "
                    + "version_no INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "commit_id INTEGER NOT NULL REFERENCES commits(id) ON DELETE CASCADE, "
                    + "version_no INTEGER NOT NULL, "
                    + "changes TEXT NOT NULL, "
                    + "history_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS pinned_repos ("
                    + "user_id INTEGER NOT NULL, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "position INTEGER NOT NULL, "
                    + "PRIMARY KEY (user_id, repo_id))");
            st.execute("CREATE TABLE IF NOT EXISTS discussions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "author_id INTEGER NOT NULL, "
                    + "author_name TEXT NOT NULL, "
                    + "category TEXT NOT NULL, "
                    + "title TEXT NOT NULL, "
                    + "body TEXT NOT NULL, "
                    + "closed INTEGER NOT NULL DEFAULT 0, "
                    + "created_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS discussion_replies ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "discussion_id INTEGER NOT NULL REFERENCES discussions(id) ON DELETE CASCADE, "
                    + "author_id INTEGER NOT NULL, "
                    + "author_name TEXT NOT NULL, "
                    + "body TEXT NOT NULL, "
                    + "created_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS board_tasks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE, "
                    + "repo_id INTEGER REFERENCES repositories(id) ON DELETE SET NULL, "
                    + "title TEXT NOT NULL, "
                    + "details TEXT NOT NULL DEFAULT '', "
                    + "status TEXT NOT NULL DEFAULT 'To do', "
                    + "created_date TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS releases ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "commit_id INTEGER NOT NULL REFERENCES commits(id) ON DELETE CASCADE, "
                    + "tag TEXT NOT NULL, "
                    + "title TEXT NOT NULL, "
                    + "notes TEXT NOT NULL DEFAULT '', "
                    + "prerelease INTEGER NOT NULL DEFAULT 0, "
                    + "created_date TEXT NOT NULL, "
                    + "UNIQUE (repo_id, tag))");
            st.execute("CREATE TABLE IF NOT EXISTS wiki_pages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "repo_id INTEGER NOT NULL REFERENCES repositories(id) ON DELETE CASCADE, "
                    + "title TEXT NOT NULL COLLATE NOCASE, "
                    + "body TEXT NOT NULL DEFAULT '', "
                    + "revision INTEGER NOT NULL DEFAULT 1, "
                    + "updated_by TEXT NOT NULL DEFAULT '', "
                    + "updated_date TEXT NOT NULL, "
                    + "UNIQUE (repo_id, title))");
        }
        try (Connection c = getEntryDatabaseConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS entries ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "commit_id INTEGER NOT NULL, "
                    + "repo_id INTEGER NOT NULL, "
                    + "relative_path TEXT NOT NULL, "
                    + "file_hash TEXT NOT NULL, "
                    + "file_size INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_entries_commit ON entries(commit_id)");
        }
        addColumnIfMissing(getUsersDatabaseConnection(), "profiles", "visibility", "TEXT NOT NULL DEFAULT 'public'");
        addColumnIfMissing(getSoftwareProgrammeDatabaseConnection(), "projects", "visibility", "TEXT NOT NULL DEFAULT 'private'");
        addColumnIfMissing(getUsersDatabaseConnection(), "profiles", "readme", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(getUsersDatabaseConnection(), "profiles", "status", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(getSoftwareProgrammeDatabaseConnection(), "repositories", "website", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(getSoftwareProgrammeDatabaseConnection(), "repositories", "topics", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(getSoftwareProgrammeDatabaseConnection(), "repositories", "readme", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(getSoftwareProgrammeDatabaseConnection(), "repositories", "pages_enabled", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
        try (Connection connection = c; Statement st = connection.createStatement()) {
            boolean found = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("name"))) {
                        found = true;
                    }
                }
            }
            if (!found) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }
}
