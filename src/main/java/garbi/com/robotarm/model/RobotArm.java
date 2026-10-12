package garbi.com.robotarm.model;

import java.util.List;

public class RobotArm {

    private final Servo base= new Servo(
            "Base",
            0,
            180,
            90,
            90);
    private final Servo shoulder= new Servo(
            "Hombro",
            0,
            180,
            90,
            90);
    private final Servo upperArmRoll= new Servo(
            "Giro brazo",
            0,
            180,
            90,
            100);
    private final Servo elbow= new Servo(
            "Codo",
            0,
            180,
            90,
            90);
    private final Servo forearmRoll= new Servo(
            "Giro antebrazo",
            0,
            180,
            90,
            120);
    private final Servo wristPitch= new Servo(
            "Muñeca",
            0,
            180,
            90,
            120);
    private final Servo wristRoll= new Servo(
            "Giro muñeca",
            0,
            180,
            90,
            120);
    private final Servo gripper= new Servo(
            "Garra",
            0,
            90,
            45,
            120);

    private final List<Servo> servos = List.of(
            base, shoulder, upperArmRoll, elbow, forearmRoll, wristPitch, wristRoll, gripper);

    public Servo base() { return base; }
    public Servo shoulder() { return shoulder; }
    public Servo upperArmRoll() { return upperArmRoll; }
    public Servo elbow() { return elbow; }
    public Servo forearmRoll() { return forearmRoll; }
    public Servo wristPitch() { return wristPitch; }
    public Servo wristRoll() { return wristRoll; }
    public Servo gripper() { return gripper; }

    public List<Servo> getServos() { return servos; }

    public void resetHome() { servos.forEach(Servo::reset); }

    public Servo byName(String name) {
        return servos.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Servo desconocido: " + name));
    }
}