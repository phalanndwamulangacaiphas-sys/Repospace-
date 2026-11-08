package com.repospace.controller;

import com.repospace.model.Profile;
import com.repospace.model.SponsorTier;
import com.repospace.model.Sponsorship;
import com.repospace.model.User;
import com.repospace.util.ActivityLogger;
import com.repospace.util.Session;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class SponsorsController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label noteLabel;
    @FXML
    private Label introLabel;
    @FXML
    private ListView<SponsorTier> tierList;
    @FXML
    private Label tierDescriptionLabel;
    @FXML
    private VBox supporterBox;
    @FXML
    private Label pledgeStatusLabel;
    @FXML
    private TextField pledgeMessageField;
    @FXML
    private Button sponsorButton;
    @FXML
    private Button stopButton;
    @FXML
    private VBox ownerBox;
    @FXML
    private TextField tierNameField;
    @FXML
    private TextField tierAmountField;
    @FXML
    private TextField tierDescriptionField;
    @FXML
    private Button deleteTierButton;
    @FXML
    private Label totalLabel;
    @FXML
    private ListView<String> supporterList;
    @FXML
    private Label myPledgesHeading;
    @FXML
    private ListView<String> myPledgesList;
    @FXML
    private Label messageLabel;

    private User viewer;
    private User person;
    private boolean own;

    @FXML
    private void initialize() {
        viewer = Session.getCurrentUser();
        int target = Navigator.userID() == 0 ? viewer.getUserID() : Navigator.userID();
        own = target == viewer.getUserID();
        noteLabel.setText("Repospace only records pledges. No payment is made by this program - it shows who supports whom.");
        try {
            person = User.findById(target);
        } catch (SQLException e) {
            ActivityLogger.error("Could not open a sponsor page", e);
        }
        if (person == null) {
            titleLabel.setText("This sponsor page is not available");
            supporterBox.setVisible(false);
            supporterBox.setManaged(false);
            ownerBox.setVisible(false);
            ownerBox.setManaged(false);
            return;
        }
        titleLabel.setText(own ? "My sponsor page" : "Sponsor " + person.getUsername());
        supporterBox.setVisible(!own);
        supporterBox.setManaged(!own);
        ownerBox.setVisible(own);
        ownerBox.setManaged(own);
        myPledgesHeading.setVisible(own);
        myPledgesHeading.setManaged(own);
        myPledgesList.setVisible(own);
        myPledgesList.setManaged(own);

        tierList.getSelectionModel().selectedItemProperty().addListener((obs, o, tier) -> onTierSelected(tier));
        reload();
    }

    private void setMessage(String text, boolean isError) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().remove("message");
        if (isError) {
            messageLabel.getStyleClass().add("message");
        }
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private void reload() {
        try {
            boolean hidden = !own && Profile.forViewer(viewer.getUserID(), person.getUserID()).isHidden();
            List<SponsorTier> tiers = SponsorTier.forUser(person.getUserID(), viewer.getUserID());
            tierList.getItems().setAll(tiers);
            if (hidden) {
                introLabel.setText("This profile is private, so there is no sponsor page.");
            } else if (tiers.isEmpty()) {
                introLabel.setText(own ? "You have no tiers yet. Add one below so people can support you." : person.getUsername() + " has not set up any tiers yet.");
            } else {
                introLabel.setText(own ? "These are the tiers people can choose from." : "Choose a tier to support " + person.getUsername() + ".");
            }
            List<String> names = new ArrayList<>();
            for (Sponsorship s : Sponsorship.sponsorsOf(person.getUserID(), viewer.getUserID())) {
                names.add(s.getSponsorName() + "  -  " + s.getTierName() + (s.getMessage().isEmpty() ? "" : "  -  \"" + s.getMessage() + "\""));
            }
            supporterList.getItems().setAll(names);
            if (own) {
                totalLabel.setText("Pledged per month: " + SponsorTier.CURRENCY + Sponsorship.monthlyTotal(viewer.getUserID(), viewer.getUserID())
                        + "  from " + Sponsorship.countSponsors(viewer.getUserID()) + " supporters");
                List<String> mine = new ArrayList<>();
                for (Sponsorship s : Sponsorship.madeBy(viewer.getUserID())) {
                    mine.add(s.getSponsoredName() + "  -  " + s.getTierName() + " (" + SponsorTier.CURRENCY + s.getAmount() + " / month)");
                }
                myPledgesList.getItems().setAll(mine);
            }
        } catch (SQLException | IllegalStateException e) {
            ActivityLogger.error("Could not load the sponsor page", e);
            setMessage("Could not load this page: " + messageOf(e), true);
        }
        onTierSelected(tierList.getSelectionModel().getSelectedItem());
    }

    private void onTierSelected(SponsorTier tier) {
        tierDescriptionLabel.setText(tier == null ? "" : tier.getDescription().isEmpty() ? "(no description)" : tier.getDescription());
        deleteTierButton.setDisable(!own || tier == null);
        sponsorButton.setDisable(own || tier == null);
        try {
            Sponsorship current = own ? null : Sponsorship.find(viewer.getUserID(), person.getUserID());
            stopButton.setDisable(current == null);
            pledgeStatusLabel.setText(current == null ? "You are not sponsoring " + person.getUsername() + " yet."
                    : "You pledged " + SponsorTier.CURRENCY + current.getAmount() + " / month (" + current.getTierName() + "). Choose another tier to change it.");
        } catch (SQLException e) {
            ActivityLogger.error("Could not read the pledge", e);
        }
    }

    @FXML
    private void handleSponsor() {
        SponsorTier tier = tierList.getSelectionModel().getSelectedItem();
        try {
            Sponsorship.pledge(viewer, tier, pledgeMessageField.getText());
            pledgeMessageField.clear();
            reload();
            setMessage("Thank you! Your pledge was recorded (no payment was made).", false);
        } catch (SQLException | IllegalStateException | IllegalArgumentException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleStop() {
        try {
            Sponsorship.cancel(viewer.getUserID(), person.getUserID());
            reload();
            setMessage("Your pledge was stopped.", false);
        } catch (SQLException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleAddTier() {
        try {
            int amount;
            try {
                amount = Integer.parseInt(tierAmountField.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Type the amount per month as a whole number, for example 25.");
            }
            SponsorTier.create(viewer.getUserID(), tierNameField.getText(), amount, tierDescriptionField.getText());
            tierNameField.clear();
            tierAmountField.clear();
            tierDescriptionField.clear();
            reload();
            setMessage("Tier added.", false);
        } catch (SQLException | IllegalStateException | IllegalArgumentException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleDeleteTier() {
        SponsorTier tier = tierList.getSelectionModel().getSelectedItem();
        if (tier == null) {
            return;
        }
        try {
            tier.delete(viewer.getUserID());
            reload();
            setMessage("Tier deleted (pledges to it were removed).", false);
        } catch (SQLException | IllegalStateException e) {
            setMessage(messageOf(e), true);
        }
    }

    @FXML
    private void handleBack() {
        Navigator.back();
    }
}
