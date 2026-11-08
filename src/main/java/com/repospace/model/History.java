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

public class History {

    private int historyID;
    private int repoID;
    private int commitID;
    private int versionNo;
    private String changes;
    private LocalDateTime date;

    public History(int repoID, int commitID, int versionNo, String changes, LocalDateTime date) {
        this.repoID = repoID;
        this.commitID = commitID;
        this.versionNo = versionNo;
        this.changes = changes;
        this.date = date;
    }

    void save(Connection c) throws SQLException {
        String sql = "INSERT INTO history (repo_id, commit_id, version_no, changes, history_date) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repoID);
            ps.setInt(2, commitID);
            ps.setInt(3, versionNo);
            ps.setString(4, changes);
            ps.setString(5, DateUtil.toDb(date));
            ps.executeUpdate();
        }
    }

    public String viewHistory() {
        return getVersion() + "  " + DateUtil.format(date) + "\n" + changes;
    }

    public String getVersion() {
        return "v" + versionNo;
    }

    public static List<History> findByRepo(int repoID) throws SQLException {
        return query("SELECT id, repo_id, commit_id, version_no, changes, history_date FROM history WHERE repo_id = ? ORDER BY version_no DESC", repoID);
    }

    public static List<History> findByCommit(int commitID) throws SQLException {
        return query("SELECT id, repo_id, commit_id, version_no, changes, history_date FROM history WHERE commit_id = ? ORDER BY id", commitID);
    }

    private static List<History> query(String sql, int param) throws SQLException {
        List<History> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    History h = new History(rs.getInt("repo_id"), rs.getInt("commit_id"), rs.getInt("version_no"),
                            rs.getString("changes"), DateUtil.fromDb(rs.getString("history_date")));
                    h.historyID = rs.getInt("id");
                    list.add(h);
                }
            }
        }
        return list;
    }

    public int getHistoryID() {
        return historyID;
    }

    public int getRepoID() {
        return repoID;
    }

    public int getCommitID() {
        return commitID;
    }

    public String getChanges() {
        return changes;
    }

    public LocalDateTime getDate() {
        return date;
    }
}
