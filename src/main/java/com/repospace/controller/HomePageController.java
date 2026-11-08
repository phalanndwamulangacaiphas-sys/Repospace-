package com.repospace.controller;

import com.repospace.Repospace;
import com.repospace.model.Commit;
import com.repospace.model.Projects;
import com.repospace.model.Repository;
import com.repospace.model.Settings;
import com.repospace.model.User;
import com.repospace.util.ActivityLogger;
import com.repospace.util.PagesServer;
import com.repospace.util.Session;
import java.io.IOException;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class HomePageController {

    @FXML
    private Label welcomeLabel;
    @FXML
    private StackPane contentArea;

    private int userID;
    private String currentView = "";
    private int notifications;

    @FXML
    private void initialize() {
        User user = Session.getCurrentUser();
        if (user == null) {
            Repospace.switchScene("login.fxml", "Repospace - Log in");
            return;
        }
        userID = user.getUserID();
        Navigator.register(contentArea, this::displayHome);
        startPagesIfNeeded();
        welcomeLabel.setText("Signed in as\n" + user.getUsername());
        displayHome();
    }

    private void startPagesIfNeeded() {
        try {
            if (!Repository.findPagesSites().isEmpty()) {
                PagesServer.start(PagesServer.DEFAULT_PORT);
            }
        } catch (SQLException | IOException e) {
            ActivityLogger.error("Could not start the Pages server", e);
        }
    }

    @FXML
    public void displayHome() {
        Navigator.clearHistory();
        currentView = "Home";
        VBox box = new VBox(10);
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(24));
        Label title = new Label("Home");
        title.getStyleClass().add("title");
        box.getChildren().add(title);
        try {
            int projectCount = Projects.findByUser(userID).size();
            int repoCount = Repository.findByOwner(userID).size();
            box.getChildren().add(new Label("Projects: " + projectCount));
            box.getChildren().add(new Label("Repositories: " + repoCount));
            notifications = Settings.forUser(userID).isNotifications() ? Commit.countRecentForOwner(userID, 7) : 0;
            if (Settings.forUser(userID).isNotifications()) {
                box.getChildren().add(new Label("Commits in the last 7 days: " + notifications));
            } else {
                box.getChildren().add(new Label("Notifications are turned off (see Settings)."));
            }
        } catch (SQLException e) {
            ActivityLogger.error("Could not load the home summary", e);
            box.getChildren().add(new Label("Could not load your summary: " + e.getMessage()));
        }
        contentArea.getChildren().setAll(box);
    }

    @FXML
    public void viewProjects() {
        show("Projects", "projects.fxml");
    }

    @FXML
    public void viewRepositories() {
        show("Repositories", "repositories.fxml");
    }

    @FXML
    public void searchUsers() {
        show("Discover", "discover.fxml");
    }

    @FXML
    public void viewMyPage() {
        Navigator.clearHistory();
        Navigator.openUserPage(userID, "home.fxml");
        currentView = "My page";
    }

    @FXML
    public void viewProfile() {
        show("Profile", "profile.fxml");
    }

    @FXML
    public void openSettings() {
        show("Settings", "settings.fxml");
    }

    @FXML
    public void logout() {
        User user = Session.getCurrentUser();
        if (user != null) {
            user.logout();
        }
        Repospace.switchScene("login.fxml", "Repospace - Log in");
    }

    private void show(String viewName, String fxmlFile) {
        Navigator.clearHistory();
        try {
            Node view = FXMLLoader.load(HomePageController.class.getResource("/com/repospace/view/" + fxmlFile));
            contentArea.getChildren().setAll(view);
            currentView = viewName;
        } catch (IOException e) {
            ActivityLogger.error("Could not open view " + fxmlFile, e);
            contentArea.getChildren().setAll(new Label("Could not open this view: " + e.getMessage()));
        }
    }

    public int getUserID() {
        return userID;
    }

    public String getCurrentView() {
        return currentView;
    }

    public int getNotifications() {
        return notifications;
    }
}
