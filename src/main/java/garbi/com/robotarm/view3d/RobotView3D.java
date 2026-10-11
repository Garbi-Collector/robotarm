package garbi.com.robotarm.view3d;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;

/** Brazo armado con primitivas, como jerarquía de Groups enlazada a los servos. */
public class RobotView3D extends Group {

    private static final double L1 = 120; // brazo superior
    private static final double L2 = 110; // antebrazo
    private static final double L3 = 40;  // segmento de muñeca

    private static final PhongMaterial METAL  = mat("#9aa5b1");
    private static final PhongMaterial ACCENT = mat("#f28c28");
    private static final PhongMaterial DARK   = mat("#3b4048");

    public RobotView3D(RobotArm arm) {
        // Base fija
        Cylinder pedestal = cyl(70, 12, DARK);
        pedestal.setTranslateY(-6);

        // 1. Torre giratoria (base, giro Y)
        Group turret = new Group();
        turret.getTransforms().add(rotation(Rotate.Y_AXIS, arm.base()));
        Cylinder turretBody = cyl(45, 30, METAL);
        turretBody.setTranslateY(-27);
        turret.getChildren().add(turretBody);

        // 2. Hombro (cabeceo Z)
        Group shoulder = new Group();
        shoulder.setTranslateY(-45);
        shoulder.getTransforms().add(rotation(Rotate.Z_AXIS, arm.shoulder()));
        shoulder.getChildren().add(joint());

        // 3. Giro del brazo superior (giro Y): contiene el eslabón y todo lo siguiente
        Group upperRoll = new Group();
        upperRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.upperArmRoll()));
        upperRoll.getChildren().addAll(link(L1, ACCENT), fin(L1));

        // 4. Codo (cabeceo Z)
        Group elbow = new Group();
        elbow.setTranslateY(-L1);
        elbow.getTransforms().add(rotation(Rotate.Z_AXIS, arm.elbow()));
        elbow.getChildren().add(joint());

        // 5. Giro del antebrazo (giro Y)
        Group foreRoll = new Group();
        foreRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.forearmRoll()));
        foreRoll.getChildren().addAll(link(L2, METAL), fin(L2));

        // 6. Muñeca (cabeceo Z)
        Group wrist = new Group();
        wrist.setTranslateY(-L2);
        wrist.getTransforms().add(rotation(Rotate.Z_AXIS, arm.wristPitch()));
        wrist.getChildren().addAll(joint(), link(L3, ACCENT));

        // 7. Giro de muñeca (giro Y) + 8. garra
        Group wristRoll = new Group();
        wristRoll.setTranslateY(-L3);
        wristRoll.getTransforms().add(rotation(Rotate.Y_AXIS, arm.wristRoll()));
        wristRoll.getChildren().add(buildGripper(arm.gripper()));

        // Anidado: cada grupo cuelga del anterior
        wrist.getChildren().add(wristRoll);
        foreRoll.getChildren().add(wrist);
        elbow.getChildren().add(foreRoll);
        upperRoll.getChildren().add(elbow);
        shoulder.getChildren().add(upperRoll);
        turret.getChildren().add(shoulder);

        getChildren().addAll(pedestal, turret);
    }

    private Group buildGripper(Servo gripper) {
        Box palm = new Box(50, 12, 20);
        palm.setMaterial(DARK);
        palm.setTranslateY(-6);

        Box left = new Box(8, 40, 14);
        Box right = new Box(8, 40, 14);
        left.setMaterial(METAL);
        right.setMaterial(METAL);
        left.setTranslateY(-32);
        right.setTranslateY(-32);

        left.translateXProperty().bind(gripper.angleProperty().multiply(-0.25).subtract(4.5));
        right.translateXProperty().bind(gripper.angleProperty().multiply(0.25).add(4.5));

        return new Group(palm, left, right);
    }

    // ---------- helpers ----------

    private static Rotate rotation(Point3D axis, Servo servo) {
        Rotate r = new Rotate(0, axis);
        r.angleProperty().bind(servo.angleProperty().subtract(90));
        return r;
    }

    private static Box link(double length, PhongMaterial m) {
        Box b = new Box(18, length, 18);
        b.setMaterial(m);
        b.setTranslateY(-length / 2);
        return b;
    }

    /** Aleta lateral que hace visible el giro del eslabón sobre su eje. */
    private static Box fin(double length) {
        Box f = new Box(8, length * 0.6, 8);
        f.setMaterial(mat("#e84a5f"));
        f.setTranslateX(13);
        f.setTranslateY(-length / 2);
        return f;
    }

    private static Cylinder joint() {
        Cylinder c = cyl(14, 30, DARK);
        c.setRotationAxis(Rotate.X_AXIS);
        c.setRotate(90);
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