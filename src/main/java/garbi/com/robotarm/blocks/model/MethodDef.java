package garbi.com.robotarm.blocks.model;

import java.util.ArrayList;
import java.util.List;

/** Un méto do del usuario: un nombre y una secuencia de bloques. */
public class MethodDef {

    private String name;
    private List<Block> blocks = new ArrayList<>();

    /** Lo usa Gson al leer archivos. */
    private MethodDef() {}

    /** Guarda una copia profunda de los bloques. */
    public MethodDef(String name, List<Block> source) {
        this.name = name;
        for (Block b : source) blocks.add(b.copy());
    }

    public String getName() { return name; }
    public List<Block> getBlocks() { return blocks; }

    public boolean usesMethods() {
        return blocks.stream().anyMatch(Block::usesMethods);
    }
}