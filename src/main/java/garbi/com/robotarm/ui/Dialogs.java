package garbi.com.robotarm.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public final class Dialogs {

    private Dialogs() {}

    public static void error(Window owner, String message) { show(Alert.AlertType.ERROR, owner, message); }
    public static void info(Window owner, String message)  { show(Alert.AlertType.INFORMATION, owner, message); }

    public static boolean confirm(Window owner, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private static void show(Alert.AlertType type, Window owner, String message) {
        Alert a = new Alert(type, message, ButtonType.OK);
        a.initOwner(owner);
        a.setHeaderText(null);
        a.showAndWait();
    }
}