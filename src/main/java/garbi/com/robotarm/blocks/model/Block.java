package garbi.com.robotarm.blocks.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Un bloque del programa.
 *  - MOVE_SERVO: servo + value (grados)
 *  - WAIT:       value (segundos)
 *  - REPEAT:     value (veces) + children
 */
public class Block {

    private BlockType type;
    private String servo;
    private double value;
    private List<Block> children = new ArrayList<>();

    public Block(BlockType type) { this.type = type; }

    // ---- fábricas para armar programas por código ----
    public static Block moveServo(String servo, double angle) {
        Block b = new Block(BlockType.MOVE_SERVO);
        b.servo = servo;
        b.value = angle;
        return b;
    }
    public static Block openGripper()  { return new Block(BlockType.OPEN_GRIPPER); }
    public static Block closeGripper() { return new Block(BlockType.CLOSE_GRIPPER); }
    public static Block home()         { return new Block(BlockType.HOME); }

    public static Block waitSeconds(double seconds) {
        Block b = new Block(BlockType.WAIT);
        b.value = seconds;
        return b;
    }

    public static Block repeat(int times, Block... body) {
        Block b = new Block(BlockType.REPEAT);
        b.value = times;
        b.children.addAll(Arrays.asList(body));
        return b;
    }

    // ---- acceso ----
    public BlockType getType() { return type; }
    public String getServo() { return servo; }
    public void setServo(String servo) { this.servo = servo; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public List<Block> getChildren() { return children; }

    public String describe() {
        return switch (type) {
            case MOVE_SERVO -> "Mover " + servo + " a " + Math.round(value) + "°";
            case WAIT       -> "Esperar " + value + " s";
            case REPEAT     -> "Repetir " + Math.round(value) + " veces";
            default         -> type.getLabel();
        };
    }
}