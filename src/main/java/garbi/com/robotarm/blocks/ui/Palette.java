package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.BlockType;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Lista de bloques disponibles para arrastrar. */
public class Palette extends VBox {

    public Palette(DragController drag) {
        setSpacing(8);
        setPadding(new Insets(12));
        setPrefWidth(210);
        setMinWidth(210);
        setStyle("-fx-background-color: #e3e5e8;");

        Label title = new Label("Bloques");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        getChildren().add(title);

        for (BlockType type : BlockType.values()) {
            if (type.isContainer()) continue; // "Repetir" llega en el paso 4
            BlockNode item = new BlockNode(BlockFactory.create(type));
            drag.makePaletteItem(item, type);
            getChildren().add(item);
        }
    }
}