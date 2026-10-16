package garbi.com.robotarm.view3d;

import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
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

    private final Translate target = new Translate(0, -250, 0);
    private final Rotate yaw = new Rotate(30, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-25, Rotate.X_AXIS); // negativo = cámara por encima del suelo
    private final Translate dolly = new Translate(0, 0, -1200);

    private double lastX, lastY;

    public OrbitCamera() {
        camera.setNearClip(1);
        camera.setFarClip(20000);
        camera.setFieldOfView(40);
        camera.getTransforms().add(dolly);
        rig.getTransforms().addAll(target, yaw, pitch);
    }

    public PerspectiveCamera getCamera() { return camera; }

    /** Este grupo debe agregarse al grafo de la escena (contiene la cámara). */
    public Group getRig() { return rig; }

    public void attachTo(SubScene subScene) {
        // Los filtros reciben el evento siempre, sin importar qué nodo 3D esté bajo el cursor.
        subScene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            lastX = e.getScreenX();
            lastY = e.getScreenY();
        });

        subScene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            double dx = e.getScreenX() - lastX;
            double dy = e.getScreenY() - lastY;
            lastX = e.getScreenX();
            lastY = e.getScreenY();

            if (e.isPrimaryButtonDown()) {
                yaw.setAngle(yaw.getAngle() + dx * 0.4);
                pitch.setAngle(clamp(pitch.getAngle() - dy * 0.4, -89, -2));
            }
        });

        subScene.addEventFilter(ScrollEvent.SCROLL, e ->
                dolly.setZ(clamp(dolly.getZ() + e.getDeltaY() * 1.0, -4000, -200)));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}