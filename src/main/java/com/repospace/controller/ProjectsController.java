package com.repospace.controller;

import com.repospace.model.Projects;
import com.repospace.util.DateUtil;
import com.repospace.util.Session;
import java.sql.SQLException;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class ProjectsController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField descriptionField;
    @FXML
    private TableView<Projects> projectTable;
    @FXML
    private TableColumn<Projects, String> nameColumn;
    @FXML
    private TableColumn<Projects, String> descriptionColumn;
    @FXML
    private TableColumn<Projects, String> statusColumn;
    @FXML
    private TableColumn<Projects, String> visibilityColumn;
    @FXML
    private TableColumn<Projects, String> createdColumn;
    @FXML
    private ComboBox<String> newVisibilityBox;
    @FXML
    private ComboBox<String> statusBox;
    @FXML
    private Label messageLabel;

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        descriptionColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        visibilityColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVisibility()));
        newVisibilityBox.getItems().setAll(Projects.VISIBILITIES);
        newVisibilityBox.setValue("private");
        createdColumn.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getCreatedDate())));
        statusBox.getItems().setAll(Projects.STATUSES);
        statusBox.setValue(Projects.STATUSES.get(0));
        refresh();
    }

    private void refresh() {
        try {
            projectTable.getItems().setAll(Projects.findByUser(Session.getCurrentUser().getUserID()));
        } catch (SQLException e) {
            messageLabel.setText("Could not load projects: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddProject() {
        try {
            new Projects(Session.getCurrentUser().getUserID(), nameField.getText(), descriptionField.getText(),
                    newVisibilityBox.getValue()).createProject();
            nameField.clear();
            descriptionField.clear();
            messageLabel.setText("Project added.");
            refresh();
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdateStatus() {
        Projects selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select a project first.");
            return;
        }
        try {
            selected.updateStatus(statusBox.getValue());
            messageLabel.setText("Status updated.");
            refresh();
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleOpenBoard() {
        Projects selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select a project first.");
            return;
        }
        Navigator.openBoard(selected.getProjectID(), "projects.fxml");
    }

    @FXML
    private void handleToggleVisibility() {
        Projects selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select a project first.");
            return;
        }
        String next = selected.isPublic() ? "private" : "public";
        if ("public".equals(next)) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Everybody who uses Repospace will be able to see '" + selected.getName() + "' on your profile. Make it public?",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Please confirm");
            if (alert.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) {
                return;
            }
        }
        try {
            selected.changeVisibility(next);
            messageLabel.setText("'" + selected.getName() + "' is now " + next + ".");
            refresh();
        } catch (SQLException | IllegalStateException e) {
            messageLabel.setText("Could not change it: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        Projects selected = projectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select a project first.");
            return;
        }
        try {
            selected.deleteProject();
            messageLabel.setText("Project deleted.");
            refresh();
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}
