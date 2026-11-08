package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PinnedRepositories {

    public static final int MAX_PINS = 6;

    private PinnedRepositories() {
    }

    public static void pin(int userID, Repository repo) throws SQLException {
        if (repo == null || !repo.isOwnedBy(userID)) {
            throw new IllegalStateException("You can only pin your own repositories.");
        }
        if (isPinned(userID, repo.getRepoID())) {
            throw new IllegalStateException("'" + repo.getName() + "' is already pinned.");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection()) {
            int count = 0;
            int last = 0;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*), COALESCE(MAX(position), 0) FROM pinned_repos WHERE user_id = ?")) {
                ps.setInt(1, userID);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    count = rs.getInt(1);
                    last = rs.getInt(2);
                }
            }
            if (count >= MAX_PINS) {
                throw new IllegalStateException("You can pin at most " + MAX_PINS + " repositories. Unpin one first.");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO pinned_repos (user_id, repo_id, position) VALUES (?, ?, ?)")) {
                ps.setInt(1, userID);
                ps.setInt(2, repo.getRepoID());
                ps.setInt(3, last + 1);
                ps.executeUpdate();
            }
        }
    }

    public static void unpin(int userID, int repoID) throws SQLException {
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM pinned_repos WHERE user_id = ? AND repo_id = ?")) {
            ps.setInt(1, userID);
            ps.setInt(2, repoID);
            ps.executeUpdate();
        }
    }

    public static boolean isPinned(int userID, int repoID) throws SQLException {
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM pinned_repos WHERE user_id = ? AND repo_id = ?")) {
            ps.setInt(1, userID);
            ps.setInt(2, repoID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static List<Repository> pinnedFor(int ownerID, int viewerID) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT repo_id FROM pinned_repos WHERE user_id = ? ORDER BY position")) {
            ps.setInt(1, ownerID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        List<Repository> list = new ArrayList<>();
        for (int id : ids) {
            Repository repo = Repository.findById(id);
            if (repo != null && repo.isOwnedBy(ownerID) && repo.canBeViewedBy(viewerID)) {
                list.add(repo);
            }
        }
        return list;
    }
}
