package garbi.com.robotarm.physics;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Física de las cajas, con motor propio. Unidades de la escena: 1 unidad = 1 cm; "abajo" es +Y.
 * Las cajas son cuerpos rígidos que se pueden apilar. Las piezas del brazo son cinemáticas: se mueven
 * con sus nodos y empujan las cajas. Usar solo desde el hilo de JavaFX.
 */
public class PhysicsWorld {

    public static final double FLOOR_HALF = 800;              // medio lado del suelo (unidades)

    private static final Vec3 GRAVITY = new Vec3(0, 981, 0); // 9,81 m/s² en cm/s²
    private static final double REST_OFFSET = 2;             // las cajas nuevas aparecen 2 unidades sobre el suelo
    private static final double FLOOR_TOLERANCE = 0.05;      // el brazo no puede bajar de aquí
    private static final double GRIP_PENETRATION = 0.2;      // presión mínima de cada dedo para considerar "agarre"
    private static final double FALL_LIMIT = 500;           // si una caja cae más abajo, reaparece
    private static final int MAX_CRATES = 30;
    private static final double WARM_DISTANCE = 0.5;         // distancia para considerar que un contacto es el mismo
    private static final double SLEEP_SPEED = 0.5;          // unidades/s: por debajo, la caja está quieta
    private static final double SLEEP_SPIN = 0.02;          // rad/s: por debajo, la caja no gira
    private static final double SLEEP_TIME = 0.5;           // segundos quieta antes de dormirse
    private static final double SCAN_STEP = 1;              // unidades que sube la caja nueva buscando hueco
    private static final double MAX_SPAWN_HEIGHT = 1000;    // hasta dónde se busca hueco hacia arriba
    private static final double CRATE_FRICTION = 1.0;
    private static final double FLOOR_FRICTION = 0.8;
    private static final double PART_FRICTION = 1.5;
    private static final String[] COLORS = {"#d9a05b", "#6fb3a2", "#c76b6b", "#7d8fd1", "#b08fd6"};

    /** Resultado de un paso de simulación. Las penetraciones están en unidades de la escena. */
    public static final class Contacts {
        public double armPenetration;     // brazo (sin dedos) contra cajas
        public double fingerPenetration;  // dedos contra cajas
        public boolean gripping;          // ambos dedos presionan la misma caja
    }

    private static final class Part {
        final ArmCollider collider;
        final Body body;
        final boolean movable;
        Vec3 prevPos;
        Mat3 prevRot;

        Part(ArmCollider collider, Body body, boolean movable) {
            this.collider = collider;
            this.body = body;
            this.movable = movable;
        }
    }

    private static final class Crate {
        final Body body = new Body(true, CRATE_FRICTION);
        final Box node;
        final double size, x, z;
        final Affine pose = new Affine();
        Vec3 holdPos = Vec3.ZERO;         // posición de la caja respecto a la garra, mientras está agarrada
        Mat3 holdRot = Mat3.identity();
        boolean asleep;                   // quieta: no se simula hasta que la toque algo que se mueva
        double restTime;                  // segundos seguidos quieta

        Crate(double size, double x, double z, Box node) {
            this.size = size;
            this.x = x;
            this.z = z;
            this.node = node;
            body.setShape(new Vec3(size / 2, size / 2, size / 2));
        }
    }

    private final Body floor = new Body(false, FLOOR_FRICTION);
    private final Body holderBody = new Body(false, PART_FRICTION);
    private final List<Part> parts = new ArrayList<>();
    private final List<Crate> crates = new ArrayList<>();
    private final Map<Body, Part> partByBody = new IdentityHashMap<>();
    private final Map<Body, Crate> crateByBody = new IdentityHashMap<>();
    private final Group crateLayer = new Group();
    private List<Contact> previousContacts = List.of();   // contactos del paso anterior (arranque en caliente)
    private final Node holder;            // grupo de la garra: referencia para "pegar" la caja
    private Crate held;                   // caja sostenida por la garra
    private Crate gripCandidate;          // caja que ambos dedos presionan en este paso
    private int colorIndex;

    public PhysicsWorld(List<ArmCollider> colliders) {
        Node h = null;
        for (ArmCollider c : colliders) {
            boolean movable = c.role() != ArmCollider.Role.STATIC;
            Part part = new Part(c, new Body(false, PART_FRICTION), movable);
            part.body.kinematic = movable;
            refresh(part.body, c.node());
            part.prevPos = part.body.pos;
            part.prevRot = part.body.rot;
            parts.add(part);
            partByBody.put(part.body, part);
            if (c.role() == ArmCollider.Role.FINGER_LEFT) h = c.node().getParent();
        }
        holder = h;
    }

