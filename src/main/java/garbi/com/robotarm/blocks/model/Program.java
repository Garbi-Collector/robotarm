package garbi.com.robotarm.blocks.model;

import java.util.ArrayList;
import java.util.List;

public class Program {

    private String name;
    private List<Block> blocks = new ArrayList<>();

    public Program(String name) { this.name = name; }

    public String getName() { return name; }
    public List<Block> getBlocks() { return blocks; }
}