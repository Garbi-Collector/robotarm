package garbi.com.robotarm.physics;

import javafx.scene.Node;

/** Una pieza del brazo que participa en las colisiones. */
public record ArmCollider(Node node, Role role) {

    public enum Role {
        STATIC,        // fija (pedestal)
        ARM,           // se mueve con los servos
        FINGER_LEFT,
        FINGER_RIGHT
    }
}