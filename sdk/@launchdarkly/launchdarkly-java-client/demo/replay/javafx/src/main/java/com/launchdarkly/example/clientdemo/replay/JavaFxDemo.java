package com.launchdarkly.example.clientdemo.replay;

import com.launchdarkly.observability.LDReplay;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * A JavaFX window recorded for session replay. With the default privacy profile the text field and
 * the password are masked; the note is masked by its {@code ld-mask} style class; the rest is shown.
 */
public final class JavaFxDemo extends Application {

    static ReplayDemo replay;

    @Override
    public void start(Stage stage) {
        Label title = new Label("Account settings (JavaFX)");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField email = new TextField("jane@example.com");
        PasswordField password = new PasswordField();
        password.setText("hunter2");
        Button save = new Button("Save");
        save.setId("save");
        save.setOnAction(event -> replay.save());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Email"), email);
        form.addRow(1, new Label("Password"), password);
        form.add(save, 1, 2);

        Label note = new Label(ReplayDemo.NOTE);
        note.getStyleClass().add(LDReplay.MASK_STYLE_CLASS);

        ListView<String> flags = new ListView<>(FXCollections.observableArrayList(ReplayDemo.FLAGS));
        flags.setPrefHeight(170);

        VBox root = new VBox(12, title, form, note, new Label("Flags"), flags, new Label(replay.status()));
        root.setPadding(new Insets(16));
        stage.setTitle("Session replay demo: JavaFX");
        stage.setScene(new Scene(root, 640, 520));
        stage.show();
    }
}
