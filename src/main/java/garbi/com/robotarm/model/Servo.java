package garbi.com.robotarm.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/** Un servo: ángulo actual, límites y velocidad máxima. */
public class Servo {

    private final String name;
    private final double min;
    private final double max;
    private final double home;
    private final double maxSpeed; // grados por segundo
    private final DoubleProperty angle = new SimpleDoubleProperty();

    public Servo(String name, double min, double max, double home, double maxSpeed) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.home = home;
        this.maxSpeed = maxSpeed;
        this.angle.set(home);
    }

    public String getName() { return name; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getHome() { return home; }
    public double getMaxSpeed() { return maxSpeed; }

    public DoubleProperty angleProperty() { return angle; }
    public double getAngle() { return angle.get(); }

    /** Fija el ángulo respetando los límites del servo. */
    public void setAngle(double value) {
        angle.set(Math.max(min, Math.min(max, value)));
    }

    public void reset() { setAngle(home); }
}