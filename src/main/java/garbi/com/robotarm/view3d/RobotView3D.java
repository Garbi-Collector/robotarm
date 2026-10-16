package garbi.com.robotarm.view3d;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import garbi.com.robotarm.physics.ArmCollider;
import garbi.com.robotarm.physics.ArmCollider.Role;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Brazo armado con primitivas, enlazado a los servos. Registra sus piezas como colliders. */
public class RobotView3D extends Group {

    private static final double L1 = 200;        // brazo superior
    private static final double L2 = 180;        // antebrazo
    private static final double L3 = 70;         // segmento de muñeca
    private static final double LINK_W = 22;
    private static final double SHOULDER_Y = 60; // altura del hombro

    private static final PhongMaterial METAL  = mat("#9aa5b1");
    private static final PhongMaterial ACCENT = mat("#f28c28");
    private static final PhongMaterial DARK   = mat("#3b4048");

    private final List<ArmCollider> colliders = new ArrayList<>();

    public RobotView3D(RobotArm arm) {
        Cylinder pedestal = cyl(85, 12, DARK);
        pedestal.setTranslateY(-6);
        reg(pedestal, Role.STATIC);

        // 1. Torre giratoria (base)
        Group turret = new Group();
        turret.getTransforms().add(rotation(Rotate.Y_AXIS, arm.base()));
        Cylinder turretBody = cyl(50, 40, METAL);
        turretBody.setTranslateY(-32);
        reg(turretBody, Role.ARM);
        turret.getChildren().add(turretBody);

        // 2. Hombro
        Group shoulder = new Group();
        shoulder.setTranslateY(-SHOULDER_Y);
        shoulder.getTransforms().add(rotation(Rotate.Z_AXIS, arm.shoulder()));
        shoulder.getChildren().add(joint());

        // 3. Giro del brazo superior
        Group upperRoll = new Group();
        upperRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.upperArmRoll()));
        upperRoll.getChildren().addAll(link(L1, ACCENT), fin(L1));

        // 4. Codo
        Group elbow = new Group();
        elbow.setTranslateY(-L1);
        elbow.getTransforms().add(rotation(Rotate.Z_AXIS, arm.elbow()));
        elbow.getChildren().add(joint());

        // 5. Giro del antebrazo
        Group foreRoll = new Group();
        foreRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.forearmRoll()));
        foreRoll.getChildren().addAll(link(L2, METAL), fin(L2));

        // 6. Muñeca
        Group wrist = new Group();
        wrist.setTranslateY(-L2);
        wrist.getTransforms().add(rotation(Rotate.Z_AXIS, arm.wristPitch()));
        wrist.getChildren().addAll(joint(), link(L3, ACCENT));

        // 7. Giro de muñeca + 8. garra
        Group wristRoll = new Group();
        wristRoll.setTranslateY(-L3);
        wristRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.wristRoll()));
        wristRoll.getChildren().add(buildGripper(arm.gripper()));

        wrist.getChildren().add(wristRoll);
        foreRoll.getChildren().add(wrist);
        elbow.getChildren().add(foreRoll);
        upperRoll.getChildren().add(elbow);
        shoulder.getChildren().add(upperRoll);
        turret.getChildren().add(shoulder);

        getChildren().addAll(pedestal, turret);
    }

    /** Piezas que participan en las colisiones. */
    public List<ArmCollider> getColliders() { return Collections.unmodifiableList(colliders); }

    private Group buildGripper(Servo gripper) {
        Box palm = new Box(100, 12, 26);
        palm.setMaterial(DARK);
        palm.setTranslateY(-6);
        reg(palm, Role.ARM);

        Box left = finger();
        Box right = finger();
        // a 0° los dedos se tocan; a 90° la abertura interior es de ~64 unidades
        left.translateXProperty().bind(gripper.angleProperty().multiply(-0.35).subtract(4.5));
        right.translateXProperty().bind(gripper.angleProperty().multiply(0.35).add(4.5));
        reg(left, Role.FINGER_LEFT);
        reg(right, Role.FINGER_RIGHT);

        return new Group(palm, left, right);
    }

    private Box finger() {
        Box b = new Box(8, 50, 20);
        b.setMaterial(METAL);
        b.setTranslateY(-37);
        return b;
    }

    // ---------- helpers ----------

    private void reg(Node node, Role role) { colliders.add(new ArmCollider(node, role)); }

    private static Rotate rotation(Point3D axis, Servo servo) {
        Rotate r = new Rotate(0, axis);
        r.angleProperty().bind(servo.angleProperty().subtract(90));
        return r;
    }

    private Box link(double length, PhongMaterial m) {
        Box b = new Box(LINK_W, length, LINK_W);
        b.setMaterial(m);
        b.setTranslateY(-length / 2);
        reg(b, Role.ARM);
        return b;
    }

    private static Box fin(double length) {
        Box f = new Box(8, length * 0.6, 8);
        f.setMaterial(mat("#e84a5f"));
        f.setTranslateX(LINK_W / 2 + 4);
        f.setTranslateY(-length / 2);
        return f;
    }

    private Cylinder joint() {
        Cylinder c = cyl(16, 36, DARK);
        c.setRotationAxis(Rotate.X_AXIS);
        c.setRotate(90);
        reg(c, Role.ARM);
        return c;
    }

    private static Cylinder cyl(double radius, double height, PhongMaterial m) {
        Cylinder c = new Cylinder(radius, height);
        c.setMaterial(m);
        return c;
    }

    private static PhongMaterial mat(String hex) {
        PhongMaterial m = new PhongMaterial(Color.web(hex));
        m.setSpecularColor(Color.gray(0.4));
        return m;
    }
}