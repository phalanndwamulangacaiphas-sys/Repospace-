package com.repospace.controller;

import com.repospace.model.Badge;
import com.repospace.model.PinnedRepositories;
import com.repospace.model.Profile;
import com.repospace.model.ProfileStats;
import com.repospace.model.Projects;
import com.repospace.model.Repository;
import com.repospace.model.User;
import com.repospace.util.ActivityLogger;
import com.repospace.util.DateUtil;
import com.repospace.util.Session;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class UserPageController {

    @FXML
    private Button backButton;
    @FXML
    private Button sponsorButton;
    @FXML
    private ImageView pictureView;
    @FXML
    private Label nameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label joinedLabel;
    @FXML
    private Label bioLabel;
    @FXML
    private FlowPane badgeBox;
    @FXML
    private VBox readmeSection;
    @FXML
    private VBox readmeBox;
    @FXML
    private VBox languageSection;
    @FXML
    private VBox languageBox;
    @FXML
    private VBox pinnedSection;
    @FXML
    private ListView<Repository> pinnedList;
    @FXML
    private Label repoHeading;
    @FXML
    private ListView<Repository> repoList;
    @FXML
    private Label projectHeading;
    @FXML
    private ListView<Projects> projectList;
    @FXML
    private VBox activitySection;
    @FXML
    private ListView<String> activityList;
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
        try {
            person = User.findById(target);
            if (person == null) {
                nameLabel.setText("This user does not exist");
                hideEverything();
                return;
            }
            setupLists();
            show();
        } catch (SQLException e) {
            ActivityLogger.error("Could not open a user page", e);
            messageLabel.setText("Could not load this page: " + e.getMessage());
            hideEverything();
        }
    }

    private void setupLists() {
        pinnedList.setCellFactory(list -> repoCell());
        repoList.setCellFactory(list -> repoCell());
        projectList.setCellFactory(list -> new ListCell<Projects>() {
            @Override
            protected void updateItem(Projects p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getName() + "  [" + p.getStatus() + ", " + p.getVisibility() + "]");
            }
        });
        pinnedList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openRepo(pinnedList.getSelectionModel().getSelectedItem());
            }
        });
        repoList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openRepo(repoList.getSelectionModel().getSelectedItem());
            }
        });
        projectList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Projects p = projectList.getSelectionModel().getSelectedItem();
                if (p != null) {
                    Navigator.openBoard(p.getProjectID(), "userpage.fxml");
                }
            }
        });
    }

    private static ListCell<Repository> repoCell() {
        return new ListCell<Repository>() {
            @Override
            protected void updateItem(Repository r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.getName() + (r.isPublic() ? "" : "  [private]")
                        + (r.getDescription().isEmpty() ? "" : "  -  " + r.getDescription()));
            }
        };
    }

    private void openRepo(Repository repo) {
        if (repo != null) {
            Navigator.openRepository(repo.getRepoID(), "userpage.fxml");
        }
    }

    private void hideEverything() {
        for (Node n : new Node[]{badgeBox, readmeSection, languageSection, pinnedSection, repoHeading, repoList, projectHeading, projectList, activitySection}) {
            n.setVisible(false);
            n.setManaged(false);
        }
        sponsorButton.setDisable(true);
    }

    private void show() throws SQLException {
        nameLabel.setText(person.getUsername() + (own ? "  (this is how others see you)" : ""));
        Profile profile = Profile.forViewer(viewer.getUserID(), person.getUserID());
        sponsorButton.setText(own ? "My sponsor page" : "Sponsor " + person.getUsername());

        List<Repository> repos = own ? Repository.findByOwner(person.getUserID()) : Repository.findPublicByOwner(person.getUserID());
        List<Projects> projects = own ? Projects.findByUser(person.getUserID()) : Projects.findPublicByUser(person.getUserID());
        repoList.getItems().setAll(repos);
        projectList.getItems().setAll(projects);

        if (profile.isHidden()) {
            statusLabel.setText("");
            joinedLabel.setText("This profile is private.");
            bioLabel.setText("");
            for (Node n : new Node[]{badgeBox, readmeSection, languageSection, pinnedSection, activitySection}) {
                n.setVisible(false);
                n.setManaged(false);
            }
            sponsorButton.setDisable(true);
            return;
        }
        statusLabel.setText(profile.getStatus().isEmpty() ? "" : "Now: " + profile.getStatus());
        joinedLabel.setText("Member since " + DateUtil.format(profile.getJoinDate()));
        bioLabel.setText(profile.getBio());
        String picture = profile.getProfilePicture();
        if (!picture.isBlank() && new File(picture).isFile()) {
            pictureView.setImage(new Image(new File(picture).toURI().toString(), 96, 96, true, true));
        }

        ProfileStats stats = ProfileStats.of(viewer.getUserID(), person.getUserID());
        for (Badge badge : stats.badges()) {
            Label label = new Label(badge.getText());
            label.getStyleClass().add("badge");
            label.setStyle("-fx-background-color: " + badge.getColor() + ";");
            badgeBox.getChildren().add(label);
        }

        String readme = stats.expand(profile.getReadme());
        if (readme.isBlank()) {
            readme = own ? "*You have no profile README yet. Write one under Edit profile - you can use placeholders like {{commits}} and {{streak}}.*" : "*No profile README.*";
        }
        readmeBox.getChildren().setAll(MarkdownView.render(readme, url -> {
            if (!url.startsWith("wiki:")) {
                com.repospace.Repospace.openInBrowser(url);
            }
        }));

        Map<String, Integer> languages = stats.languagePercentages();
        if (languages.isEmpty()) {
            languageBox.getChildren().add(new Label("No code saved yet."));
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> e : languages.entrySet()) {
                if (shown++ == 5) {
                    break;
                }
                languageBox.getChildren().add(new Label(String.format("%-12s %3d%%  %s", e.getKey(), e.getValue(), "█".repeat(Math.max(1, e.getValue() / 4)))));
            }
        }

        pinnedList.getItems().setAll(PinnedRepositories.pinnedFor(person.getUserID(), viewer.getUserID()));
        activityList.getItems().setAll(stats.recentActivity(15));
    }

    @FXML
    private void handleBack() {
        Navigator.back();
    }

    @FXML
    private void handleSponsor() {
        if (person != null) {
            Navigator.openSponsors(person.getUserID(), "userpage.fxml");
        }
    }
}
