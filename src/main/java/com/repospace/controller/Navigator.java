package com.repospace.controller;

import com.repospace.util.ActivityLogger;
import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public final class Navigator {

    private static StackPane contentArea;
    private static int repositoryID;
    private static int projectID;
    private static int userID;
    private static final java.util.ArrayDeque<Frame> HISTORY = new java.util.ArrayDeque<>();

    private static final class Frame {
        private final String view;
        private final int repo;
        private final int project;
        private final int user;

        Frame(String view) {
            this.view = view;
            this.repo = repositoryID;
            this.project = projectID;
            this.user = userID;
        }
    }

    private Navigator() {
    }

    private static Runnable homeAction;

    static void register(StackPane area, Runnable showHome) {
        contentArea = area;
        homeAction = showHome;
        HISTORY.clear();
    }

    static void clearHistory() {
        HISTORY.clear();
    }

    public static void open(String fxmlFile) {
        if (contentArea == null) {
            return;
        }
        if ("home.fxml".equals(fxmlFile)) {
            if (homeAction != null) {
                homeAction.run();
            }
            return;
        }
        try {
            Node view = FXMLLoader.load(Navigator.class.getResource("/com/repospace/view/" + fxmlFile));
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            ActivityLogger.error("Could not open view " + fxmlFile, e);
            contentArea.getChildren().setAll(new Label("Could not open this view: " + e.getMessage()));
        }
    }

    public static void openRepository(int repoID, String backTo) {
        HISTORY.push(new Frame(backTo));
        repositoryID = repoID;
        open("repopage.fxml");
    }

    public static void openBoard(int project, String backTo) {
        HISTORY.push(new Frame(backTo));
        projectID = project;
        open("board.fxml");
    }

    public static void openUserPage(int user, String backTo) {
        HISTORY.push(new Frame(backTo));
        userID = user;
        open("userpage.fxml");
    }

    public static void openSponsors(int user, String backTo) {
        HISTORY.push(new Frame(backTo));
        userID = user;
        open("sponsors.fxml");
    }

    public static void back() {
        if (HISTORY.isEmpty()) {
            open("home.fxml");
            return;
        }
        Frame frame = HISTORY.pop();
        repositoryID = frame.repo;
        projectID = frame.project;
        userID = frame.user;
        open(frame.view);
    }

    public static int repositoryID() {
        return repositoryID;
    }

    public static int projectID() {
        return projectID;
    }

    public static int userID() {
        return userID;
    }
}