    public Group getCrateLayer() { return crateLayer; }
    public int crateCount() { return crates.size(); }

    // ---------- cajas ----------

    public void addCrate(double x, double z, double size) {
        if (crates.size() >= MAX_CRATES) return;
        Box node = new Box(size, size, size);
        PhongMaterial m = new PhongMaterial(Color.web(COLORS[colorIndex++ % COLORS.length]));
        m.setSpecularColor(Color.gray(0.3));
        node.setMaterial(m);

        Crate c = new Crate(size, x, z, node);
        double y = freeSpawnHeight(c, crates);
        if (Double.isNaN(y)) return;      // no hay sitio libre ni arriba: no se agrega
        node.getTransforms().add(c.pose);
        crates.add(c);
        crateByBody.put(c.body, c);
        crateLayer.getChildren().add(node);
        spawn(c);
        c.body.pos = new Vec3(c.x, y, c.z);
        updatePose(c);
    }

    /**
     * Busca la altura de aparición más baja donde la caja no toca nada. Empieza en la altura normal y sube
     * hasta encontrar un hueco. Devuelve NaN si no hay ninguno dentro de MAX_SPAWN_HEIGHT.
     */
    private double freeSpawnHeight(Crate c, List<Crate> others) {
        for (Part p : parts) refresh(p.body, p.collider.node());
        followHolder();
        for (double y = spawnY(c); y > -MAX_SPAWN_HEIGHT; y -= SCAN_STEP) {
            c.body.pos = new Vec3(c.x, y, c.z);
            c.body.rot = Mat3.identity();
            if (isClear(c.body, c, others)) return y;
        }
        return Double.NaN;
    }

    /** ¿La caja b cabe aquí sin tocar el suelo, el brazo ni otra caja de la lista? */
    private boolean isClear(Body b, Crate self, List<Crate> others) {
        if (b.lowestY() > 0) return false;                       // bajo el suelo
        for (Part p : parts) {
            if (Collision.intersects(b, p.body)) return false;
        }
        for (Crate o : others) {
            if (o != self && Collision.intersects(b, o.body)) return false;
        }
        return true;
    }

    private static double spawnY(Crate c) {
        return -(c.size / 2 + REST_OFFSET);
    }

    /** Devuelve todas las cajas a su posición inicial. */
    public void resetCrates() {
        // se colocan una a una, buscando hueco entre las ya colocadas, para no superponerlas
        List<Crate> placed = new ArrayList<>();
        for (Crate c : crates) {
            spawn(c);
            double y = freeSpawnHeight(c, placed);
            c.body.pos = new Vec3(c.x, Double.isNaN(y) ? spawnY(c) : y, c.z);
            updatePose(c);
            placed.add(c);
        }
    }

    public void clearCrates() {
        crates.clear();
        crateByBody.clear();
        crateLayer.getChildren().clear();
        held = null;
        gripCandidate = null;
    }

    private void spawn(Crate c) {
        if (c == held) held = null;
        if (c == gripCandidate) gripCandidate = null;
        c.body.pos = new Vec3(c.x, spawnY(c), c.z);
        c.body.rot = Mat3.identity();
        c.body.vel = Vec3.ZERO;
        c.body.ang = Vec3.ZERO;
        c.asleep = false;
        c.restTime = 0;
        updatePose(c);
    }

    // ---------- simulación ----------

    /** ¿Alguna pieza del brazo quedaría bajo el suelo, o la caja agarrada demasiado abajo? Se llama antes de simular. */
    public boolean armBelowFloor() {
        for (Part p : parts) {
            if (!p.movable) continue;
            refresh(p.body, p.collider.node());
            if (p.body.lowestY() > FLOOR_TOLERANCE) return true;
        }
        followHolder();
        return held != null && held.body.lowestY() > 1.0;
    }

    /** Avanza la física dt segundos y devuelve los contactos del brazo con las cajas. */
    public Contacts step(double dt) {
        for (Part p : parts) {
            refresh(p.body, p.collider.node());
            if (p.movable) kinematicVelocity(p, dt);
        }
        followHolder();

        List<Contact> contacts = collect();
        wakeTouching(contacts);
        Contacts result = measure(contacts);
        for (Crate c : crates) {
            if (c != held && !c.asleep) c.body.vel = c.body.vel.add(GRAVITY.scale(dt));
        }
        warmStart(contacts);
        ContactSolver.prepare(contacts, dt);
        ContactSolver.solve(contacts);
        previousContacts = contacts;
        for (Crate c : crates) {
            if (c != held && !c.asleep) {
                integrate(c.body, dt);
                updateSleep(c, dt);
            }
        }
        return result;
    }

