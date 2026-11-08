package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Sponsorship {

    private int sponsorshipID;
    private int sponsorID;
    private String sponsorName;
    private int sponsoredID;
    private String sponsoredName;
    private int tierID;
    private String tierName;
    private int amount;
    private String message;
    private LocalDateTime createdDate;

    private Sponsorship() {
    }

    public static Sponsorship pledge(User sponsor, SponsorTier tier, String message) throws SQLException {
        if (tier == null) {
            throw new IllegalArgumentException("Please choose a tier.");
        }
        if (sponsor.getUserID() == tier.getUserID()) {
            throw new IllegalStateException("You cannot sponsor yourself.");
        }
        if (Profile.forViewer(sponsor.getUserID(), tier.getUserID()).isHidden()) {
            throw new IllegalStateException("This user's profile is private, so their sponsor page is not available.");
        }
        String m = message == null ? "" : message.trim();
        if (m.length() > 200) {
            throw new IllegalArgumentException("The message can be at most 200 characters.");
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement("UPDATE sponsorships SET tier_id = ?, amount = ?, message = ?, active = 1, created_date = ? WHERE sponsor_id = ? AND sponsored_id = ?")) {
                ps.setInt(1, tier.getTierID());
                ps.setInt(2, tier.getAmount());
                ps.setString(3, m);
                ps.setString(4, DateUtil.toDb(DateUtil.now()));
                ps.setInt(5, sponsor.getUserID());
                ps.setInt(6, tier.getUserID());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO sponsorships (sponsor_id, sponsored_id, tier_id, amount, message, created_date, active) VALUES (?, ?, ?, ?, ?, ?, 1)")) {
                    ps.setInt(1, sponsor.getUserID());
                    ps.setInt(2, tier.getUserID());
                    ps.setInt(3, tier.getTierID());
                    ps.setInt(4, tier.getAmount());
                    ps.setString(5, m);
                    ps.setString(6, DateUtil.toDb(DateUtil.now()));
                    ps.executeUpdate();
                }
            }
        }
        ActivityLogger.info("User #" + sponsor.getUserID() + " pledged to sponsor user #" + tier.getUserID() + " (tier " + tier.getName() + ")");
        return find(sponsor.getUserID(), tier.getUserID());
    }

    public static void cancel(int sponsorID, int sponsoredID) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE sponsorships SET active = 0 WHERE sponsor_id = ? AND sponsored_id = ?")) {
            ps.setInt(1, sponsorID);
            ps.setInt(2, sponsoredID);
            ps.executeUpdate();
        }
    }

    private static final String SELECT = "SELECT s.id, s.sponsor_id, a.username AS sponsor_name, s.sponsored_id, b.username AS sponsored_name, "
            + "s.tier_id, t.name AS tier_name, s.amount, s.message, s.created_date "
            + "FROM sponsorships s JOIN users a ON a.id = s.sponsor_id JOIN users b ON b.id = s.sponsored_id JOIN sponsor_tiers t ON t.id = s.tier_id ";

    public static Sponsorship find(int sponsorID, int sponsoredID) throws SQLException {
        List<Sponsorship> list = query(SELECT + "WHERE s.sponsor_id = ? AND s.sponsored_id = ? AND s.active = 1", sponsorID, sponsoredID);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<Sponsorship> sponsorsOf(int sponsoredID, int viewerID) throws SQLException {
        if (viewerID != sponsoredID && Profile.forViewer(viewerID, sponsoredID).isHidden()) {
            return new ArrayList<>();
        }
        return query(SELECT + "WHERE s.sponsored_id = ? AND s.active = 1 ORDER BY s.id DESC", sponsoredID, -1);
    }

    public static List<Sponsorship> madeBy(int sponsorID) throws SQLException {
        return query(SELECT + "WHERE s.sponsor_id = ? AND s.active = 1 ORDER BY s.id DESC", sponsorID, -1);
    }

    public static int countSponsors(int sponsoredID) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sponsorships WHERE sponsored_id = ? AND active = 1")) {
            ps.setInt(1, sponsoredID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static int monthlyTotal(int sponsoredID, int requesterID) throws SQLException {
        if (sponsoredID != requesterID) {
            throw new IllegalStateException("Only the sponsored person can see the pledged total.");
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM sponsorships WHERE sponsored_id = ? AND active = 1")) {
            ps.setInt(1, sponsoredID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static List<Sponsorship> query(String sql, int first, int second) throws SQLException {
        List<Sponsorship> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, first);
            if (second >= 0) {
                ps.setInt(2, second);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sponsorship s = new Sponsorship();
                    s.sponsorshipID = rs.getInt("id");
                    s.sponsorID = rs.getInt("sponsor_id");
                    s.sponsorName = rs.getString("sponsor_name");
                    s.sponsoredID = rs.getInt("sponsored_id");
                    s.sponsoredName = rs.getString("sponsored_name");
                    s.tierID = rs.getInt("tier_id");
                    s.tierName = rs.getString("tier_name");
                    s.amount = rs.getInt("amount");
                    s.message = rs.getString("message");
                    s.createdDate = DateUtil.fromDb(rs.getString("created_date"));
                    list.add(s);
                }
            }
        }
        return list;
    }

    public int getSponsorshipID() {
        return sponsorshipID;
    }

    public int getSponsorID() {
        return sponsorID;
    }

    public String getSponsorName() {
        return sponsorName;
    }

    public int getSponsoredID() {
        return sponsoredID;
    }

    public String getSponsoredName() {
        return sponsoredName;
    }

    public int getTierID() {
        return tierID;
    }

    public String getTierName() {
        return tierName;
    }

    public int getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
