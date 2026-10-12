package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.BlockType;
import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.paint.Color;

import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Dibujo de un Block.
 *  - Constructor de 1 argumento: solo lectura (paleta, fantasma de arrastre).
 *  - Constructor de 3 argumentos: con campos editables que escriben en el modelo.
 */
public class BlockNode extends javafx.scene.layout.HBox {

    public static final double MAX_WIDTH = 320;

    private final Block block;

    public BlockNode(Block block) {
        this(block, null, null);
    }

    public BlockNode(Block block, RobotArm arm, ReadOnlyBooleanProperty locked) {
        this.block = block;

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setPadding(new Insets(7, 14, 7, 14));
        setMaxWidth(MAX_WIDTH);
        setCursor(Cursor.OPEN_HAND);
        setStyle("-fx-background-color: " + colorOf(block.getType()) + ";"
                + "-fx-background-radius: 7;"
                + "-fx-border-color: rgba(0,0,0,0.25);"
                + "-fx-border-radius: 7;");

        if (arm == null) getChildren().add(text(block.describe()));
        else buildEditable(arm, locked);
    }

    public Block getBlock() { return block; }

    // ---------- contenido editable ----------

    private void buildEditable(RobotArm arm, ReadOnlyBooleanProperty locked) {
        switch (block.getType()) {
            case MOVE_SERVO -> {
                TextField angle = numberField(() -> servoRange(arm), locked);

                ComboBox<String> servo = new ComboBox<>();
                arm.getServos().forEach(s -> servo.getItems().add(s.getName()));
                servo.setValue(block.getServo());
                servo.setPrefWidth(135);
                servo.disableProperty().bind(locked);
                servo.valueProperty().addListener((o, old, name) -> {
                    if (name == null) return;
                    block.setServo(name);
                    double[] r = servoRange(arm);
                    block.setValue(clamp(block.getValue(), r[0], r[1]));
                    angle.setText(format(block.getValue()));
                });

                getChildren().addAll(text("Mover"), servo, text("a"), angle, text("°"));
            }
            case WAIT -> {
                TextField seconds = numberField(() -> new double[]{0, 60}, locked);
                getChildren().addAll(text("Esperar"), seconds, text("s"));
            }
            default -> getChildren().add(text(block.describe()));
        }
    }

    /** Campo numérico: escribe en block.value al teclear, limitado al rango dado. */
    private TextField numberField(Supplier<double[]> range, ReadOnlyBooleanProperty locked) {
        TextField field = new TextField(format(block.getValue()));
        field.setPrefWidth(58);
        field.disableProperty().bind(locked);

        // hasta 3 dígitos enteros y 2 decimales
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,3}([.,]\\d{0,2})?") ? change : null));

        field.textProperty().addListener((o, old, text) -> {
            Double v = parse(text);
            if (v != null) {
                double[] r = range.get();
                block.setValue(clamp(v, r[0], r[1]));
            }
        });

        // al salir (o con Enter) el texto se corrige al valor real del modelo
        field.focusedProperty().addListener((o, was, focused) -> {
            if (!focused) field.setText(format(block.getValue()));
        });
        field.setOnAction(e -> field.setText(format(block.getValue())));

        return field;
    }

    // ---------- helpers ----------

    private double[] servoRange(RobotArm arm) {
        try {
            Servo s = arm.byName(block.getServo());
            return new double[]{s.getMin(), s.getMax()};
        } catch (IllegalArgumentException e) {
            return new double[]{0, 180};
        }
    }

    private static Label text(String s) {
        Label l = new Label(s);
        l.setTextFill(Color.WHITE);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    private static Double parse(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null; // vacío o "." : se ignora hasta que haya un número válido
        }
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String colorOf(BlockType type) {
        return switch (type) {
            case MOVE_SERVO, HOME            -> "#4c97ff"; // movimiento
            case OPEN_GRIPPER, CLOSE_GRIPPER -> "#9966ff"; // garra
            case WAIT, REPEAT                -> "#ffab19"; // control
        };
    }
}