    /** Una caja dormida se despierta si la toca otra caja que se mueve o una pieza del brazo. */
    private void wakeTouching(List<Contact> cs) {
        for (Contact c : cs) {
            wakeIfTouched(c.a, c.b);
            wakeIfTouched(c.b, c.a);
        }
    }

    private void wakeIfTouched(Body maybeSleeping, Body other) {
        Crate c = crateByBody.get(maybeSleeping);
        if (c == null || !c.asleep) return;
        Crate o = crateByBody.get(other);
        if (other.kinematic || (o != null && !o.asleep)) c.asleep = false;
    }

    /** Una caja quieta durante un rato deja de simularse. Así no queda ni el mínimo temblor ni deriva. */
    private static void updateSleep(Crate c, double dt) {
        boolean still = c.body.vel.length() < SLEEP_SPEED && c.body.ang.length() < SLEEP_SPIN;
        c.restTime = still ? c.restTime + dt : 0;
        if (c.restTime > SLEEP_TIME) {
            c.asleep = true;
            c.body.vel = Vec3.ZERO;
            c.body.ang = Vec3.ZERO;
        }
    }

    /** Actualiza los nodos 3D de las cajas. Una vez por frame. */
    public void syncVisuals() {
        followHolder();
        for (Crate c : crates) {
            updatePose(c);
            if (c != held && c.body.pos.y() > FALL_LIMIT) spawn(c);   // se cayó del suelo
        }
    }

    /**
     * Contactos del paso. Los del suelo van al final: el solver de impulsos resuelve en orden, así que lo
     * último que aplica es lo que queda cumplido. Si el brazo empuja una caja contra el suelo, el suelo debe
     * ganar; si no, la caja se hunde en el suelo.
     */
    private List<Contact> collect() {
        List<Contact> out = new ArrayList<>();
        List<Contact> ground = new ArrayList<>();
        for (int i = 0; i < crates.size(); i++) {
            Crate c = crates.get(i);
            if (c == held) continue;
            Collision.floor(floor, c.body, ground);
            for (Part p : parts) Collision.boxBox(p.body, c.body, out);
            for (int j = i + 1; j < crates.size(); j++) {
                Crate d = crates.get(j);
                if (d != held) Collision.boxBox(c.body, d.body, out);
            }
        }
        out.addAll(ground);
        return out;
    }

    /** Mide penetraciones del brazo y detecta si la garra sujeta una caja. */
    private Contacts measure(List<Contact> cs) {
        Contacts r = new Contacts();
        Map<Body, double[]> fingers = new IdentityHashMap<>();   // por caja: profundidad [izquierdo, derecho]
        Map<Body, Double> arm = new IdentityHashMap<>();         // por caja: profundidad máxima del brazo

        for (Contact c : cs) {
            Part part = partByBody.get(c.a);
            Body other = c.b;
            if (part == null) {
                part = partByBody.get(c.b);
                other = c.a;
            }
            if (part == null || !part.movable) continue;

            switch (part.collider.role()) {
                case FINGER_LEFT, FINGER_RIGHT -> {
                    double[] f = fingers.computeIfAbsent(other, k -> new double[2]);
                    int side = part.collider.role() == ArmCollider.Role.FINGER_LEFT ? 0 : 1;
                    f[side] = Math.max(f[side], c.depth);
                    r.fingerPenetration = Math.max(r.fingerPenetration, c.depth);
                }
                default -> arm.merge(other, c.depth, Math::max);
            }
        }

        gripCandidate = null;
        for (Map.Entry<Body, double[]> e : fingers.entrySet()) {
            double[] f = e.getValue();
            if (f[0] >= GRIP_PENETRATION && f[1] >= GRIP_PENETRATION) {
                r.gripping = true;
                gripCandidate = crateByBody.get(e.getKey());
            }
        }
        for (Map.Entry<Body, Double> e : arm.entrySet()) {
            if (crateByBody.get(e.getKey()) == gripCandidate) continue;   // la caja que se agarra no cuenta como choque
            r.armPenetration = Math.max(r.armPenetration, e.getValue());
        }
        return r;
    }

    /**
     * Copia los impulsos del paso anterior a los contactos que siguen en el mismo sitio entre los mismos cuerpos.
     */
    private void warmStart(List<Contact> cs) {
        for (Contact c : cs) {
            for (Contact old : previousContacts) {
                if (old.a == c.a && old.b == c.b && old.p.sub(c.p).length() < WARM_DISTANCE) {
                    c.accN = old.accN;
                    c.accT1 = old.accT1;
                    c.accT2 = old.accT2;
                    break;
                }
            }
        }
    }

