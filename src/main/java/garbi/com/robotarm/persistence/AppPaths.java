package garbi.com.robotarm.persistence;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Carpetas por defecto de la aplicación y memoria de la última carpeta usada. */
public final class AppPaths {

    private static final String APP_FOLDER = "RobotArm";
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPaths.class);

    private AppPaths() {}

    /** Documentos\RobotArm (respeta Documentos redirigido a OneDrive). */
    public static Path root() {
        // getDefaultDirectory() devuelve la carpeta "Documentos" real en Windows
        File docs = FileSystemView.getFileSystemView().getDefaultDirectory();
        Path base = (docs != null && docs.isDirectory())
                ? docs.toPath() : Path.of(System.getProperty("user.home"));
        return base.resolve(APP_FOLDER);
    }

    public static Path projectsDir() { return root().resolve("Proyectos"); }
    public static Path methodsDir()  { return root().resolve("Métodos"); }

    /** Crea las carpetas si no existen. Si no se puede, la app sigue funcionando. */
    public static void ensureFolders() {
        try {
            Files.createDirectories(projectsDir());
            Files.createDirectories(methodsDir());
        } catch (IOException ignored) {
            // sin permisos: los diálogos usarán la carpeta por defecto del sistema
        }
    }

    /** Carpeta inicial para un diálogo: la última usada, o la de por defecto. */
    public static File startDir(String kind, Path fallback) {
        File last = new File(PREFS.get("last." + kind, ""));
        if (last.isDirectory()) return last;
        return Files.isDirectory(fallback) ? fallback.toFile() : null;
    }

    public static void remember(String kind, Path file) {
        Path dir = (file != null) ? file.getParent() : null;
        if (dir != null) PREFS.put("last." + kind, dir.toString());
    }
}