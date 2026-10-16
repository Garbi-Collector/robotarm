package garbi.com.robotarm.motion;

import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import garbi.com.robotarm.physics.PhysicsWorld;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mueve los servos y simula la física en micro-pasos (240 Hz).
 * Cada micro-paso: avanza los servos, valida colisiones y avanza las cajas.
 * Crear y usar (salvo moveTo/stop) desde el hilo de JavaFX.
 */
public class MotionController {

    private static final double BASE_SUBSTEP = 1.0 / 240;
    private static final int MAX_SUBSTEPS_PER_FRAME = 24;
    private static final double MAX_PENETRATION = 5.0; // unidades; más = tolera más choque antes de bloquear
    private static final double RELEASE_ANGLE = 2.0; // grados que debe abrirse la garra para soltar
    private double heldAngle;                         // ángulo de la garra al agarrar

    private static final class Move {
        final Map<Servo, Double> start = new LinkedHashMap<>();
        final Map<Servo, Double> target = new LinkedHashMap<>();
        final CompletableFuture<Void> done;
        double duration;
        double elapsed;

        Move(CompletableFuture<Void> done) { this.done = done; }
    }

    private final RobotArm arm;
    private final PhysicsWorld physics;
    private final DoubleProperty speedFactor = new SimpleDoubleProperty(1.0);
    private final Map<Servo, Double> jog = new LinkedHashMap<>();       // objetivos de los sliders
    private final Map<Servo, Double> lastValid = new HashMap<>();       // última pose sin colisión

    private Move active;
    private Move justFinished;
    private double accumulator;

    private final AnimationTimer timer = new AnimationTimer() {
        private long last = -1;

        @Override
        public void handle(long now) {
            if (last < 0) { last = now; return; }
            double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            frame(dt);
        }
    };

    public MotionController(RobotArm arm, PhysicsWorld physics) {
        this.arm = arm;
        this.physics = physics;
        snapshot();
        timer.start();
    }

    public DoubleProperty speedFactorProperty() { return speedFactor; }

