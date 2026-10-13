package garbi.com.robotarm.ui;

import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.persistence.ProjectStorage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Abrir, guardar y controlar los cambios sin guardar del proyecto actual. */
public class ProjectManager {

    private final Stage stage;
    private final Program program;
    private final Runnable refreshUi;

    private Path file;              // null = proyecto nuevo, aún sin archivo
    private String savedSnapshot;
    private File lastDir;

    public ProjectManager(Stage stage, Program program, Runnable refreshUi) {
        this.stage = stage;
        this.program = program;
        this.refreshUi = refreshUi;
        this.savedSnapshot = ProjectStorage.snapshot(program);
    }

    public String displayName() {
        return file == null ? "Sin título" : file.getFileName().toString();
    }

    public boolean isDirty() {
        return !ProjectStorage.snapshot(program).equals(savedSnapshot);
    }

    /** Proyecto vacío. No crea ningún archivo hasta que se guarde. */
    public void newProject() {
        program.loadFrom(new Program());
        file = null;
        savedSnapshot = ProjectStorage.snapshot(program);
        refreshUi.run();
    }

    /** Elige un archivo y lo abre (preguntando antes si hay cambios sin guardar). */
    public boolean open() {
        File f = chooser("Abrir proyecto").showOpenDialog(stage);
        if (f == null) return false;
        if (!confirmDiscard()) return false;
        return openPath(f.toPath());
    }

    public boolean openPath(Path path) {
        try {
            Program loaded = ProjectStorage.loadProject(path);
            program.loadFrom(loaded);
            file = path;
            savedSnapshot = ProjectStorage.snapshot(program);
            remember(path);
            refreshUi.run();
            return true;
        } catch (IOException e) {
            Dialogs.error(stage, "No se pudo abrir el proyecto:\n" + e.getMessage());
            return false;
        }
    }

    public boolean save() {
        return file == null ? saveAs() : writeTo(file);
    }

    public boolean saveAs() {
        FileChooser fc = chooser("Guardar proyecto como");
        fc.setInitialFileName(file != null ? file.getFileName().toString() : "mi-proyecto.raa");
        File f = fc.showSaveDialog(stage);
        if (f == null) return false;

        Path path = f.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(ProjectStorage.PROJECT_EXT)) {
            path = path.resolveSibling(path.getFileName() + ProjectStorage.PROJECT_EXT);
        }
        return writeTo(path);
    }

    /**
     * Si hay cambios sin guardar, pregunta qué hacer.
     * @return true si se puede continuar (guardó o descartó), false si canceló.
     */
    public boolean confirmDiscard() {
        if (!isDirty()) return true;

        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("No guardar", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(stage);
        a.setTitle("Cambios sin guardar");
        a.setHeaderText("«" + displayName() + "» tiene cambios sin guardar");
        a.setContentText("¿Quieres guardarlos antes de continuar?");
        a.getButtonTypes().setAll(save, discard, cancel);

        ButtonType result = a.showAndWait().orElse(cancel);
        if (result == save) return save();   // si cancela el "Guardar como", no se continúa
        return result == discard;
    }

    // ---------- interno ----------

    private boolean writeTo(Path path) {
        try {
            ProjectStorage.saveProject(program, path);
            file = path;
            savedSnapshot = ProjectStorage.snapshot(program);
            remember(path);
            return true;
        } catch (IOException e) {
            Dialogs.error(stage, "No se pudo guardar el proyecto:\n" + e.getMessage());
            return false;
        }
    }

    private FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Proyecto Robot Arm (*.raa)", "*.raa"));
        if (lastDir != null && lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
        return fc;
    }

    private void remember(Path path) {
        if (path.getParent() != null) lastDir = path.getParent().toFile();
    }
}