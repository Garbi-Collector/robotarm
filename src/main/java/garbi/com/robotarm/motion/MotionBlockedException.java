package garbi.com.robotarm.motion;

/** El brazo chocó con el suelo o con algo que no cede, y el movimiento se detuvo. */
public class MotionBlockedException extends RuntimeException {
    public MotionBlockedException(String message) { super(message); }
}