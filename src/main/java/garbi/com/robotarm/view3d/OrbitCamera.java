package garbi.com.robotarm.view3d;

import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Cámara que orbita alrededor de un punto.
 * Arrastrar con click izquierdo = rotar. Rueda del mouse = zoom.
 */
public class OrbitCamera {

    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Group rig = new Group(camera);

    private final Translate target = new Translate(0, -150, 0); // punto que mira
    private final Rotate yaw = new Rotate(30, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-25, Rotate.X_AXIS); // negativo = cámara por encima del suelo
    private final Translate dolly = new Translate(0, 0, -700);   // distancia al target

    private double lastX, lastY;

    public OrbitCamera() {
        camera.setNearClip(1);
        camera.setFarClip(10000);
        camera.setFieldOfView(40);
        camera.getTransforms().add(dolly);
        rig.getTransforms().addAll(target, yaw, pitch);
    }

    public PerspectiveCamera getCamera() { return camera; }

    /** Este grupo debe agregarse al grafo de la escena (contiene la cámara). */
    public Group getRig() { return rig; }

    public void attachTo(SubScene subScene) {
        subScene.setOnMousePressed(e -> {
            lastX = e.getSceneX();
            lastY = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - lastX;
            double dy = e.getSceneY() - lastY;
            lastX = e.getSceneX();
            lastY = e.getSceneY();

            if (e.isPrimaryButtonDown()) {
                yaw.setAngle(yaw.getAngle() + dx * 0.4);
                pitch.setAngle(clamp(pitch.getAngle() - dy * 0.4, -89, -2));
            }
        });

        subScene.setOnScroll(e ->
                dolly.setZ(clamp(dolly.getZ() + e.getDeltaY() * 0.5, -2500, -150)));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}