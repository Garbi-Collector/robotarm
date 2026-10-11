package garbi.com.robotarm;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.motion.MotionController;
import garbi.com.robotarm.ui.ControlPanel;
import garbi.com.robotarm.view3d.Viewport3D;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        RobotArm arm = new RobotArm();
        MotionController motion = new MotionController();

        ScrollPane right = new ScrollPane(new ControlPanel(arm, motion));
        right.setFitToWidth(true);
        right.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        right.setPrefWidth(280);

        BorderPane root = new BorderPane();
        root.setCenter(new Viewport3D(arm));
        root.setRight(right);

        stage.setTitle("Robot Arm Programmer");
        stage.setScene(new Scene(root, 1200, 750));
        stage.show();
    }
}