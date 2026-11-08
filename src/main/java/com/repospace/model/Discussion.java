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

public class Discussion {

    public static final List<String> CATEGORIES = List.of("Announcements", "General", "Ideas", "Q&A", "Show and tell");
    public static final int MAX_TITLE = 120;
    public static final int MAX_BODY = 5000;

    public static class Reply {
        private int replyID;
        private int authorID;
        private String authorName;
        private String body;
        private LocalDateTime createdDate;

        public int getReplyID() {
            return replyID;
        }

        public int getAuthorID() {
            return authorID;
        }

        public String getAuthorName() {
            return authorName;
        }

        public String getBody() {
            return body;
        }

        public LocalDateTime getCreatedDate() {
            return createdDate;
        }
    }

    private int discussionID;
    private int repoID;
    private int authorID;
    private String authorName;
    private String category;
    private String title;
    private String body;
    private boolean closed;
    private int replyCount;
    private LocalDateTime createdDate;

    private Discussion() {
    }

    public static Discussion start(Repository repo, User author, String category, String title, String body) throws SQLException {
        repo.requireViewable(author.getUserID());
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Choose a category: " + String.join(", ", CATEGORIES) + ".");
        }
        if ("Announcements".equals(category) && !repo.isOwnedBy(author.getUserID())) {
            throw new IllegalStateException("Only the owner of the repository can post announcements.");
        }
        String t = title == null ? "" : title.trim();
        String b = body == null ? "" : body.trim();
        if (t.isEmpty() || t.length() > MAX_TITLE) {
            throw new IllegalArgumentException("A discussion needs a title of 1-" + MAX_TITLE + " characters.");
        }
        if (b.isEmpty() || b.length() > MAX_BODY) {
            throw new IllegalArgumentException("Please write your message (up to " + MAX_BODY + " characters).");
        }
        Discussion d = new Discussion();
        d.repoID = repo.getRepoID();
        d.authorID = author.getUserID();
        d.authorName = author.getUsername();
        d.category = category;
        d.title = t;
        d.body = b;
        d.createdDate = DateUtil.now();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO discussions (repo_id, author_id, author_name, category, title, body, created_date) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, d.repoID);
            ps.setInt(2, d.authorID);
            ps.setString(3, d.authorName);
            ps.setString(4, category);
            ps.setString(5, t);
            ps.setString(6, b);
            ps.setString(7, DateUtil.toDb(d.createdDate));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                d.discussionID = keys.getInt(1);
            }
        }
        ActivityLogger.info("Discussion #" + d.discussionID + " started on repository #" + d.repoID + " by " + d.authorName);
        return d;
    }

    public static List<Discussion> findByRepo(Repository repo, int viewerID, String category) throws SQLException {
        repo.requireViewable(viewerID);
        List<Discussion> list = new ArrayList<>();
        String sql = "SELECT d.id, d.repo_id, d.author_id, d.author_name, d.category, d.title, d.body, d.closed, d.created_date, "
                + "(SELECT COUNT(*) FROM discussion_replies r WHERE r.discussion_id = d.id) AS replies "
                + "FROM discussions d WHERE d.repo_id = ?" + (category == null ? "" : " AND d.category = ?") + " ORDER BY d.id DESC";
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, repo.getRepoID());
            if (category != null) {
                ps.setString(2, category);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Discussion d = new Discussion();
                    d.discussionID = rs.getInt("id");
                    d.repoID = rs.getInt("repo_id");
                    d.authorID = rs.getInt("author_id");
                    d.authorName = rs.getString("author_name");
                    d.category = rs.getString("category");
                    d.title = rs.getString("title");
                    d.body = rs.getString("body");
                    d.closed = rs.getInt("closed") == 1;
                    d.createdDate = DateUtil.fromDb(rs.getString("created_date"));
                    d.replyCount = rs.getInt("replies");
                    list.add(d);
                }
            }
        }
        return list;
    }

    public List<Reply> replies(Repository repo, int viewerID) throws SQLException {
        checkRepo(repo);
        repo.requireViewable(viewerID);
        List<Reply> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, author_id, author_name, body, created_date FROM discussion_replies WHERE discussion_id = ? ORDER BY id")) {
            ps.setInt(1, discussionID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reply r = new Reply();
                    r.replyID = rs.getInt("id");
                    r.authorID = rs.getInt("author_id");
                    r.authorName = rs.getString("author_name");
                    r.body = rs.getString("body");
                    r.createdDate = DateUtil.fromDb(rs.getString("created_date"));
                    list.add(r);
                }
            }
        }
        return list;
    }

    public void reply(Repository repo, User author, String text) throws SQLException {
        checkRepo(repo);
        repo.requireViewable(author.getUserID());
        if (closed) {
            throw new IllegalStateException("This discussion is closed. Ask the owner to reopen it.");
        }
        String b = text == null ? "" : text.trim();
        if (b.isEmpty() || b.length() > MAX_BODY) {
            throw new IllegalArgumentException("Please write your reply (up to " + MAX_BODY + " characters).");
        }
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO discussion_replies (discussion_id, author_id, author_name, body, created_date) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, discussionID);
            ps.setInt(2, author.getUserID());
            ps.setString(3, author.getUsername());
            ps.setString(4, b);
            ps.setString(5, DateUtil.toDb(DateUtil.now()));
            ps.executeUpdate();
        }
        replyCount++;
    }

    public void setClosed(Repository repo, int userID, boolean value) throws SQLException {
        checkRepo(repo);
        requireAuthorOrOwner(repo, userID);
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE discussions SET closed = ? WHERE id = ?")) {
            ps.setInt(1, value ? 1 : 0);
            ps.setInt(2, discussionID);
            ps.executeUpdate();
        }
        this.closed = value;
    }

    public void delete(Repository repo, int userID) throws SQLException {
        checkRepo(repo);
        requireAuthorOrOwner(repo, userID);
        try (Connection c = DatabaseConnection.getSoftwareProgrammeDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM discussions WHERE id = ?")) {
            ps.setInt(1, discussionID);
            ps.executeUpdate();
        }
    }

    private void requireAuthorOrOwner(Repository repo, int userID) {
        if (userID != authorID && !repo.isOwnedBy(userID)) {
            throw new IllegalStateException("Only the author of this discussion or the repository owner can do that.");
        }
    }

    private void checkRepo(Repository repo) {
        if (repo == null || repo.getRepoID() != repoID) {
            throw new IllegalArgumentException("This discussion belongs to another repository.");
        }
    }

    public int getDiscussionID() {
        return discussionID;
    }

    public int getRepoID() {
        return repoID;
    }

    public int getAuthorID() {
        return authorID;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isClosed() {
        return closed;
    }

    public int getReplyCount() {
        return replyCount;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
