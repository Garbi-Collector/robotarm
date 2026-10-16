package garbi.com.robotarm.blocks.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Un bloque del programa.
 *  - MOVE_SERVO:  servo + value (grados)
 *  - WAIT:        value (segundos)
 *  - REPEAT:      value (veces) + children
 *  - CALL_METHOD: method (nombre del método)
 */
public class Block {

    private BlockType type;
    private String servo;
    private double value;
    private String method;
    private List<Block> children = new ArrayList<>();
    private Map<String, Double> pose;

    /** Lo usa Gson al leer archivos. */
    private Block() {}

    public Block(BlockType type) { this.type = type; }

    // ---- fábricas ----
    public static Block moveServo(String servo, double angle) {
        Block b = new Block(BlockType.MOVE_SERVO);
        b.servo = servo;
        b.value = angle;
        return b;
    }
    public static Block pose(Map<String, Double> angles) {
        Block b = new Block(BlockType.POSE);
        b.pose = new LinkedHashMap<>(angles);
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

    public static Block callMethod(String name) {
        Block b = new Block(BlockType.CALL_METHOD);
        b.method = name;
        return b;
    }

    // ---- acceso ----
    public BlockType getType() { return type; }
    public String getServo() { return servo; }
    public void setServo(String servo) { this.servo = servo; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<Block> getChildren() { return children; }
    public Map<String, Double> getPose() { return pose == null ? Map.of() : pose; }

    public String poseDetails() {
        StringBuilder sb = new StringBuilder();
        getPose().forEach((k, v) -> sb.append(k).append(": ").append(Math.round(v)).append("°\n"));
        return sb.toString().trim();
    }

    /** Copia profunda (incluye los hijos). */
    public Block copy() {
        Block b = new Block(type);
        b.servo = servo;
        b.value = value;
        b.method = method;
        if (pose != null) b.pose = new LinkedHashMap<>(pose);
        for (Block c : children) b.children.add(c.copy());
        return b;
    }

    /** ¿Este bloque, o alguno de sus hijos, usa un método? */
    public boolean usesMethods() {
        if (type == BlockType.CALL_METHOD) return true;
        for (Block c : children) if (c.usesMethods()) return true;
        return false;
    }

    /** ¿Este bloque, o alguno de sus hijos, llama al método con ese nombre? */
    public boolean callsMethod(String name) {
        if (type == BlockType.CALL_METHOD && name.equals(method)) return true;
        for (Block c : children) if (c.callsMethod(name)) return true;
        return false;
    }

    public String describe() {
        return switch (type) {
            case MOVE_SERVO  -> "Mover " + servo + " a " + Math.round(value) + "°";
            case WAIT        -> "Esperar " + value + " s";
            case REPEAT      -> "Repetir " + Math.round(value) + " veces";
            case CALL_METHOD -> "Método " + (method == null ? "…" : method);
            case POSE -> "Pose (" + getPose().size() + " servos)";
            default          -> type.getLabel();
        };
    }
}