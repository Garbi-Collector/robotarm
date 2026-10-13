package garbi.com.robotarm.blocks.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** El proyecto: bloques principales + biblioteca de métodos. */
public class Program {

    private String name;
    private List<Block> blocks = new ArrayList<>();
    private List<MethodDef> methods = new ArrayList<>();

    public Program() { this("Sin título"); }
    public Program(String name) { this.name = name; }

    public String getName() { return name; }
    public List<Block> getBlocks() { return blocks; }
    public List<MethodDef> getMethods() { return methods; }

    public MethodDef findMethod(String name) {
        if (name == null) return null;
        for (MethodDef m : methods) if (name.equals(m.getName())) return m;
        return null;
    }

    public List<String> methodNames() {
        List<String> names = new ArrayList<>();
        for (MethodDef m : methods) names.add(m.getName());
        return names;
    }

    /** Agrega el méto do; si ya había uno con ese nombre, lo reemplaza. */
    public void putMethod(MethodDef method) {
        methods.removeIf(m -> m.getName().equals(method.getName()));
        methods.add(method);
        methods.sort(Comparator.comparing(MethodDef::getName, String.CASE_INSENSITIVE_ORDER));
    }

    public boolean removeMethod(String name) {
        return methods.removeIf(m -> m.getName().equals(name));
    }

    public boolean isMethodUsed(String name) {
        for (Block b : blocks) if (b.callsMethod(name)) return true;
        return false;
    }

    /** Reemplaza el contenido EN SITIO, para que quienes referencian este objeto vean el cambio. */
    public void loadFrom(Program other) {
        name = other.name;
        blocks.clear();
        blocks.addAll(other.blocks);
        methods.clear();
        methods.addAll(other.methods);
    }
}