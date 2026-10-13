package garbi.com.robotarm.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Menú principal: nuevo proyecto o abrir uno existente. */
public class StartScreen extends VBox {

    public StartScreen(Runnable onNew, Runnable onOpen) {
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(30));
        setStyle("-fx-background-color: #2b2f36;");

        Label title = new Label("Robot Arm Programmer");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");
        Label subtitle = new Label("Programa tu brazo robótico con bloques");
        subtitle.setStyle("-fx-text-fill: #aab2bd; -fx-font-size: 13px;");
        VBox.setMargin(subtitle, new Insets(0, 0, 18, 0));

        Button newBtn = bigButton("Nuevo proyecto");
        newBtn.setOnAction(e -> onNew.run());
        Button openBtn = bigButton("Abrir proyecto…");
        openBtn.setOnAction(e -> onOpen.run());

        getChildren().addAll(title, subtitle, newBtn, openBtn);
    }

    private static Button bigButton(String text) {
        Button b = new Button(text);
        b.setPrefWidth(240);
        b.setPrefHeight(40);
        b.setStyle("-fx-font-size: 14px;");
        return b;
    }
}