    /** Velocidad lineal y angular de una pieza cinemática, a partir de su movimiento en este paso. */
    private static void kinematicVelocity(Part p, double dt) {
        Body b = p.body;
        b.vel = b.pos.sub(p.prevPos).scale(1 / dt);
        Mat3 dR = b.rot.mul(p.prevRot.transpose());   // pequeño giro en ejes del mundo
        b.ang = new Vec3(
                (dR.get(2, 1) - dR.get(1, 2)) / (2 * dt),
                (dR.get(0, 2) - dR.get(2, 0)) / (2 * dt),
                (dR.get(1, 0) - dR.get(0, 1)) / (2 * dt));
        p.prevPos = b.pos;
        p.prevRot = b.rot;
    }

    private static void integrate(Body b, double dt) {
        b.pos = b.pos.add(b.vel.scale(dt));
        b.rot = Mat3.rotation(b.ang.scale(dt)).mul(b.rot).orthonormalized();
    }

    // ---------- agarre ----------

    public boolean isHolding() { return held != null; }

    /** Pega a la garra la caja que ambos dedos presionan. Devuelve false si no hay ninguna. */
    public boolean grab() {
        if (held != null || gripCandidate == null || holder == null) return false;
        refresh(holderBody, holder);
        Crate c = gripCandidate;
        Mat3 inv = holderBody.rot.transpose();
        c.holdRot = inv.mul(c.body.rot);                             // garra⁻¹ × caja
        c.holdPos = inv.mul(c.body.pos.sub(holderBody.pos));
        c.body.vel = Vec3.ZERO;
        c.body.ang = Vec3.ZERO;
        held = c;
        gripCandidate = null;
        return true;
    }

    /** Devuelve la caja a la simulación, quieta, para que caiga. */
    public void release() {
        if (held == null) return;
        held.body.vel = Vec3.ZERO;
        held.body.ang = Vec3.ZERO;
        held.asleep = false;
        held.restTime = 0;
        held = null;
    }

    /** Coloca la caja sostenida donde le corresponde según la garra. */
    private void followHolder() {
        if (held == null || holder == null) return;
        refresh(holderBody, holder);
        held.body.rot = holderBody.rot.mul(held.holdRot);
        held.body.pos = holderBody.pos.add(holderBody.rot.mul(held.holdPos));
        held.body.vel = Vec3.ZERO;
        held.body.ang = Vec3.ZERO;
    }

    // ---------- geometría y conversiones ----------

    private static void updatePose(Crate c) {
        Mat3 r = c.body.rot;
        Vec3 p = c.body.pos;
        c.pose.setToTransform(
                r.get(0, 0), r.get(0, 1), r.get(0, 2), p.x(),
                r.get(1, 0), r.get(1, 1), r.get(1, 2), p.y(),
                r.get(2, 0), r.get(2, 1), r.get(2, 2), p.z());
    }

    /**
     * Copia posición, orientación y tamaño de un nodo de JavaFX al cuerpo. Los nodos que no son
     * caja ni cilindro (como el grupo de la garra) solo aportan la pose.
     * Un cilindro se aproxima por la caja que lo envuelve.
     */
    private static void refresh(Body body, Node n) {
        Transform t = n.getLocalToSceneTransform();
        Vec3 c0 = new Vec3(t.getMxx(), t.getMyx(), t.getMzx());
        Vec3 c1 = new Vec3(t.getMxy(), t.getMyy(), t.getMzy());
        Vec3 c2 = new Vec3(t.getMxz(), t.getMyz(), t.getMzz());
        body.pos = new Vec3(t.getTx(), t.getTy(), t.getTz());
        body.rot = Mat3.fromColumns(c0.normalized(), c1.normalized(), c2.normalized());
        if (n instanceof Box || n instanceof Cylinder) {
            Vec3 local = localHalf(n);
            body.setShape(new Vec3(
                    local.x() * c0.length(), local.y() * c1.length(), local.z() * c2.length()));
        }
    }

    /** Semiejes de la caja que envuelve al nodo, en su sistema local. */
    private static Vec3 localHalf(Node n) {
        if (n instanceof Box b) return new Vec3(b.getWidth() / 2, b.getHeight() / 2, b.getDepth() / 2);
        if (n instanceof Cylinder c) return new Vec3(c.getRadius(), c.getHeight() / 2, c.getRadius());
        throw new IllegalArgumentException("Collider no soportado: " + n.getClass());
    }
}
