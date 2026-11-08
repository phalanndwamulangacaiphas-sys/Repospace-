package com.repospace.controller;

import com.repospace.model.Profile;
import com.repospace.model.User;
import com.repospace.util.DateUtil;
import com.repospace.util.Session;
import java.io.File;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

public class ProfileController {

    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextArea bioArea;
    @FXML
    private TextField statusField;
    @FXML
    private TextArea readmeArea;
    @FXML
    private TextField pictureField;
    @FXML
    private ImageView pictureView;
    @FXML
    private ComboBox<String> visibilityBox;
    @FXML
    private Label joinedLabel;
    @FXML
    private Label messageLabel;

    private User user;
    private Profile profile;

    @FXML
    private void initialize() {
        user = Session.getCurrentUser();
        try {
            profile = Profile.forUser(user.getUserID());
            usernameField.setText(user.getUsername());
            emailField.setText(user.getEmail());
            bioArea.setText(profile.getBio());
            statusField.setText(profile.getStatus());
            readmeArea.setText(profile.getReadme());
            pictureField.setText(profile.getProfilePicture());
            visibilityBox.getItems().setAll(Profile.VISIBILITIES);
            visibilityBox.setValue(profile.getVisibility());
            joinedLabel.setText("Member since " + DateUtil.format(profile.getJoinDate()));
            showPicture();
        } catch (SQLException e) {
            messageLabel.setText("Could not load your profile: " + e.getMessage());
        }
    }

    private void showPicture() {
        String path = pictureField.getText();
        if (path != null && !path.isBlank() && new File(path).isFile()) {
            pictureView.setImage(new Image(new File(path).toURI().toString(), 110, 110, true, true));
        } else {
            pictureView.setImage(null);
        }
    }

    @FXML
    private void handleChoosePicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a profile picture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(pictureField.getScene().getWindow());
        if (file != null) {
            pictureField.setText(file.getAbsolutePath());
            showPicture();
        }
    }

    @FXML
    private void handleViewMyPage() {
        Navigator.openUserPage(user.getUserID(), "profile.fxml");
    }

    @FXML
    private void handleSave() {
        try {
            user.setUsername(usernameField.getText());
            user.setEmail(emailField.getText());
            profile.editProfile(bioArea.getText(), pictureField.getText());
            profile.editReadme(readmeArea.getText(), statusField.getText());
            profile.changeVisibility(visibilityBox.getValue());
            user.updateProfile();
            profile.updateProfile();
            showPicture();
            messageLabel.setText("Profile saved.");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}
