package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Program;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Área de trabajo: cabecera "Al iniciar" y la columna de bloques. */
public class Workspace extends VBox {

    private final BlockStack stack;

    public Workspace(Program program, BlockContext ctx) {
        Label hat = new Label("▶  Al iniciar");
        hat.setPadding(new Insets(8, 14, 8, 14));
        hat.setStyle("-fx-background-color: #3b4048; -fx-text-fill: white;"
                + "-fx-font-weight: bold; -fx-background-radius: 14 14 7 7;");
        hat.setMaxWidth(BlockNode.MAX_WIDTH);

        stack = new BlockStack(program.getBlocks(), ctx, false);
        VBox.setVgrow(stack, Priority.ALWAYS);

        setPadding(new Insets(12));
        setSpacing(2);
        setMinWidth(300);
        setStyle("-fx-background-color: #fafafa;");
        getChildren().addAll(hat, stack);
        VBox.setMargin(hat, new Insets(0, 0, 0, 8));
    }

    public BlockStack getStack() { return stack; }
}