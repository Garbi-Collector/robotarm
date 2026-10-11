package garbi.com.robotarm;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.ui.ControlPanel;
import garbi.com.robotarm.view3d.Viewport3D;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        RobotArm arm = new RobotArm();

        BorderPane root = new BorderPane();
        root.setCenter(new Viewport3D(arm));
        root.setRight(new ControlPanel(arm));

        stage.setTitle("Robot Arm Programmer");
        stage.setScene(new Scene(root, 1200, 750));
        stage.show();
    }
}