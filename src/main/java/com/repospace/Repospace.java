package com.repospace;

import com.repospace.database.DatabaseConnection;
import com.repospace.util.ActivityLogger;
import com.repospace.util.PagesServer;
import com.repospace.util.Session;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Repospace extends Application {

    private static Stage primaryStage;
    private static Repospace instance;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        instance = this;
        try {
            DatabaseConnection.initialise();
        } catch (SQLException e) {
            ActivityLogger.error("Database initialisation failed", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "The databases could not be opened:\n" + e.getMessage());
            alert.setHeaderText("Repospace cannot start");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        addLogoAsWindowIcon(stage);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        switchScene("login.fxml", "Repospace - Log in");
        stage.show();
        ActivityLogger.info("Repospace started");
    }

    @Override
    public void stop() {
        PagesServer.stop();
    }

    public static void openInBrowser(String url) {
        if (instance != null && url != null) {
            instance.getHostServices().showDocument(url);
        }
    }

    private static void addLogoAsWindowIcon(Stage stage) {
        for (String icon : new String[]{"icon-64.png", "icon-256.png"}) {
            java.net.URL url = Repospace.class.getResource("/com/repospace/images/" + icon);
            if (url != null) {
                stage.getIcons().add(new Image(url.toExternalForm()));
            }
        }
    }

    public static void switchScene(String fxmlFile, String title) {
        try {
            Parent root = FXMLLoader.load(Repospace.class.getResource("/com/repospace/view/" + fxmlFile));
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, 980, 640);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            applyTheme(scene);
            primaryStage.setTitle(title);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load screen " + fxmlFile, e);
        }
    }

    public static void applyTheme(Scene scene) {
        String sheet = "Dark".equalsIgnoreCase(Session.getTheme()) ? "dark.css" : "light.css";
        scene.getStylesheets().setAll(Repospace.class.getResource("/com/repospace/css/" + sheet).toExternalForm());
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
