package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.BlockType;
import garbi.com.robotarm.blocks.model.MethodDef;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
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
            setMinHeight(34);
            setStyle(NESTED_STYLE);
        } else {
            setPadding(new Insets(8));
            setMinHeight(300);
        }
        refresh();
    }

    public List<Block> getModel() { return model; }
    public boolean isNested() { return nested; }

    public void setHighlighted(boolean on) {
        if (nested) setStyle(on ? NESTED_HOVER : NESTED_STYLE);
    }

    /** Busca, a cualquier profundidad, el nodo que dibuja ese bloque. */
    public BlockNode findNode(Block target) {
        for (Node n : getChildren()) {
            BlockNode bn = (BlockNode) n;
            if (bn.getBlock() == target) return bn;
            BlockStack inner = bn.getInnerStack();
            if (inner != null) {
                BlockNode found = inner.findNode(target);
                if (found != null) return found;
            }
        }
        return null;
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

    private void attachContextMenu(BlockNode node, int index) {
        node.setOnContextMenuRequested(e -> {
            e.consume(); // solo responde el bloque más interno
            if (ctx.isLocked()) return;

            Block block = model.get(index);
            ContextMenu menu = new ContextMenu();

            if (block.getType() == BlockType.CALL_METHOD) {
                MethodDef def = ctx.getProgram().findMethod(block.getMethod());
                if (def != null) {
                    MenuItem expand = new MenuItem("Expandir método en bloques");
                    expand.setOnAction(a -> {
                        List<Block> copies = new ArrayList<>();
                        for (Block b : def.getBlocks()) copies.add(b.copy());
                        model.remove(index);
                        model.addAll(index, copies);
                        refresh();
                    });
                    menu.getItems().addAll(expand, new SeparatorMenuItem());
                }
            }

            MenuItem duplicate = new MenuItem("Duplicar");
            duplicate.setOnAction(a -> { model.add(index + 1, block.copy()); refresh(); });

            MenuItem delete = new MenuItem("Eliminar");
            delete.setOnAction(a -> { model.remove(index); refresh(); });

            menu.getItems().addAll(duplicate, delete);

            if (ctx.getOnSaveMethod() != null) {
                MenuItem save = new MenuItem("Guardar como método (este bloque y los siguientes)…");
                save.setOnAction(a -> ctx.getOnSaveMethod()
                        .accept(new ArrayList<>(model.subList(index, model.size()))));
                menu.getItems().addAll(new SeparatorMenuItem(), save);
            }

            menu.show(node, e.getScreenX(), e.getScreenY());
        });
    }
}