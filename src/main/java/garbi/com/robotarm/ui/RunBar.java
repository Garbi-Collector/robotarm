package garbi.com.robotarm.ui;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.interpreter.Interpreter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class RunBar extends HBox {

    private final Label status = new Label("Listo");
    private volatile String lastError;

    public RunBar(Interpreter interpreter, Program program) {
        setSpacing(10);
        setPadding(new Insets(8, 15, 8, 15));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #e3e5e8;");

        Button run = new Button("▶ Ejecutar");
        Button pause = new Button("⏸ Pausar");
        Button stop = new Button("■ Detener");
        pause.setDisable(true);
        stop.setDisable(true);

        run.setOnAction(e -> {
            lastError = null;
            interpreter.run(program);
            run.setDisable(true);
            pause.setDisable(false);
            stop.setDisable(false);
        });

        pause.setOnAction(e -> {
            if (interpreter.isPaused()) {
                interpreter.resume();
                pause.setText("⏸ Pausar");
            } else {
                interpreter.pause();
                pause.setText("⏵ Continuar");
            }
        });

        stop.setOnAction(e -> interpreter.stop());

        interpreter.setListener(new Interpreter.Listener() {
            @Override
            public void onBlockStart(Block block) {
                Platform.runLater(() -> status.setText("Ejecutando: " + block.describe()));
            }

            @Override
            public void onError(String message) {
                lastError = message; // se muestra en onFinished, que llega justo después
            }

            @Override
            public void onFinished(boolean completed) {
                Platform.runLater(() -> {
                    if (lastError != null) status.setText("Error: " + lastError);
                    else status.setText(completed ? "Programa terminado" : "Detenido");
                    run.setDisable(false);
                    pause.setDisable(true);
                    pause.setText("⏸ Pausar");
                    stop.setDisable(true);
                });
            }
        });

        getChildren().addAll(run, pause, stop, status);
    }
}