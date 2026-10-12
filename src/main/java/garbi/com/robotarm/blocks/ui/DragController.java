package garbi.com.robotarm.blocks.ui;

import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.BlockType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Maneja el arrastre de bloques (desde la paleta o desde el área de trabajo).
 * Trabaja en coordenadas de escena, así no depende de cómo esté armado el layout.
 */
public class DragController {

    private final Pane dragLayer;
    private final Node dropArea;
    private final BlockStack stack;
    private final BooleanSupplier locked;
    private final Region indicator = new Region();

    // estado del arrastre en curso
    private boolean armed;
    private BlockNode ghost;
    private BlockNode source; // null si el bloque viene de la paleta
    private double grabX, grabY;

    public DragController(Pane dragLayer, Node dropArea, BlockStack stack, BooleanSupplier locked) {
        this.dragLayer = dragLayer;
        this.dropArea = dropArea;
        this.stack = stack;
        this.locked = locked;

        indicator.setStyle("-fx-background-color: #1e90ff; -fx-background-radius: 2;");
        indicator.setMouseTransparent(true);

        stack.setNodeDecorator(node -> attach(node, node::getBlock, true));
        stack.refresh();
    }

    public void makePaletteItem(BlockNode node, BlockType type) {
        attach(node, () -> BlockFactory.create(type), false);
    }

    // ---------- eventos ----------

    private void attach(BlockNode node, Supplier<Block> blockForDrag, boolean fromStack) {
        node.setOnMousePressed(e -> {
            armed = e.isPrimaryButtonDown()
                    && !locked.getAsBoolean()
                    && !isInteractive(e.getTarget(), node);   // clic en un campo = no arrastrar
            grabX = e.getX();
            grabY = e.getY();
        });

        node.setOnMouseDragged(e -> {
            if (!armed) return;
            if (ghost == null) startDrag(node, blockForDrag.get(), fromStack);
            updateDrag(e.getSceneX(), e.getSceneY());
        });

        node.setOnMouseReleased(e -> {
            if (!armed) return;
            armed = false;
            if (ghost != null) finishDrag(e.getSceneX(), e.getSceneY());
        });
    }

    private void startDrag(BlockNode node, Block block, boolean fromStack) {
        ghost = new BlockNode(block);
        ghost.setPrefWidth(node.getWidth());
        ghost.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.4)));
        ghost.setMouseTransparent(true);

        source = fromStack ? node : null;
        if (source != null) source.setOpacity(0.3); // queda como "hueco" hasta soltar

        dragLayer.getChildren().addAll(indicator, ghost);
        indicator.setVisible(false);
    }

    private void updateDrag(double sceneX, double sceneY) {
        Point2D p = dragLayer.sceneToLocal(sceneX - grabX, sceneY - grabY);
        ghost.relocate(p.getX(), p.getY());

        boolean over = isOverDropArea(sceneX, sceneY);
        // un bloque del área de trabajo soltado fuera se elimina: se ve más pálido
        ghost.setOpacity(over || source == null ? 0.92 : 0.45);

        if (over) showIndicator(insertionIndex(sceneY));
        else indicator.setVisible(false);
    }

    private void finishDrag(double sceneX, double sceneY) {
        boolean over = isOverDropArea(sceneX, sceneY);
        int index = insertionIndex(sceneY); // calculado sin contar el bloque arrastrado
        Block block = ghost.getBlock();
        List<Block> model = stack.getModel();

        if (source != null) model.remove(block);
        if (over) model.add(Math.min(index, model.size()), block);

        dragLayer.getChildren().removeAll(ghost, indicator);
        ghost = null;
        source = null;
        stack.refresh();
    }

    // ---------- geometría ----------

    private boolean isOverDropArea(double sceneX, double sceneY) {
        return dropArea.localToScene(dropArea.getBoundsInLocal()).contains(sceneX, sceneY);
    }

    /** Bloques del stack, sin el que se está arrastrando. */
    private List<BlockNode> others() {
        List<BlockNode> list = new ArrayList<>();
        for (Node n : stack.getChildren()) {
            if (n != source) list.add((BlockNode) n);
        }
        return list;
    }

    private static Bounds sceneBounds(Node n) {
        return n.localToScene(n.getBoundsInLocal());
    }

    /** Posición (0..n) donde se insertaría el bloque según la altura del cursor. */
    private int insertionIndex(double sceneY) {
        int index = 0;
        for (BlockNode n : others()) {
            Bounds b = sceneBounds(n);
            if (sceneY > b.getMinY() + b.getHeight() / 2) index++;
            else break;
        }
        return index;
    }

    private void showIndicator(int index) {
        List<BlockNode> others = others();
        Bounds sb = sceneBounds(stack);
        double gap = stack.getSpacing() / 2;

        double y;
        if (others.isEmpty()) y = sb.getMinY() + stack.getPadding().getTop();
        else if (index < others.size()) y = sceneBounds(others.get(index)).getMinY() - gap;
        else y = sceneBounds(others.get(others.size() - 1)).getMaxY() + gap;

        double x = sb.getMinX() + stack.getPadding().getLeft();
        double width = Math.min(BlockNode.MAX_WIDTH,
                sb.getWidth() - stack.getPadding().getLeft() - stack.getPadding().getRight());

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