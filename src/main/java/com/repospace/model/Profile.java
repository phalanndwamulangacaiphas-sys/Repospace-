package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

public class Profile implements Persistable {

    public static final List<String> VISIBILITIES = List.of("public", "private");
    private static final int MAX_BIO = 500;
    public static final int MAX_README = 5000;
    public static final int MAX_STATUS = 80;

    private int profileID;
    private int userID;
    private String bio = "";
    private String profilePicture = "";
    private LocalDateTime joinDate;
    private String visibility = "public";
    private String readme = "";
    private String status = "";
    private boolean hidden;

    public static Profile forUser(int userID) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            Profile found = load(c, userID);
            if (found != null) {
                return found;
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO profiles (user_id, bio, profile_picture, join_date) VALUES (?, '', '', ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userID);
                ps.setString(2, DateUtil.toDb(DateUtil.now()));
                ps.executeUpdate();
            }
            return load(c, userID);
        }
    }

    private static Profile load(Connection c, int userID) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, user_id, bio, profile_picture, visibility, readme, status, join_date FROM profiles WHERE user_id = ?")) {
            ps.setInt(1, userID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Profile p = new Profile();
                    p.profileID = rs.getInt("id");
                    p.userID = rs.getInt("user_id");
                    p.bio = rs.getString("bio");
                    p.profilePicture = rs.getString("profile_picture");
                    p.visibility = rs.getString("visibility");
                    p.readme = rs.getString("readme");
                    p.status = rs.getString("status");
                    p.joinDate = DateUtil.fromDb(rs.getString("join_date"));
                    return p;
                }
            }
        }
        return null;
    }

    public static Profile forViewer(int viewerID, int targetUserID) throws SQLException {
        Profile p = forUser(targetUserID);
        if (viewerID != targetUserID && !p.isPublic()) {
            p.bio = "";
            p.profilePicture = "";
            p.readme = "";
            p.status = "";
            p.joinDate = null;
            p.hidden = true;
        }
        return p;
    }

    public void viewProfile() {
        if (hidden) {
            System.out.println("This profile is private.");
            return;
        }
        System.out.println("Profile #" + profileID + "  [" + visibility + "]");
        System.out.println("  Joined : " + DateUtil.format(joinDate));
        System.out.println("  Bio    : " + (bio.isEmpty() ? "(empty)" : bio));
        System.out.println("  Picture: " + (profilePicture.isEmpty() ? "(none)" : profilePicture));
    }

    public void editProfile(String newBio, String newPicture) {
        if (newBio != null && newBio.length() > MAX_BIO) {
            throw new IllegalArgumentException("The bio can be at most " + MAX_BIO + " characters.");
        }
        this.bio = newBio == null ? "" : newBio.trim();
        this.profilePicture = newPicture == null ? "" : newPicture.trim();
    }

    public void editReadme(String newReadme, String newStatus) {
        if (newReadme != null && newReadme.length() > MAX_README) {
            throw new IllegalArgumentException("The profile README can be at most " + MAX_README + " characters.");
        }
        if (newStatus != null && newStatus.trim().length() > MAX_STATUS) {
            throw new IllegalArgumentException("The status can be at most " + MAX_STATUS + " characters.");
        }
        this.readme = newReadme == null ? "" : newReadme;
        this.status = newStatus == null ? "" : newStatus.trim();
    }

    public void changeVisibility(String newVisibility) {
        String value = newVisibility == null ? "" : newVisibility.trim().toLowerCase();
        if (!VISIBILITIES.contains(value)) {
            throw new IllegalArgumentException("Visibility must be 'public' or 'private'.");
        }
        this.visibility = value;
    }

    public void updateProfile() throws SQLException {
        if (hidden) {
            throw new IllegalStateException("You can only change your own profile.");
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE profiles SET bio = ?, profile_picture = ?, visibility = ?, readme = ?, status = ? WHERE id = ?")) {
            ps.setString(1, bio);
            ps.setString(2, profilePicture);
            ps.setString(3, visibility);
            ps.setString(4, readme);
            ps.setString(5, status);
            ps.setInt(6, profileID);
            ps.executeUpdate();
        }
    }

    @Override
    public int getId() {
        return profileID;
    }

    @Override
    public void save() throws SQLException {
        updateProfile();
    }

    public int getProfileID() {
        return profileID;
    }

    public int getUserID() {
        return userID;
    }

    public String getBio() {
        return bio;
    }

    public String getReadme() {
        return readme;
    }

    public String getStatus() {
        return status;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public LocalDateTime getJoinDate() {
        return joinDate;
    }

    public String getVisibility() {
        return visibility;
    }

    public boolean isPublic() {
        return "public".equals(visibility);
    }

    public boolean isHidden() {
        return hidden;
    }
}