    /**
     * Mueve los servos indicados, todos llegando al mismo tiempo. Se puede llamar desde cualquier hilo.
     * El future se completa al llegar, se cancela si otro movimiento o stop() lo interrumpe,
     * y falla con MotionBlockedException si el brazo choca.
     */
    public CompletableFuture<Void> moveTo(Map<Servo, Double> targets) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> begin(targets, result));
        return result;
    }

    /** Detiene to do movimiento (los servos quedan donde están). */
    public void stop() {
        Platform.runLater(() -> { cancelActive(); jog.clear(); });
    }

    /** Control manual: el servo persigue el objetivo a su velocidad máxima. Hilo de JavaFX. */
    public void jog(Servo servo, double target) {
        cancelActive();
        jog.put(servo, Math.max(servo.getMin(), Math.min(servo.getMax(), target)));
    }

    // ---------- bucle ----------

    private void frame(double dt) {
        // a más velocidad global, micro-pasos más cortos: el recorrido por paso se mantiene acotado
        double sub = BASE_SUBSTEP / Math.max(1.0, speedFactor.get());
        accumulator += dt;
        int n = 0;
        while (accumulator >= sub && n < MAX_SUBSTEPS_PER_FRAME) {
            accumulator -= sub;
            n++;
            substep(sub);
        }
        if (accumulator >= sub) accumulator = 0; // si el equipo no da abasto, la simulación va más lenta
        physics.syncVisuals();
    }

    private void substep(double dt) {
        advance(dt);
        Servo g = arm.gripper();
        if (physics.isHolding()) {
            if (g.getAngle() > heldAngle + RELEASE_ANGLE) {
                physics.release();                 // la garra se abrió: la caja cae
            } else if (g.getAngle() < heldAngle) {
                g.setAngle(heldAngle);             // no se cierra más: hay una caja entre los dedos
                freezeGripper();
            }
        }
        PhysicsWorld.Contacts c = null;
        boolean blocked = physics.armBelowFloor();
        if (!blocked) {
            c = physics.step(dt);
            blocked = c.armPenetration > MAX_PENETRATION
                    || (!c.gripping && c.fingerPenetration > MAX_PENETRATION);
        }

        if (blocked) {
            lastValid.forEach(Servo::setAngle);   // vuelve a la última pose sin colisión
            jog.clear();
            failMove(new MotionBlockedException(
                    "Colisión: el brazo chocó con el suelo o con una caja y se detuvo."));
            if (c == null) physics.step(dt);       // las cajas siguen su curso
            return;
        }

        // garra cerrándose sobre una caja: se queda en este ángulo
        if (c.gripping && isClosing(arm.gripper())) {
            freezeGripper();
            if (physics.grab()) heldAngle = arm.gripper().getAngle();
        }

        snapshot();
        if (justFinished != null) {
            justFinished.done.complete(null);
            justFinished = null;
        }
    }

    private void advance(double dt) {
        Move m = active;
        if (m != null) {
            m.elapsed += dt;
            double t = Math.min(1.0, m.elapsed / m.duration);
            for (Map.Entry<Servo, Double> e : m.target.entrySet()) {
                double from = m.start.get(e.getKey());
                e.getKey().setAngle(from + (e.getValue() - from) * t);
            }
            if (t >= 1.0) { justFinished = m; active = null; }
        }

        if (!jog.isEmpty()) {
            double speed = Math.max(0.05, speedFactor.get());
            Iterator<Map.Entry<Servo, Double>> it = jog.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Servo, Double> e = it.next();
                Servo s = e.getKey();
                double step = s.getMaxSpeed() * speed * dt;
                double diff = e.getValue() - s.getAngle();
                if (Math.abs(diff) <= step) { s.setAngle(e.getValue()); it.remove(); }
                else s.setAngle(s.getAngle() + Math.signum(diff) * step);
            }
        }
    }

    // ---------- movimiento ----------

    private void begin(Map<Servo, Double> targets, CompletableFuture<Void> result) {
        cancelActive();
        jog.clear();

        Move move = new Move(result);
        double duration = 0;
        double speed = Math.max(0.05, speedFactor.get());

        for (Map.Entry<Servo, Double> e : targets.entrySet()) {
            Servo s = e.getKey();
            double goal = Math.max(s.getMin(), Math.min(s.getMax(), e.getValue()));
            double from = s.getAngle();
            move.start.put(s, from);
            move.target.put(s, goal);
            duration = Math.max(duration, Math.abs(goal - from) / (s.getMaxSpeed() * speed));
        }

        if (duration < 1e-6) {
            move.target.forEach(Servo::setAngle);
            result.complete(null);
            return;
        }

        move.duration = duration;
        active = move;
    }

    private void cancelActive() {
        if (active != null) {
            active.done.cancel(false);
            active = null;
        }
    }

    private void failMove(RuntimeException error) {
        Move m = active != null ? active : justFinished;
        active = null;
        justFinished = null;
        if (m != null) m.done.completeExceptionally(error);
    }

    // ---------- agarre ----------

    private boolean isClosing(Servo g) {
        return g.getAngle() < lastValid.get(g) - 1e-9;
    }

    private void freezeGripper() {
        Servo g = arm.gripper();
        jog.remove(g);

        Move m = active != null ? active : justFinished;
        if (m == null || !m.target.containsKey(g)) return;

        double now = g.getAngle();
        m.start.put(g, now);
        m.target.put(g, now);

        if (m == active) {
            // si solo quedaba la garra moviéndose, el movimiento termina aquí
            boolean othersMoving = false;
            for (Map.Entry<Servo, Double> e : m.target.entrySet()) {
                if (e.getKey() != g && Math.abs(e.getValue() - m.start.get(e.getKey())) > 1e-6) {
                    othersMoving = true;
                    break;
                }
            }
            if (!othersMoving) { justFinished = active; active = null; }
        }
    }

    private void snapshot() {
        for (Servo s : arm.getServos()) lastValid.put(s, s.getAngle());
    }
}