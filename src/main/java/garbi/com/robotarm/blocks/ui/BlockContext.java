package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.model.RobotArm;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.List;
import java.util.function.Consumer;

/** Datos compartidos por todos los bloques dibujados (incluidos los anidados). */
public class BlockContext {

    private final RobotArm arm;
    private final ReadOnlyBooleanProperty locked;
    private final Program program;
    private Consumer<BlockNode> decorator = n -> {};
    private Consumer<List<Block>> onSaveMethod;

    public BlockContext(RobotArm arm, ReadOnlyBooleanProperty locked, Program program) {
        this.arm = arm;
        this.locked = locked;
        this.program = program;
    }

    public RobotArm getArm() { return arm; }
    public ReadOnlyBooleanProperty getLocked() { return locked; }
    public boolean isLocked() { return locked.get(); }
    public Program getProgram() { return program; }

    public Consumer<BlockNode> getDecorator() { return decorator; }
    public void setDecorator(Consumer<BlockNode> decorator) { this.decorator = decorator; }

    /** Se invoca con los bloques que el usuario quiere guardar como método. */
    public Consumer<List<Block>> getOnSaveMethod() { return onSaveMethod; }
    public void setOnSaveMethod(Consumer<List<Block>> onSaveMethod) { this.onSaveMethod = onSaveMethod; }
}