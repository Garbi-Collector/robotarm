package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.model.RobotArm;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Columna de bloques que dibuja una lista del modelo. */
public class BlockStack extends VBox {

    private final List<Block> model;
    private final RobotArm arm;
    private final ReadOnlyBooleanProperty locked;
    private Consumer<BlockNode> decorator = n -> {};

    public BlockStack(List<Block> model, RobotArm arm, ReadOnlyBooleanProperty locked) {
        this.model = model;
        this.arm = arm;
        this.locked = locked;
        setSpacing(4);
        setPadding(new Insets(8));
        setMinHeight(300);
        refresh();
    }

    public List<Block> getModel() { return model; }

    /** Permite engancharle comportamiento (arrastre) a cada bloque dibujado. */
    public void setNodeDecorator(Consumer<BlockNode> decorator) {
        this.decorator = decorator;
    }

    /** Vuelve a dibujar todo a partir del modelo. */
    public void refresh() {
        getChildren().clear();
        for (Block block : model) {
            BlockNode node = new BlockNode(block, arm, locked);
            decorator.accept(node);
            getChildren().add(node);
        }
    }
}