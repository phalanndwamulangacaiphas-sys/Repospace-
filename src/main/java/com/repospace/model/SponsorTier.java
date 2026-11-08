package com.repospace.model;

import com.repospace.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SponsorTier {

    public static final String CURRENCY = "R";
    public static final int MAX_TIERS = 5;
    public static final int MAX_AMOUNT = 100000;

    private int tierID;
    private int userID;
    private String name;
    private int amount;
    private String description;

    private SponsorTier() {
    }

    public static SponsorTier create(int userID, String name, int amount, String description) throws SQLException {
        String n = name == null ? "" : name.trim();
        String d = description == null ? "" : description.trim();
        if (n.isEmpty() || n.length() > 40) {
            throw new IllegalArgumentException("A tier needs a name of 1-40 characters.");
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("The amount per month must be between 1 and " + MAX_AMOUNT + ".");
        }
        if (d.length() > 300) {
            throw new IllegalArgumentException("The description can be at most 300 characters.");
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sponsor_tiers WHERE user_id = ?")) {
                ps.setInt(1, userID);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= MAX_TIERS) {
                        throw new IllegalStateException("You can have at most " + MAX_TIERS + " tiers.");
                    }
                }
            }
            SponsorTier tier = new SponsorTier();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sponsor_tiers (user_id, name, amount, description) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userID);
                ps.setString(2, n);
                ps.setInt(3, amount);
                ps.setString(4, d);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    tier.tierID = keys.getInt(1);
                }
            }
            tier.userID = userID;
            tier.name = n;
            tier.amount = amount;
            tier.description = d;
            return tier;
        }
    }

    public static List<SponsorTier> forUser(int ownerID, int viewerID) throws SQLException {
        List<SponsorTier> list = new ArrayList<>();
        if (viewerID != ownerID && Profile.forViewer(viewerID, ownerID).isHidden()) {
            return list;
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, user_id, name, amount, description FROM sponsor_tiers WHERE user_id = ? ORDER BY amount")) {
            ps.setInt(1, ownerID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs));
                }
            }
        }
        return list;
    }

    public static SponsorTier findById(int id) throws SQLException {
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, user_id, name, amount, description FROM sponsor_tiers WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private static SponsorTier read(ResultSet rs) throws SQLException {
        SponsorTier t = new SponsorTier();
        t.tierID = rs.getInt("id");
        t.userID = rs.getInt("user_id");
        t.name = rs.getString("name");
        t.amount = rs.getInt("amount");
        t.description = rs.getString("description");
        return t;
    }

    public void delete(int requesterID) throws SQLException {
        if (requesterID != userID) {
            throw new IllegalStateException("Only the owner of a tier can delete it.");
        }
        try (Connection c = DatabaseConnection.getUsersDatabaseConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM sponsor_tiers WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, tierID);
            ps.setInt(2, userID);
            ps.executeUpdate();
        }
    }

    public int getTierID() {
        return tierID;
    }

    public int getUserID() {
        return userID;
    }

    public String getName() {
        return name;
    }

    public int getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name + " - " + CURRENCY + amount + " / month";
    }
}
