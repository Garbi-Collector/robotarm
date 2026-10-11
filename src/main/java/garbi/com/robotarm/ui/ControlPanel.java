package garbi.com.robotarm.ui;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Un slider por servo, enlazado bidireccionalmente al ángulo. */
public class ControlPanel extends VBox {

    public ControlPanel(RobotArm arm) {
        setSpacing(14);
        setPadding(new Insets(15));
        setPrefWidth(260);
        setStyle("-fx-background-color: #f0f1f3;");

        Label title = new Label("Control manual");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        getChildren().add(title);

        for (Servo servo : arm.getServos()) {
            getChildren().add(row(servo));
        }

        Button reset = new Button("Posición inicial");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(e -> arm.resetHome());
        getChildren().add(reset);
    }

    private VBox row(Servo servo) {
        Label name = new Label(servo.getName());
        Label value = new Label();
        value.textProperty().bind(servo.angleProperty().asString("%.0f°"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(name, spacer, value);

        Slider slider = new Slider(servo.getMin(), servo.getMax(), servo.getAngle());
        slider.valueProperty().bindBidirectional(servo.angleProperty());

        return new VBox(4, header, slider);
    }
}