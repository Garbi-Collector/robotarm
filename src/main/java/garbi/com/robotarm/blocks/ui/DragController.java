package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.BlockType;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Arrastre de bloques entre la paleta y un árbol de stacks (raíz + interiores de contenedores).
 * Trabaja en coordenadas de escena.
 */
public class DragController {

    private final Pane dragLayer;
    private final Node dropArea;
    private final BlockStack root;
    private final BlockContext ctx;
    private final Region indicator = new Region();

    // estado del arrastre en curso
    private boolean armed;
    private BlockNode ghost;
    private BlockNode source;          // null si viene de la paleta
    private BlockStack highlighted;
    private double grabX, grabY;

    public DragController(Pane dragLayer, Node dropArea, BlockStack root, BlockContext ctx) {
        this.dragLayer = dragLayer;
        this.dropArea = dropArea;
        this.root = root;
        this.ctx = ctx;

        indicator.setStyle("-fx-background-color: #1e90ff; -fx-background-radius: 2;");
        indicator.setMouseTransparent(true);

        ctx.setDecorator(node -> attach(node, node::getBlock, true));
        root.refresh();
    }

    public void makePaletteItem(BlockNode node, BlockType type) {
        attach(node, () -> BlockFactory.create(type), false);
    }

    // ---------- eventos ----------

    private void attach(BlockNode node, Supplier<Block> blockForDrag, boolean fromStack) {
        // Los eventos suben por el árbol: se consumen para que solo actúe el bloque más interno.
        node.setOnMousePressed(e -> {
            armed = e.isPrimaryButtonDown()
                    && !ctx.isLocked()
                    && !isInteractive(e.getTarget(), node);
            grabX = e.getX();
            grabY = e.getY();
            e.consume();
        });

        node.setOnMouseDragged(e -> {
            e.consume();
            if (!armed) return;
            if (ghost == null) startDrag(node, blockForDrag.get(), fromStack);
            updateDrag(e.getSceneX(), e.getSceneY());
        });

        node.setOnMouseReleased(e -> {
            e.consume();
            if (!armed) return;
            armed = false;
            if (ghost != null) finishDrag(e.getSceneX(), e.getSceneY());
        });
    }

    private void startDrag(BlockNode node, Block block, boolean fromStack) {
        ghost = new BlockNode(block); // solo lectura: un contenedor se ve solo con su cabecera
        ghost.setPrefWidth(Math.min(node.getWidth(), 360));
        ghost.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.4)));
        ghost.setMouseTransparent(true);

        source = fromStack ? node : null;
        if (source != null) source.setOpacity(0.3);

        dragLayer.getChildren().addAll(indicator, ghost);
        indicator.setVisible(false);
    }

    private void updateDrag(double sceneX, double sceneY) {
        Point2D p = dragLayer.sceneToLocal(sceneX - grabX, sceneY - grabY);
        ghost.relocate(p.getX(), p.getY());

        BlockStack target = targetStack(sceneX, sceneY);
        // un bloque del área de trabajo soltado fuera se elimina: se ve más pálido
        ghost.setOpacity(target != null || source == null ? 0.92 : 0.45);

        highlight(target);
        if (target != null) showIndicator(target, insertionIndex(target, sceneY));
        else indicator.setVisible(false);
    }

    private void finishDrag(double sceneX, double sceneY) {
        BlockStack target = targetStack(sceneX, sceneY);
        int index = target == null ? 0 : insertionIndex(target, sceneY);
        Block block = ghost.getBlock();

        if (source != null) removeFromTree(root.getModel(), block);
        if (target != null) {
            List<Block> list = target.getModel();
            list.add(Math.min(index, list.size()), block);
        }

        dragLayer.getChildren().removeAll(ghost, indicator);
        highlight(null);
        ghost = null;
        source = null;
        root.refresh();
    }

    /** Quita el bloque de la lista donde esté, a cualquier profundidad. */
    private static boolean removeFromTree(List<Block> list, Block target) {
        if (list.remove(target)) return true; // Block no redefine equals: compara identidad
        for (Block b : list) {
            if (removeFromTree(b.getChildren(), target)) return true;
        }
        return false;
    }

    // ---------- destino ----------

    /** Stack más profundo bajo el cursor; null si el cursor está fuera del área de trabajo. */
    private BlockStack targetStack(double sceneX, double sceneY) {
        if (!sceneBounds(dropArea).contains(sceneX, sceneY)) return null;

        BlockStack best = root;
        List<BlockStack> candidates = new ArrayList<>();
        collect(root, candidates);
        // orden "padre antes que hijos": el último que contiene el punto es el más profundo
        for (BlockStack s : candidates) {
            if (sceneBounds(s).contains(sceneX, sceneY)) best = s;
        }
        return best;
    }

    /** Stacks válidos como destino: se salta el interior del bloque que se está arrastrando. */
    private void collect(BlockStack stack, List<BlockStack> out) {
        out.add(stack);
        for (Node n : stack.getChildren()) {
            if (n == source) continue; // no se puede soltar un contenedor dentro de sí mismo
            BlockStack inner = ((BlockNode) n).getInnerStack();
            if (inner != null) collect(inner, out);
        }
    }

    private void highlight(BlockStack target) {
        if (highlighted == target) return;
        if (highlighted != null) highlighted.setHighlighted(false);
        highlighted = target;
        if (highlighted != null) highlighted.setHighlighted(true);
    }

    // ---------- geometría ----------

    private List<BlockNode> others(BlockStack stack) {
        List<BlockNode> list = new ArrayList<>();
        for (Node n : stack.getChildren()) {
            if (n != source) list.add((BlockNode) n);
        }
        return list;
    }

    private static Bounds sceneBounds(Node n) {
        return n.localToScene(n.getBoundsInLocal());
    }

    private int insertionIndex(BlockStack stack, double sceneY) {
        int index = 0;
        for (BlockNode n : others(stack)) {
            Bounds b = sceneBounds(n);
            if (sceneY > b.getMinY() + b.getHeight() / 2) index++;
            else break;
        }
        return index;
    }

    private void showIndicator(BlockStack stack, int index) {
        List<BlockNode> others = others(stack);
        Bounds sb = sceneBounds(stack);
        Insets pad = stack.getPadding();
        double gap = stack.getSpacing() / 2;

        double y;
        if (others.isEmpty()) y = sb.getMinY() + pad.getTop();
        else if (index < others.size()) y = sceneBounds(others.get(index)).getMinY() - gap;
        else y = sceneBounds(others.get(others.size() - 1)).getMaxY() + gap;

        double x = sb.getMinX() + pad.getLeft();
        double width = sb.getWidth() - pad.getLeft() - pad.getRight();

        Point2D p = dragLayer.sceneToLocal(x, y);
        indicator.setPrefSize(width, 4);
        indicator.relocate(p.getX(), p.getY() - 2);
        indicator.setVisible(true);
    }

    /** ¿El clic cayó sobre un campo de texto, combo o botón dentro del bloque? */
    private static boolean isInteractive(Object target, Node limit) {
        Node n = (target instanceof Node) ? (Node) target : null;
        while (n != null && n != limit) {
            if (n instanceof TextInputControl || n instanceof ComboBoxBase<?> || n instanceof ButtonBase) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }
}