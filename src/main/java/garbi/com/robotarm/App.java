package garbi.com.robotarm;

import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.blocks.ui.DragController;
import garbi.com.robotarm.blocks.ui.Palette;
import garbi.com.robotarm.blocks.ui.Workspace;
import garbi.com.robotarm.interpreter.Interpreter;
import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.motion.MotionController;
import garbi.com.robotarm.ui.ControlPanel;
import garbi.com.robotarm.ui.RunBar;
import garbi.com.robotarm.view3d.Viewport3D;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        RobotArm arm = new RobotArm();
        MotionController motion = new MotionController();
        Interpreter interpreter = new Interpreter(arm, motion);
        Program program = new Program("Mi programa");

        // capa transparente sobre toda la ventana, donde flota el bloque que se arrastra
        Pane dragLayer = new Pane();
        dragLayer.setMouseTransparent(true);

        // editor de bloques
        Workspace workspace = new Workspace(program, arm, interpreter.runningProperty());
        ScrollPane workspaceScroll = new ScrollPane(workspace);
        workspaceScroll.setFitToWidth(true);
        workspaceScroll.setFitToHeight(true);

        DragController drag = new DragController(
                dragLayer, workspaceScroll, workspace.getStack(), interpreter::isRunning);

        HBox editor = new HBox(new Palette(drag), workspaceScroll);
        HBox.setHgrow(workspaceScroll, Priority.ALWAYS);
        editor.setPrefWidth(620);

        // panel de control manual
        ScrollPane right = new ScrollPane(new ControlPanel(arm, motion));
        right.setFitToWidth(true);
        right.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        right.setPrefWidth(280);

        BorderPane root = new BorderPane();
        root.setLeft(editor);
        root.setCenter(new Viewport3D(arm));
        root.setRight(right);
        root.setBottom(new RunBar(interpreter, program));

        stage.setTitle("Robot Arm Programmer");
        stage.setScene(new Scene(new StackPane(root, dragLayer), 1500, 800));
        stage.show();
    }
}