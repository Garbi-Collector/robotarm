package garbi.com.robotarm.blocks.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import java.util.ArrayList;
import garbi.com.robotarm.blocks.model.Block;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

import java.util.List;

/** Columna de bloques que dibuja una lista del modelo. Puede ser la raíz o el interior de un contenedor. */
public class BlockStack extends VBox {

    private static final String NESTED_STYLE =
            "-fx-background-color: rgba(0,0,0,0.14); -fx-background-radius: 5;";
    private static final String NESTED_HOVER =
            "-fx-background-color: rgba(255,255,255,0.40); -fx-background-radius: 5;";

    private final List<Block> model;
    private final BlockContext ctx;
    private final boolean nested;

    public BlockStack(List<Block> model, BlockContext ctx, boolean nested) {
        this.model = model;
        this.ctx = ctx;
        this.nested = nested;
        setSpacing(4);
        if (nested) {
            setPadding(new Insets(6));
            setMinHeight(34);          // hueco visible aunque esté vacío
            setStyle(NESTED_STYLE);
        } else {
            setPadding(new Insets(8));
            setMinHeight(300);
        }
        refresh();
    }

    public List<Block> getModel() { return model; }
    public boolean isNested() { return nested; }

    /** Resalta el stack interior cuando es el destino de una suelta. */
    public void setHighlighted(boolean on) {
        if (nested) setStyle(on ? NESTED_HOVER : NESTED_STYLE);
    }

    /** Vuelve a dibujar todo (incluidos los anidados) a partir del modelo. */
    public void refresh() {
        getChildren().clear();
        for (int i = 0; i < model.size(); i++) {
            BlockNode node = new BlockNode(model.get(i), ctx);
            ctx.getDecorator().accept(node);
            attachContextMenu(node, i);
            getChildren().add(node);
        }
    }

    /** Clic derecho: guardar este bloque y los siguientes de su lista como método. */
    private void attachContextMenu(BlockNode node, int index) {
        node.setOnContextMenuRequested(e -> {
            e.consume(); // solo responde el bloque más interno
            if (ctx.getOnSaveMethod() == null) return;

            MenuItem save = new MenuItem("Guardar como método (este bloque y los siguientes)…");
            save.setOnAction(a -> ctx.getOnSaveMethod()
                    .accept(new ArrayList<>(model.subList(index, model.size()))));
            new ContextMenu(save).show(node, e.getScreenX(), e.getScreenY());
        });
    }
}