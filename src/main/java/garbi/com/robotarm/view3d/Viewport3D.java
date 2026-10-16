package garbi.com.robotarm.view3d;

import garbi.com.robotarm.physics.PhysicsWorld;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

/** Panel con la escena 3D: suelo, luces, ejes, brazo y cajas. */
public class Viewport3D extends Pane {

    private final Group world = new Group(); // sin transformaciones: la física depende de eso
    private final OrbitCamera orbit = new OrbitCamera();

    public Viewport3D(Node robot, Node crates) {
        buildGround();
        buildAxes();
        world.getChildren().addAll(robot, crates);

        Group root = new Group(world, buildLights(), orbit.getRig());

        SubScene subScene = new SubScene(root, 100, 100, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#2b2f36"));
        subScene.setCamera(orbit.getCamera());
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());

        orbit.attachTo(subScene);
        setMinSize(0, 0);
        getChildren().add(subScene);
    }

    private Group buildLights() {
        AmbientLight ambient = new AmbientLight(Color.gray(0.45));
        PointLight sun = new PointLight(Color.WHITE);
        sun.setTranslateX(-600);
        sun.setTranslateY(-1200);
        sun.setTranslateZ(-600);
        return new Group(ambient, sun);
    }

    private void buildGround() {
        double size = PhysicsWorld.FLOOR_HALF * 2;
        Box floor = new Box(size, 2, size);
        floor.setTranslateY(1); // la cara superior queda en y = 0
        floor.setMaterial(new PhongMaterial(Color.web("#4a505a")));
        world.getChildren().add(floor);
    }

    private void buildAxes() {
        world.getChildren().addAll(
                axis(300, 2, 2, Color.RED, 150, 0, 0),
                axis(2, 300, 2, Color.LIME, 0, -150, 0),
                axis(2, 2, 300, Color.DODGERBLUE, 0, 0, 150));
    }

    private Box axis(double w, double h, double d, Color c, double x, double y, double z) {
        Box b = new Box(w, h, d);
        b.setMaterial(new PhongMaterial(c));
        b.setTranslateX(x);
        b.setTranslateY(y);
        b.setTranslateZ(z);
        return b;
    }
}