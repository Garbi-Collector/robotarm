package garbi.com.robotarm.ui;

import garbi.com.robotarm.physics.PhysicsWorld;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Agregar, reiniciar y quitar cajas. */
public class CratePanel extends VBox {

    public CratePanel(PhysicsWorld physics, ReadOnlyBooleanProperty locked) {
        setSpacing(10);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #e8eaed;");

        Label title = new Label("Cajas");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Spinner<Integer> size = spinner(20, 60, 30, 5);
        Spinner<Integer> x = spinner(-700, 700, 320, 10);
        Spinner<Integer> z = spinner(-700, 700, 0, 10);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.addRow(0, new Label("Tamaño"), size);
        grid.addRow(1, new Label("X (adelante)"), x);
        grid.addRow(2, new Label("Z (lado)"), z);

        Button add = new Button("Agregar caja");
        add.setOnAction(e -> physics.addCrate(read(x, -700, 700), read(z, -700, 700), read(size, 20, 60)));

        Button reset = new Button("Reiniciar cajas");
        reset.setOnAction(e -> physics.resetCrates());

        Button clear = new Button("Quitar todas");
        clear.setOnAction(e -> physics.clearCrates());

        for (Button b : new Button[]{add, reset, clear}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.disableProperty().bind(locked);
        }

        Label hint = new Label("X positivo = delante del brazo (con la base en 90°).\n"
                + "Abre la garra antes de agarrar: abre hasta ~64.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a6068;");

        getChildren().addAll(title, grid, add, reset, clear, hint);
    }

    private static Spinner<Integer> spinner(int min, int max, int init, int step) {
        Spinner<Integer> sp = new Spinner<>(min, max, init, step);
        sp.setEditable(true);
        sp.setPrefWidth(95);
        return sp;
    }

    /** Lee el texto del editor (el valor del spinner no se confirma hasta perder el foco). */
    private static int read(Spinner<Integer> sp, int min, int max) {
        try {
            int v = Integer.parseInt(sp.getEditor().getText().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return sp.getValue();
        }
    }
}