package garbi.com.robotarm.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.MethodDef;
import garbi.com.robotarm.blocks.model.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Lee y escribe proyectos (.raa) y métodos (.ram). Ambos son JSON con una cabecera de formato. */
public final class ProjectStorage {

    public static final String PROJECT_EXT = ".raa";
    public static final String METHOD_EXT = ".ram";

    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();

    static final class ProjectFile {
        String format = "RAA";
        int version = VERSION;
        Program program;
    }

    static final class MethodFile {
        String format = "RAM";
        int version = VERSION;
        MethodDef method;
    }

    private ProjectStorage() {}

    /** Texto que representa el estado actual; sirve para detectar cambios sin guardar. */
    public static String snapshot(Program program) {
        return COMPACT.toJson(program);
    }

    // ---------- proyecto ----------

    public static void saveProject(Program program, Path file) throws IOException {
        ProjectFile f = new ProjectFile();
        f.program = program;
        write(file, GSON.toJson(f));
    }

    public static Program loadProject(Path file) throws IOException {
        ProjectFile f = read(file, ProjectFile.class);
        check(f == null ? null : f.format, f == null ? 0 : f.version, "RAA");
        if (f.program == null) throw new IOException("El archivo no contiene un programa.");
        validate(f.program.getBlocks());
        for (MethodDef m : f.program.getMethods()) validate(m.getBlocks());
        return f.program;
    }

    // ---------- méto do ----------

    public static void saveMethod(MethodDef method, Path file) throws IOException {
        MethodFile f = new MethodFile();
        f.method = method;
        write(file, GSON.toJson(f));
    }

    public static MethodDef loadMethod(Path file) throws IOException {
        MethodFile f = read(file, MethodFile.class);
        check(f == null ? null : f.format, f == null ? 0 : f.version, "RAM");
        if (f.method == null || f.method.getName() == null || f.method.getName().isBlank()) {
            throw new IOException("El archivo no contiene un método válido.");
        }
        validate(f.method.getBlocks());
        return f.method;
    }

    // ---------- interno ----------

    /** Escribe en un temporal y luego reemplaza, para no dejar un archivo a medias si algo falla. */
    private static void write(Path file, String json) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static <T> T read(Path file, Class<T> type) throws IOException {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (JsonParseException e) {
            throw new IOException("El archivo está dañado o no es válido.", e);
        }
    }

    private static void check(String format, int version, String expected) throws IOException {
        if (!expected.equals(format)) {
            throw new IOException("No es un archivo " + (expected.equals("RAA") ? "de proyecto" : "de método")
                    + " de Robot Arm Programmer.");
        }
        if (version > VERSION) {
            throw new IOException("Fue creado con una versión más nueva de la aplicación.");
        }
    }

    private static void validate(List<Block> blocks) throws IOException {
        for (Block b : blocks) {
            if (b == null || b.getType() == null) {
                throw new IOException("Contiene un bloque desconocido (¿versión más nueva?).");
            }
            validate(b.getChildren());
        }
    }
}