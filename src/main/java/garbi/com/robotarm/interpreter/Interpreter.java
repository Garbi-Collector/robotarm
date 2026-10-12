package garbi.com.robotarm.interpreter;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import garbi.com.robotarm.blocks.model.Block;
import garbi.com.robotarm.blocks.model.Program;
import garbi.com.robotarm.model.RobotArm;
import garbi.com.robotarm.model.Servo;
import garbi.com.robotarm.motion.MotionController;

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
    }

    private final RobotArm arm;
    private final MotionController motion;
    private final Object pauseLock = new Object();
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);


    private volatile Thread thread;
    private volatile boolean paused;
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

    public synchronized void run(Program program) {
        stop();
        paused = false;

        Thread t = new Thread(() -> {
            boolean completed = false;
            try {
                execute(program.getBlocks());
                completed = true;
            } catch (InterruptedException | CancellationException | CompletionException ignored) {
                // detenido por el usuario
            } catch (RuntimeException e) {
                e.printStackTrace(); // programa inválido (servo inexistente, etc.)
            } finally {
                // Si ya arrancó otra ejecución, esta no avisa
                if (thread == Thread.currentThread()) {
                    thread = null;
                    listener.onFinished(completed);
                    // si mientras tanto arrancó otra ejecución, no se baja la bandera
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

    private void execute(List<Block> blocks) throws InterruptedException {
        for (Block block : blocks) {
            checkpoint();
            listener.onBlockStart(block);
            runBlock(block);
        }
    }

    private void runBlock(Block block) throws InterruptedException {
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
                    execute(block.getChildren());
                }
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
            while (paused) pauseLock.wait(); // interrupt() lo despierta
        }
    }
}