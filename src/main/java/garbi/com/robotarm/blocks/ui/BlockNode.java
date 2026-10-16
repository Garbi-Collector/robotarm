package garbi.com.robotarm.blocks.ui;

import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Supplier;

/**
 * Dibujo de un Block.
 *  - BlockNode(block): solo lectura (paleta, fantasma de arrastre).
 *  - BlockNode(block, ctx): editable. Los contenedores incluyen un BlockStack interior.
 */
public class BlockNode extends VBox {

    public static final double MAX_WIDTH = 320;

    private final Block block;
    private final HBox header = new HBox(6);
    private BlockStack inner; // solo en contenedores editables

    public BlockNode(Block block) {
        this(block, null);
    }

    public BlockNode(Block block, BlockContext ctx) {
        this.block = block;

        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(7, 14, 7, 14));
        getChildren().add(header);

        setMaxWidth(block.getType().isContainer() ? Double.MAX_VALUE : MAX_WIDTH);
        setCursor(Cursor.OPEN_HAND);
        setStyle("-fx-background-color: " + colorOf(block.getType()) + ";"
                + "-fx-background-radius: 7;"
                + "-fx-border-color: rgba(0,0,0,0.25);"
                + "-fx-border-radius: 7;");

        if (ctx == null) header.getChildren().add(text(block.describe()));
        else buildEditable(ctx);
    }

    public Block getBlock() { return block; }

    /** Stack interior de un contenedor editable; null en cualquier otro caso. */
    public BlockStack getInnerStack() { return inner; }
    /** Ilumina el bloque mientras se ejecuta. */
    public void setActive(boolean on) {
        setEffect(on ? new DropShadow(18, Color.web("#ffe600")) : null);
    }
    // ---------- contenido editable ----------

    private void buildEditable(BlockContext ctx) {
        RobotArm arm = ctx.getArm();
        ReadOnlyBooleanProperty locked = ctx.getLocked();

        switch (block.getType()) {
            case MOVE_SERVO -> {
                TextField angle = numberField(() -> servoRange(arm), false, locked);

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

                header.getChildren().addAll(text("Mover"), servo, text("a"), angle, text("°"));
            }
            case WAIT -> {
                TextField seconds = numberField(() -> new double[]{0, 60}, false, locked);
                header.getChildren().addAll(text("Esperar"), seconds, text("s"));
            }
            case REPEAT -> {
                TextField times = numberField(() -> new double[]{1, 999}, true, locked);
                header.getChildren().addAll(text("Repetir"), times, text("veces"));

                inner = new BlockStack(block.getChildren(), ctx, true);
                VBox.setMargin(inner, new Insets(0, 8, 8, 18));
                getChildren().add(inner);
            }
            case CALL_METHOD -> {
                ComboBox<String> methods = new ComboBox<>();
                methods.getItems().addAll(ctx.getProgram().methodNames());
                methods.setPromptText("elegir…");
                methods.setPrefWidth(170);
                methods.setValue(block.getMethod());
                methods.disableProperty().bind(locked);
                methods.valueProperty().addListener((o, old, name) -> {
                    if (name != null) block.setMethod(name);
                });
                header.getChildren().addAll(text("Método"), methods);

                if (block.getMethod() != null && ctx.getProgram().findMethod(block.getMethod()) == null) {
                    header.getChildren().add(text("⚠ no existe"));
                }
            }
            case POSE -> {
                header.getChildren().add(text(block.describe()));
                Tooltip.install(this, new Tooltip(block.poseDetails()));
            }
            default -> header.getChildren().add(text(block.describe()));
        }
    }

    /** Campo numérico: escribe en block.value al teclear, limitado al rango dado. */
    private TextField numberField(Supplier<double[]> range, boolean integer,
                                  ReadOnlyBooleanProperty locked) {
        TextField field = new TextField(format(block.getValue()));
        field.setPrefWidth(58);
        field.disableProperty().bind(locked);

        String regex = integer ? "\\d{0,3}" : "\\d{0,3}([.,]\\d{0,2})?";
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches(regex) ? change : null));

        field.textProperty().addListener((o, old, text) -> {
            Double v = parse(text);
            if (v != null) {
                double[] r = range.get();
                block.setValue(clamp(v, r[0], r[1]));
            }
        });

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
            return null;
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
            case MOVE_SERVO, HOME, POSE      -> "#4c97ff";
            case OPEN_GRIPPER, CLOSE_GRIPPER -> "#9966ff";
            case WAIT, REPEAT                -> "#ffab19";
            case CALL_METHOD                 -> "#ff6680";
        };
    }
}