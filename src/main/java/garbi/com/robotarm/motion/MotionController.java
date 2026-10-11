package garbi.com.robotarm.motion;

import garbi.com.robotarm.model.Servo;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mueve servos hacia ángulos objetivo respetando su velocidad máxima.
 * moveTo() se puede llamar desde cualquier hilo; la animación corre en el hilo de JavaFX.
 */
public class MotionController {

    private static final class Move {
        final Map<Servo, Double> start = new LinkedHashMap<>();
        final Map<Servo, Double> target = new LinkedHashMap<>();
        final CompletableFuture<Void> done;
        double duration;
        double elapsed;

        Move(CompletableFuture<Void> done) { this.done = done; }
    }

    private final DoubleProperty speedFactor = new SimpleDoubleProperty(1.0);
    private Move active; // solo se toca desde el hilo de JavaFX

    private final AnimationTimer timer = new AnimationTimer() {
        private long last = -1;

        @Override
        public void handle(long now) {
            if (last < 0) { last = now; return; }
            double dt = (now - last) / 1e9;
            last = now;
            step(dt);
        }

        @Override
        public void stop() {
            super.stop();
            last = -1;
        }
    };

    public DoubleProperty speedFactorProperty() { return speedFactor; }

    /**
     * Mueve los servos indicados a sus ángulos objetivo, todos llegando al mismo tiempo.
     * El future se completa al terminar, o se cancela si otro movimiento o stop() lo interrumpe.
     */
    public CompletableFuture<Void> moveTo(Map<Servo, Double> targets) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> begin(targets, result));
        return result;
    }

    /** Detiene el movimiento actual (los servos quedan donde están). */
    public void stop() {
        Platform.runLater(this::cancelActive);
    }

    // ---------- interno (hilo FX) ----------

    private void begin(Map<Servo, Double> targets, CompletableFuture<Void> result) {
        cancelActive();

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
        timer.start();
    }

    private void step(double dt) {
        if (active == null) { timer.stop(); return; }

        active.elapsed += dt;
        double t = Math.min(1.0, active.elapsed / active.duration);

        for (Map.Entry<Servo, Double> e : active.target.entrySet()) {
            Servo s = e.getKey();
            double from = active.start.get(s);
            s.setAngle(from + (e.getValue() - from) * t);
        }

        if (t >= 1.0) {
            Move finished = active;
            active = null;
            timer.stop();
            finished.done.complete(null);
        }
    }

    private void cancelActive() {
        if (active != null) {
            active.done.cancel(false);
            active = null;
        }
        timer.stop();
    }
}