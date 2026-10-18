package garbi.com.robotarm.update;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.function.DoubleConsumer;

/** Busca, descarga e instala nuevas versiones. Los métodos de red bloquean: no llamar desde el hilo de JavaFX. */
public final class Updater {

    public record Release(String version, String url, String sha256, String notes) {}

    /** CAMBIA ESTO por la URL de tu version.json. Debe ser https. */
    private static final String MANIFEST_URL =
            "https://github.com/Garbi-Collector/robotarm/releases/latest/download/version.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Updater() {}

    public static String currentVersion() {
        try (InputStream in = Updater.class.getResourceAsStream("/version.properties")) {
            if (in == null) return "dev";
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version", "dev").trim();
            return v.startsWith("$") ? "dev" : v;
        } catch (IOException e) {
            return "dev";
        }
    }

    /** Solo la app instalada (RobotArm.exe en Windows) puede actualizarse sola, no desde el IDE. */
    public static boolean canSelfUpdate() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                && ProcessHandle.current().info().command()
                .map(c -> c.toLowerCase().endsWith("robotarm.exe")).orElse(false);
    }

    /** @return la versión nueva, o null si ya tienes la última. */
    public static Release check() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(MANIFEST_URL))
                .timeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> res = HTTP.send(req, BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("El servidor respondió " + res.statusCode());
        }

        Release r;
        try {
            r = new Gson().fromJson(res.body(), Release.class);
        } catch (JsonParseException e) {
            throw new IOException("La información de versión no es válida.", e);
        }
        if (r == null || r.version() == null || r.url() == null || r.sha256() == null
                || !r.url().startsWith("https://")) {
            throw new IOException("La información de versión está incompleta.");
        }
        return isNewer(r.version(), currentVersion()) ? r : null;
    }

    /** Descarga el instalador y verifica su hash. Devuelve el archivo temporal. */
    public static Path download(Release r, DoubleConsumer progress)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(r.url()))
                .timeout(Duration.ofMinutes(10)).build();
        HttpResponse<InputStream> res = HTTP.send(req, BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("La descarga falló (" + res.statusCode() + ").");
        }

        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        Path file = Files.createTempFile("RobotArm-update-", ".msi");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }

        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                done += n;
                if (total > 0) progress.accept((double) done / total);
            }
        } catch (IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }

        String actual = HexFormat.of().formatHex(md.digest());
        if (!actual.equalsIgnoreCase(r.sha256().trim())) {
            Files.deleteIfExists(file);
            throw new IOException("El archivo descargado no coincide con el hash esperado. No se instaló.");
        }
        return file;
    }

    /**
     * Lanza un script que instala el .msi y vuelve a abrir la app. Después, el llamador debe cerrar la app.
     */
    public static void installAndRestart(Path msi) throws IOException {
        Path exe = Path.of(ProcessHandle.current().info().command()
                .orElseThrow(() -> new IOException("No se encontró el ejecutable de la app.")));
        Path script = Files.createTempFile("robotarm-update-", ".cmd");

        String text = "@echo off\r\n"
                + "ping 127.0.0.1 -n 3 >nul\r\n"                       // da tiempo a que la app se cierre
                + "msiexec /i \"" + msi + "\" /passive /norestart\r\n"
                + "start \"\" \"" + exe + "\"\r\n"
                + "del \"" + msi + "\"\r\n";
        Charset console = Charset.forName(System.getProperty("native.encoding", "Cp1252"));
        Files.writeString(script, text, console);

        new ProcessBuilder("cmd.exe", "/c", script.toString()).start();
    }

    // ---------- versiones ----------

    static boolean isNewer(String candidate, String current) {
        int[] a = parts(candidate), b = parts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parts(String v) {
        return java.util.Arrays.stream(v.split("[.\\-]"))
                .filter(s -> s.matches("\\d+"))
                .mapToInt(Integer::parseInt).toArray();
    }
}