package garbi.com.robotarm.view3d;

import garbi.com.robotarm.model.RobotArm;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

/** Panel que contiene la escena 3D (suelo, luces, ejes y, luego, el brazo). */
public class Viewport3D extends Pane {

    private final Group world = new Group();
    private final OrbitCamera orbit = new OrbitCamera();

    public Viewport3D(RobotArm arm) {
        buildGround();
        buildAxes();
        world.getChildren().add(new RobotView3D(arm));

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

    public Group getWorld() { return world; }

    private Group buildLights() {
        AmbientLight ambient = new AmbientLight(Color.gray(0.45));
        PointLight sun = new PointLight(Color.WHITE);
        sun.setTranslateX(-300);
        sun.setTranslateY(-600);
        sun.setTranslateZ(-300);
        return new Group(ambient, sun);
    }

    private void buildGround() {
        Box floor = new Box(800, 2, 800);
        floor.setTranslateY(1); // la cara superior queda en y = 0
        floor.setMaterial(new PhongMaterial(Color.web("#4a505a")));
        world.getChildren().add(floor);
    }

    private void buildAxes() {
        world.getChildren().addAll(
                axis(200, 2, 2, Color.RED, 100, 0, 0),     // X
                axis(2, 200, 2, Color.LIME, 0, -100, 0),   // Y (hacia arriba = negativo)
                axis(2, 2, 200, Color.DODGERBLUE, 0, 0, 100) // Z
        );
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