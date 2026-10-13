package garbi.com.robotarm;

import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.blocks.ui.BlockContext;
import garbi.com.robotarm.blocks.ui.DragController;
import garbi.com.robotarm.blocks.ui.Palette;
import garbi.com.robotarm.blocks.ui.Workspace;
import garbi.com.robotarm.interpreter.Interpreter;
import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.motion.MotionController;
import garbi.com.robotarm.ui.ControlPanel;
import garbi.com.robotarm.ui.MethodActions;
import garbi.com.robotarm.ui.ProjectManager;
import garbi.com.robotarm.ui.RunBar;
import garbi.com.robotarm.ui.StartScreen;
import garbi.com.robotarm.view3d.Viewport3D;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.nio.file.Path;

public class App extends Application {

    private Stage stage;
    private Scene startScene;
    private Scene editorScene;
    private ProjectManager project;
    private Interpreter interpreter;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        RobotArm arm = new RobotArm();
        MotionController motion = new MotionController();
        interpreter = new Interpreter(arm, motion);
        Program program = new Program();

        // capa transparente sobre toda la ventana, donde flota el bloque que se arrastra
        Pane dragLayer = new Pane();
        dragLayer.setMouseTransparent(true);

        BlockContext ctx = new BlockContext(arm, interpreter.runningProperty(), program);

        // editor de bloques
        Workspace workspace = new Workspace(program, ctx);
        ScrollPane workspaceScroll = new ScrollPane(workspace);
        workspaceScroll.setFitToWidth(true);
        workspaceScroll.setFitToHeight(true);

        DragController drag = new DragController(
                dragLayer, workspaceScroll, workspace.getStack(), ctx);

        HBox editor = new HBox(new Palette(drag), workspaceScroll);
        HBox.setHgrow(workspaceScroll, Priority.ALWAYS);
        editor.setPrefWidth(680);

        // panel de control manual
        ScrollPane right = new ScrollPane(new ControlPanel(arm, motion));
        right.setFitToWidth(true);
        right.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        right.setPrefWidth(280);

        // proyectos y métodos
        Runnable refresh = () -> workspace.getStack().refresh();
        project = new ProjectManager(stage, program, refresh);
        MethodActions methods = new MethodActions(stage, program, refresh);
        ctx.setOnSaveMethod(methods::saveFromBlocks);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(methods));
        root.setLeft(editor);
        root.setCenter(new Viewport3D(arm));
        root.setRight(right);
        root.setBottom(new RunBar(interpreter, program));

        editorScene = new Scene(new StackPane(root, dragLayer), 1500, 800);
        startScene = new Scene(new StartScreen(this::startNew, this::startOpen), 520, 340);

        // el título muestra el nombre del proyecto y un * si hay cambios sin guardar
        Timeline titleTimer = new Timeline(new KeyFrame(Duration.millis(500), e -> updateTitle()));
        titleTimer.setCycleCount(Animation.INDEFINITE);
        titleTimer.play();

        stage.setOnCloseRequest(e -> {
            if (stage.getScene() != editorScene) return;
            if (!project.confirmDiscard()) {
                e.consume(); // el usuario canceló: no se cierra
                return;
            }
            interpreter.stop();
        });

        switchTo(startScene);
        stage.show();

        // si se lanzó con un archivo (p. ej. doble clic con la extensión asociada), se abre directo
        getParameters().getRaw().stream()
                .filter(a -> a.toLowerCase().endsWith(".raa"))
                .findFirst()
                .ifPresent(a -> {
                    if (project.openPath(Path.of(a))) switchTo(editorScene);
                });
    }

    // ---------- menú principal ----------

    private void startNew() {
        project.newProject();
        switchTo(editorScene);
    }

    private void startOpen() {
        if (project.open()) switchTo(editorScene);
    }

    // ---------- barra de menú ----------

    private MenuBar buildMenuBar(MethodActions methods) {
        MenuItem miNew    = item("Nuevo proyecto",  "Shortcut+N",       () -> { if (project.confirmDiscard()) project.newProject(); });
        MenuItem miOpen   = item("Abrir…",          "Shortcut+O",       project::open);
        MenuItem miSave   = item("Guardar",         "Shortcut+S",       project::save);
        MenuItem miSaveAs = item("Guardar como…",   "Shortcut+Shift+S", project::saveAs);
        MenuItem miClose  = item("Cerrar proyecto", null, () -> {
            if (project.confirmDiscard()) {
                project.newProject();
                switchTo(startScene);
            }
        });
        MenuItem miExit   = item("Salir", null,
                () -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));

        // no se cambia de proyecto mientras corre un programa
        miNew.disableProperty().bind(interpreter.runningProperty());
        miOpen.disableProperty().bind(interpreter.runningProperty());
        miClose.disableProperty().bind(interpreter.runningProperty());

        Menu file = new Menu("Archivo", null, miNew, miOpen, new SeparatorMenuItem(),
                miSave, miSaveAs, new SeparatorMenuItem(), miClose, miExit);

        MenuItem miImport = item("Importar método (.ram)…", null, methods::importMethods);
        MenuItem miExport = item("Exportar método…",         null, methods::exportMethod);
        MenuItem miDelete = item("Eliminar método…",         null, methods::deleteMethod);
        miImport.disableProperty().bind(interpreter.runningProperty());
        miDelete.disableProperty().bind(interpreter.runningProperty());

        Menu methodMenu = new Menu("Métodos", null, miImport, miExport, miDelete);

        return new MenuBar(file, methodMenu);
    }

    private static MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (accelerator != null) mi.setAccelerator(KeyCombination.keyCombination(accelerator));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    // ---------- ventana ----------

    private void switchTo(Scene scene) {
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
        updateTitle();
    }

    private void updateTitle() {
        if (stage.getScene() == editorScene) {
            stage.setTitle(project.displayName() + (project.isDirty() ? " *" : "")
                    + " — Robot Arm Programmer");
        } else {
            stage.setTitle("Robot Arm Programmer");
        }
    }
}