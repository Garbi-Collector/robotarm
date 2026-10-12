package garbi.com.robotarm.blocks.model;

public enum BlockType {
    MOVE_SERVO("Mover servo", false),
    OPEN_GRIPPER("Abrir garra", false),
    CLOSE_GRIPPER("Cerrar garra", false),
    WAIT("Esperar", false),
    HOME("Posición inicial", false),
    REPEAT("Repetir", true);

    private final String label;
    private final boolean container;

    BlockType(String label, boolean container) {
        this.label = label;
        this.container = container;
    }

    public String getLabel() { return label; }
    public boolean isContainer() { return container; }
}