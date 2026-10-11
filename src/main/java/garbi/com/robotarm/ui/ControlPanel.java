package garbi.com.robotarm.ui;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import garbi.com.robotarm.motion.MotionController;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/** Sliders por servo, velocidad global y una demo de movimiento animado. */
public class ControlPanel extends VBox {

    private final RobotArm arm;
    private final MotionController motion;
    private Thread demoThread;

    public ControlPanel(RobotArm arm, MotionController motion) {
        this.arm = arm;
        this.motion = motion;

        setSpacing(14);
        setPadding(new Insets(15));
        setPrefWidth(260);
        setStyle("-fx-background-color: #f0f1f3;");

        Label title = new Label("Control manual");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        getChildren().add(title);

        for (Servo servo : arm.getServos()) {
            getChildren().add(servoRow(servo));
        }

        Button reset = new Button("Posición inicial");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(e -> {
            stopDemo();
            arm.resetHome();
        });

        getChildren().addAll(new Separator(), speedRow(), reset, demoButtons());
    }

    private VBox servoRow(Servo servo) {
        Label name = new Label(servo.getName());
        Label value = new Label();
        value.textProperty().bind(servo.angleProperty().asString("%.0f°"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Slider slider = new Slider(servo.getMin(), servo.getMax(), servo.getAngle());
        slider.valueProperty().bindBidirectional(servo.angleProperty());

        return new VBox(4, new HBox(name, spacer, value), slider);
    }

    private VBox speedRow() {
        Label name = new Label("Velocidad global");
        Label value = new Label();
        value.textProperty().bind(motion.speedFactorProperty().asString("%.1fx"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Slider slider = new Slider(0.2, 3.0, 1.0);
        slider.valueProperty().bindBidirectional(motion.speedFactorProperty());

        return new VBox(4, new HBox(name, spacer, value), slider);
    }

    private HBox demoButtons() {
        Button demo = new Button("▶ Demo");
        demo.setOnAction(e -> runDemo());
        Button stop = new Button("■ Parar");
        stop.setOnAction(e -> stopDemo());
        HBox box = new HBox(8, demo, stop);
        return box;
    }

    // ---------- demo ----------

    private void runDemo() {
        stopDemo();
        demoThread = new Thread(() -> {
            try {
                move(Map.of(arm.base(), 45.0, arm.shoulder(), 130.0, arm.elbow(), 50.0, arm.gripper(), 90.0));
                Thread.sleep(300);
                move(Map.of(arm.shoulder(), 150.0, arm.elbow(), 80.0, arm.wristPitch(), 60.0));
                move(Map.of(arm.gripper(), 5.0));              // cerrar garra
                Thread.sleep(300);
                move(Map.of(arm.base(), 135.0, arm.shoulder(), 100.0, arm.elbow(), 100.0,
                        arm.wristPitch(), 90.0, arm.wristRoll(), 160.0));
                move(Map.of(arm.gripper(), 90.0));             // soltar
                Thread.sleep(300);
                for (Servo s : arm.getServos()) { /* volver a casa */ }
                move(homeTargets());
            } catch (InterruptedException | CancellationException | CompletionException ignored) {
                // demo detenida
            }
        }, "demo-thread");
        demoThread.setDaemon(true);
        demoThread.start();
    }

    private void stopDemo() {
        if (demoThread != null) demoThread.interrupt();
        motion.stop();
    }

    private void move(Map<Servo, Double> targets) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        motion.moveTo(targets).join(); // bloquea este hilo (no el de la UI) hasta llegar
    }

    private Map<Servo, Double> homeTargets() {
        Map<Servo, Double> m = new java.util.LinkedHashMap<>();
        arm.getServos().forEach(s -> m.put(s, s.getHome()));
        return m;
    }
}