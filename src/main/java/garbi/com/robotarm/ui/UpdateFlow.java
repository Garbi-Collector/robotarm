package garbi.com.robotarm.ui;

import garbi.com.robotarm.update.Updater;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Window;

import java.nio.file.Path;

/** Flujo de actualización: buscar, confirmar, descargar, instalar y reiniciar. */
public final class UpdateFlow {

    private UpdateFlow() {}

    public static void run(Window owner, Button button, Label status) {
        if (!Updater.canSelfUpdate()) {
            Dialogs.info(owner, "La actualización automática solo funciona en la versión instalada.");
            return;
        }

        button.setDisable(true);
        status.setText("Buscando actualizaciones…");

        Task<Updater.Release> check = new Task<>() {
            @Override protected Updater.Release call() throws Exception { return Updater.check(); }
        };
        check.setOnFailed(e -> fail(button, status, check.getException()));
        check.setOnSucceeded(e -> {
            Updater.Release r = check.getValue();
            if (r == null) {
                status.setText("Ya tienes la última versión (" + Updater.currentVersion() + ").");
                button.setDisable(false);
                return;
            }
            String notes = (r.notes() == null || r.notes().isBlank()) ? "" : "\n\n" + r.notes();
            boolean ok = Dialogs.confirm(owner, "Hay una versión nueva: " + r.version()
                    + " (tienes la " + Updater.currentVersion() + ")." + notes
                    + "\n\nSe descargará, la app se cerrará, se instalará y se volverá a abrir."
                    + "\n¿Actualizar ahora?");
            if (ok) download(r, button, status);
            else { status.setText(""); button.setDisable(false); }
        });
        start(check);
    }

    private static void download(Updater.Release r, Button button, Label status) {
        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception {
                return Updater.download(r, p -> updateProgress(p, 1.0));
            }
        };
        task.progressProperty().addListener((o, old, p) ->
                status.setText("Descargando… " + Math.round(p.doubleValue() * 100) + "%"));
        task.setOnFailed(e -> fail(button, status, task.getException()));
        task.setOnSucceeded(e -> {
            try {
                status.setText("Instalando…");
                Updater.installAndRestart(task.getValue());
                Platform.exit();
                System.exit(0);
            } catch (Exception ex) {
                fail(button, status, ex);
            }
        });
        status.setText("Descargando… 0%");
        start(task);
    }

    private static void fail(Button button, Label status, Throwable t) {
        String msg = (t == null || t.getMessage() == null) ? "error desconocido" : t.getMessage();
        status.setText("No se pudo actualizar: " + msg);
        button.setDisable(false);
    }

    private static void start(Task<?> task) {
        Thread t = new Thread(task, "updater");
        t.setDaemon(true);
        t.start();
    }
}