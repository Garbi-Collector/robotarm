package garbi.com.robotarm.blocks.ui;

import garbi.com.robotarm.blocks.model.Block;

/** Ilumina en el editor el bloque que se está ejecutando. Usar solo desde el hilo de JavaFX. */
public class ExecutionHighlighter {

    private final BlockStack root;
    private BlockNode current;

    public ExecutionHighlighter(BlockStack root) { this.root = root; }

    public void show(Block block) {
        BlockNode node = root.findNode(block);
        if (node == null) return; // está dentro de un método: no se dibuja, queda el «Método» iluminado
        clear();
        current = node;
        node.setActive(true);
    }

    public void clear() {
        if (current != null) current.setActive(false);
        current = null;
    }
}