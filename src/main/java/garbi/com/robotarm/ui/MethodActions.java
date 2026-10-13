package garbi.com.robotarm.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.MethodDef;
import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.persistence.ProjectStorage;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Crear, importar, exportar y eliminar métodos. */
public class MethodActions {

    private final Stage stage;
    private final Program program;
    private final Runnable refreshUi;
    private File lastDir;

    public MethodActions(Stage stage, Program program, Runnable refreshUi) {
        this.stage = stage;
        this.program = program;
        this.refreshUi = refreshUi;
    }

    /** Crea un método a partir de estos bloques, lo añade al proyecto y ofrece guardarlo como .ram. */
    public void saveFromBlocks(List<Block> blocks) {
        if (blocks.stream().anyMatch(Block::usesMethods)) {
            Dialogs.error(stage, "Un método no puede contener otros métodos (por ahora).");
            return;
        }

        TextInputDialog dlg = new TextInputDialog();
        dlg.initOwner(stage);
        dlg.setTitle("Nuevo método");
        dlg.setHeaderText("Guardar " + blocks.size() + " bloque(s) como método");
        dlg.setContentText("Nombre:");
        Optional<String> result = dlg.showAndWait();
        if (result.isEmpty()) return;

        String name = result.get().trim();
        if (name.isEmpty() || name.length() > 40) {
            Dialogs.error(stage, "El nombre debe tener entre 1 y 40 caracteres.");
            return;
        }
        if (program.findMethod(name) != null
                && !Dialogs.confirm(stage, "Ya existe un método llamado «" + name + "». ¿Reemplazarlo?")) {
            return;
        }

        MethodDef def = new MethodDef(name, blocks);
        program.putMethod(def);
        refreshUi.run();
        exportToFile(def);
    }

    public void importMethods() {
        List<File> files = chooser("Importar método").showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        int imported = 0;
        for (File f : files) {
            try {
                MethodDef m = ProjectStorage.loadMethod(f.toPath());
                if (m.usesMethods()) throw new IOException("Los métodos no pueden contener otros métodos.");
                if (program.findMethod(m.getName()) != null && !Dialogs.confirm(stage,
                        "Ya existe un método llamado «" + m.getName() + "». ¿Reemplazarlo?")) {
                    continue;
                }
                program.putMethod(m);
                imported++;
            } catch (IOException e) {
                Dialogs.error(stage, "No se pudo importar " + f.getName() + ":\n" + e.getMessage());
            }
        }
        if (imported > 0) {
            refreshUi.run();
            Dialogs.info(stage, imported + " método(s) importado(s).");
        }
    }

    public void exportMethod() {
        pick("Exportar método").map(program::findMethod).ifPresent(this::exportToFile);
    }

    public void deleteMethod() {
        Optional<String> name = pick("Eliminar método");
        if (name.isEmpty()) return;
        if (program.isMethodUsed(name.get())) {
            Dialogs.error(stage, "«" + name.get() + "» se está usando en el programa. Quita esos bloques primero.");
            return;
        }
        program.removeMethod(name.get());
        refreshUi.run();
    }

    // ---------- interno ----------

    private void exportToFile(MethodDef def) {
        FileChooser fc = chooser("Guardar método como archivo (.ram)");
        fc.setInitialFileName(def.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ProjectStorage.METHOD_EXT);
        File f = fc.showSaveDialog(stage);
        if (f == null) {
            Dialogs.info(stage, "El método quedó guardado en este proyecto.\n"
                    + "Puedes exportarlo después desde Métodos → Exportar.");
            return;
        }

        Path path = f.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(ProjectStorage.METHOD_EXT)) {
            path = path.resolveSibling(path.getFileName() + ProjectStorage.METHOD_EXT);
        }
        try {
            ProjectStorage.saveMethod(def, path);
            if (path.getParent() != null) lastDir = path.getParent().toFile();
            Dialogs.info(stage, "Método guardado en:\n" + path);
        } catch (IOException e) {
            Dialogs.error(stage, "No se pudo guardar el método:\n" + e.getMessage());
        }
    }

    private Optional<String> pick(String title) {
        List<String> names = program.methodNames();
        if (names.isEmpty()) {
            Dialogs.info(stage, "Este proyecto todavía no tiene métodos.");
            return Optional.empty();
        }
        ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
        dlg.initOwner(stage);
        dlg.setTitle(title);
        dlg.setHeaderText(title);
        dlg.setContentText("Método:");
        return dlg.showAndWait();
    }

    private FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Método Robot Arm (*.ram)", "*.ram"));
        if (lastDir != null && lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
        return fc;
    }
}