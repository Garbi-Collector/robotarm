package garbi.com.robotarm.interpreter;

import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.MethodDef;
import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import garbi.com.robotarm.motion.MotionController;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/** Ejecuta un Program sobre el brazo en un hilo propio. */
public class Interpreter {

    /** Los callbacks se invocan desde el hilo del intérprete: la UI debe usar Platform.runLater. */
    public interface Listener {
        void onBlockStart(Block block);
        void onFinished(boolean completed);
        default void onError(String message) {}
    }

    private static final int MAX_DEPTH = 16; // protección contra llamadas anidadas infinitas

    private final RobotArm arm;
    private final MotionController motion;
    private final Object pauseLock = new Object();
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);

    private volatile Thread thread;
    private volatile boolean paused;
    private volatile Program program;
    private volatile Listener listener = new Listener() {
        public void onBlockStart(Block block) {}
        public void onFinished(boolean completed) {}
    };

    public Interpreter(RobotArm arm, MotionController motion) {
        this.arm = arm;
        this.motion = motion;
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isRunning() { return thread != null; }
    public boolean isPaused() { return paused; }

    /** Observable desde la UI: true mientras hay un programa en ejecución. */
    public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }

    public synchronized void run(Program p) {
        stop();
        this.program = p;
        paused = false;

        Thread t = new Thread(() -> {
            boolean completed = false;
            try {
                execute(p.getBlocks(), 0);
                completed = true;
            } catch (InterruptedException | CancellationException | CompletionException ignored) {
                // detenido por el usuario
            } catch (RuntimeException e) {
                listener.onError(e.getMessage() != null ? e.getMessage() : e.toString());
            } finally {
                if (thread == Thread.currentThread()) {
                    thread = null;
                    listener.onFinished(completed);
                    Platform.runLater(() -> { if (thread == null) running.set(false); });
                }
            }
        }, "interpreter");
        t.setDaemon(true);
        thread = t;
        if (Platform.isFxApplicationThread()) running.set(true);
        else Platform.runLater(() -> running.set(true));
        t.start();
    }

    public void pause() { paused = true; }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public void stop() {
        Thread t = thread;
        if (t != null) t.interrupt();
        motion.stop();
    }

    // ---------- ejecución ----------

    private void execute(List<Block> blocks, int depth) throws InterruptedException {
        for (Block block : blocks) {
            checkpoint();
            listener.onBlockStart(block);
            runBlock(block, depth);
        }
    }

    private void runBlock(Block block, int depth) throws InterruptedException {
        switch (block.getType()) {
            case MOVE_SERVO -> move(Map.of(arm.byName(block.getServo()), block.getValue()));
            case OPEN_GRIPPER -> move(Map.of(arm.gripper(), arm.gripper().getMax()));
            case CLOSE_GRIPPER -> move(Map.of(arm.gripper(), arm.gripper().getMin()));
            case HOME -> {
                Map<Servo, Double> targets = new LinkedHashMap<>();
                arm.getServos().forEach(s -> targets.put(s, s.getHome()));
                move(targets);
            }
            case WAIT -> {
                double speed = Math.max(0.05, motion.speedFactorProperty().get());
                Thread.sleep((long) (block.getValue() * 1000 / speed));
            }
            case REPEAT -> {
                for (int i = 0; i < (int) block.getValue(); i++) {
                    execute(block.getChildren(), depth);
                }
            }
            case CALL_METHOD -> {
                if (block.getMethod() == null) {
                    throw new IllegalStateException("Hay un bloque «Método» sin método elegido.");
                }
                MethodDef def = program.findMethod(block.getMethod());
                if (def == null) {
                    throw new IllegalStateException("El método «" + block.getMethod() + "» no existe en este proyecto.");
                }
                if (depth >= MAX_DEPTH) {
                    throw new IllegalStateException("Demasiadas llamadas anidadas de métodos.");
                }
                execute(def.getBlocks(), depth + 1);
            }
        }
    }

    private void move(Map<Servo, Double> targets) {
        motion.moveTo(targets).join(); // si stop() lo cancela, lanza CancellationException
    }

    /** Punto de control entre bloques: respeta detener y pausar. */
    private void checkpoint() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        synchronized (pauseLock) {
            while (paused) pauseLock.wait();
        }
    }
}