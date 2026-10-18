package garbi.com.robotarm.physics;


import java.util.Collections;
import java.util.Set;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CylinderShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.MotionState;
import com.bulletphysics.linearmath.Transform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Affine;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Física de las cajas. Las unidades de la escena se convierten a metros (1 unidad = 1 cm).
 * Usar solo desde el hilo de JavaFX.
 *
 * Esto funciona porque ni el grupo raíz ni "world" tienen transformaciones: la transformación
 * local->escena de un nodo es directamente su posición en el mundo.
 */
public class PhysicsWorld {

    public static final double FLOOR_HALF = 800;              // medio lado del suelo (unidades)
    private static final float REST_SPEED = 0.05f;   // m/s: por debajo, una caja apoyada se considera quieta
    private final Set<Crate> touched = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Vector3f vel = new Vector3f();
    private static final float S = 0.01f;                     // unidades -> metros
    private static final double SKIN = 0.5;                   // el brazo físico es 0.5 u más "gordo" que el visual
    private static final float MARGIN = 0.002f;               // margen de colisión de Bullet (m)
    private static final double FLOOR_TOLERANCE = 0.05;
    private static final double GRIP_PENETRATION = 0.2;       // presión mínima de cada dedo para considerar "agarre"
    private static final double FALL_LIMIT = 500;             // si una caja cae más abajo, reaparece
    private static final int MAX_CRATES = 30;
    private static final String[] COLORS = {"#d9a05b", "#6fb3a2", "#c76b6b", "#7d8fd1", "#b08fd6"};
    private Crate held;                 // caja sostenida por la garra (fuera de la simulación)
    private Crate gripCandidate;        // caja que ambos dedos presionan en este paso
    private final Transform heldOffset = new Transform();
    private Node holder;                // grupo de la garra: referencia para "pegar" la caja

    /** Resultado de un paso de simulación. Las penetraciones están en unidades de la escena. */
    public static final class Contacts {
        public double armPenetration;     // brazo (sin dedos) contra cajas
        public double fingerPenetration;  // dedos contra cajas
        public boolean gripping;          // ambos dedos presionan la misma caja
    }

    private static final class PoseState extends MotionState {

        final Transform t = new Transform();

        @Override
        public Transform getWorldTransform(Transform out) {
            out.set(t);
            return out;
        }

        @Override
        public void setWorldTransform(Transform in) {
            // Cinemático: la posición la controla JavaFX.
        }
    }

    private static final class Part {
        final ArmCollider collider;
        final PoseState state;
        final boolean movable;
        Part(ArmCollider collider, PoseState state, boolean movable) {
            this.collider = collider; this.state = state; this.movable = movable;
        }
    }

    private static final class Crate {
        final Box node;
        final double size, x, z;
        final Affine pose = new Affine();
        RigidBody body;
        Crate(double size, double x, double z, Box node) {
            this.size = size; this.x = x; this.z = z; this.node = node;
        }
    }

    private final CollisionDispatcher dispatcher;
    private final DiscreteDynamicsWorld world;
    private final List<Part> parts = new ArrayList<>();
    private final List<Crate> crates = new ArrayList<>();
    private final Map<Object, Part> partOf = new IdentityHashMap<>();
    private final Map<Object, Crate> crateOf = new IdentityHashMap<>();
    private final Group crateLayer = new Group();
    private final Transform tmp = new Transform();
    private int colorIndex;

    public PhysicsWorld(List<ArmCollider> colliders) {
        DefaultCollisionConfiguration config = new DefaultCollisionConfiguration();
        dispatcher = new CollisionDispatcher(config);
        world = new DiscreteDynamicsWorld(dispatcher, new DbvtBroadphase(),
                new SequentialImpulseConstraintSolver(), config);
        world.setGravity(new Vector3f(0, 9.81f, 0));      // en JavaFX "abajo" es +Y
        world.getSolverInfo().numIterations = 30;          // más iteraciones = agarre más estable

        addFloor();
        for (ArmCollider c : colliders) addPart(c);
        for (ArmCollider c : colliders) {
            if (c.role() == ArmCollider.Role.FINGER_LEFT) holder = c.node().getParent();
        }
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
        node.getTransforms().add(c.pose);
        crates.add(c);
        crateLayer.getChildren().add(node);
        spawn(c);
    }

    /** Devuelve todas las cajas a su posición inicial. */
    public void resetCrates() { for (Crate c : crates) spawn(c); }

    public void clearCrates() {
        for (Crate c : crates) {
            if (c != held) world.removeRigidBody(c.body);
            crateOf.remove(c.body);
        }
        held = null;
        crates.clear();
        crateLayer.getChildren().clear();
    }

    private void spawn(Crate c) {
        if (c.body != null) {
            if (c == held) held = null;
            else world.removeRigidBody(c.body);
            crateOf.remove(c.body);
        }
        Transform t = new Transform();
        t.setIdentity();
        t.origin.set((float) (c.x * S), (float) (-(c.size / 2 + 2) * S), (float) (c.z * S));

        float h = (float) (c.size / 2 * S);
        BoxShape shape = new BoxShape(new Vector3f(h, h, h));
        shape.setMargin(MARGIN);

        RigidBody b = body(0.2f, shape, new DefaultMotionState(t));
        b.setFriction(1.0f);
        b.setRestitution(0f);
        b.setDamping(0.2f, 0.4f);
        b.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        world.addRigidBody(b);

        c.body = b;
        crateOf.put(b, c);
        updatePose(c);
    }

    // ---------- simulación ----------

    /** ¿Alguna pieza del brazo quedaría bajo el suelo? Se llama antes de simular. */
    public boolean armBelowFloor() {
        for (Part p : parts) {
            if (p.movable && lowestY(p.collider.node()) > FLOOR_TOLERANCE) {
                System.out.printf("[SUELO] %s (%s) bajo el suelo: %.2f%n", p.collider.role(),
                        p.collider.node().getClass().getSimpleName(), lowestY(p.collider.node()));
                return true;
            }
        }
        if (held != null && heldLowestY() > 1.0) return true;
        return false;
    }

    /** Copia la pose actual del brazo, avanza la física dt segundos y devuelve los contactos. */
    public Contacts step(double dt) {
        for (Part p : parts) {
            if (p.movable) fill(p.state.t, p.collider.node().getLocalToSceneTransform());
        }
        followHolder();
        world.stepSimulation((float) dt, 1, (float) dt);
        Contacts result = readContacts();
        settleCrates();
        return result;
    }

    /** Actualiza los nodos 3D de las cajas. Una vez por frame. */
    public void syncVisuals() {
        followHolder();
        for (Crate c : crates) {
            updatePose(c);
            if (c != held && tmp.origin.y / S > FALL_LIMIT) spawn(c); // se cayó de la mesa
        }
    }

    private Contacts readContacts() {
        touched.clear();
        Contacts result = new Contacts();
        Map<Crate, double[]> fingers = new IdentityHashMap<>();
        Map<Crate, Double> armPen = new IdentityHashMap<>();

        int n = dispatcher.getNumManifolds();
        for (int i = 0; i < n; i++) {
            PersistentManifold m = dispatcher.getManifoldByIndexInternal(i);
            Object a = m.getBody0(), b = m.getBody1();

            Part part = partOf.containsKey(a) ? partOf.get(a) : partOf.get(b);
            Crate crate = crateOf.containsKey(a) ? crateOf.get(a) : crateOf.get(b);
            if (part == null || crate == null || !part.movable || crate == held) continue;
            touched.add(crate);

            double pen = 0;
            for (int j = 0; j < m.getNumContacts(); j++) {
                pen = Math.max(pen, -m.getContactPoint(j).getDistance() / S); // distancia negativa = penetración
            }
            //temporal------------------------------------------------
            if (pen > 1.0) {
                System.out.printf("[CONTACTO] %s (%s) vs caja en (%.0f, %.0f, %.0f) pen=%.2f%n",
                        part.collider.role(), part.collider.node().getClass().getSimpleName(),
                        crate.node.getLocalToSceneTransform().getTx(),
                        crate.node.getLocalToSceneTransform().getTy(),
                        crate.node.getLocalToSceneTransform().getTz(), pen);
            }
            //---------------------------------------------------------
            switch (part.collider.role()) {
                case FINGER_LEFT, FINGER_RIGHT -> {
                    double[] f = fingers.computeIfAbsent(crate, k -> new double[2]);
                    int side = part.collider.role() == ArmCollider.Role.FINGER_LEFT ? 0 : 1;
                    f[side] = Math.max(f[side], pen);
                    result.fingerPenetration = Math.max(result.fingerPenetration, pen);
                }
                default -> armPen.merge(crate, pen, Math::max);
            }
        }

        gripCandidate = null;
        for (Map.Entry<Crate, double[]> e : fingers.entrySet()) {
            double[] f = e.getValue();
            if (f[0] >= GRIP_PENETRATION && f[1] >= GRIP_PENETRATION) {
                result.gripping = true;
                gripCandidate = e.getKey();
            }
        }
        for (Map.Entry<Crate, Double> e : armPen.entrySet()) {
            if (e.getKey() == gripCandidate) continue; // la caja que se está agarrando no cuenta como choque
            result.armPenetration = Math.max(result.armPenetration, e.getValue());
        }
        return result;
    }

    // ---------- construcción ----------

    private void addFloor() {
        BoxShape shape = new BoxShape(new Vector3f((float) (FLOOR_HALF * S), 0.05f, (float) (FLOOR_HALF * S)));
        shape.setMargin(MARGIN);
        Transform t = new Transform();
        t.setIdentity();
        t.origin.set(0, 0.05f, 0); // cara superior en y = 0 (abajo es +Y)
        RigidBody floor = body(0f, shape, new DefaultMotionState(t));
        floor.setFriction(0.8f);
        world.addRigidBody(floor);
    }

    private void addPart(ArmCollider c) {
        boolean fixed = c.role() == ArmCollider.Role.STATIC;
        CollisionShape shape = shapeFor(c.node(), fixed ? 0 : SKIN);

        PoseState state = new PoseState();
        fill(state.t, c.node().getLocalToSceneTransform());

        RigidBody b = body(0f, shape, state);
        b.setFriction(1.5f);
        if (!fixed) {
            b.setCollisionFlags(b.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
            b.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        }
        world.addRigidBody(b);

        Part part = new Part(c, state, !fixed);
        parts.add(part);
        partOf.put(b, part);
    }

    private static CollisionShape shapeFor(Node n, double skin) {
        CollisionShape shape;
        if (n instanceof Box b) {
            shape = new BoxShape(new Vector3f(
                    (float) ((b.getWidth() / 2 + skin) * S),
                    (float) ((b.getHeight() / 2 + skin) * S),
                    (float) ((b.getDepth() / 2 + skin) * S)));
        } else if (n instanceof Cylinder c) {
            float r = (float) ((c.getRadius() + skin) * S);
            shape = new CylinderShape(new Vector3f(r, (float) ((c.getHeight() / 2 + skin) * S), r));
        } else {
            throw new IllegalArgumentException("Collider no soportado: " + n.getClass());
        }
        shape.setMargin(MARGIN);
        return shape;
    }

    private static RigidBody body(float mass, CollisionShape shape, MotionState state) {
        Vector3f inertia = new Vector3f(0, 0, 0);
        if (mass > 0) shape.calculateLocalInertia(mass, inertia);
        return new RigidBody(new RigidBodyConstructionInfo(mass, state, shape, inertia));
    }

    // ---------- conversiones y geometría ----------

    private void updatePose(Crate c) {
        c.body.getWorldTransform(tmp);
        var m = tmp.basis;
        var o = tmp.origin;
        c.pose.setToTransform(
                m.m00, m.m01, m.m02, o.x / S,
                m.m10, m.m11, m.m12, o.y / S,
                m.m20, m.m21, m.m22, o.z / S);
    }

    private static void fill(
            Transform out,
            javafx.scene.transform.Transform t
    ) {
        out.basis.m00 = (float) t.getMxx();
        out.basis.m01 = (float) t.getMxy();
        out.basis.m02 = (float) t.getMxz();

        out.basis.m10 = (float) t.getMyx();
        out.basis.m11 = (float) t.getMyy();
        out.basis.m12 = (float) t.getMyz();

        out.basis.m20 = (float) t.getMzx();
        out.basis.m21 = (float) t.getMzy();
        out.basis.m22 = (float) t.getMzz();

        out.origin.set(
                (float) (t.getTx() * S),
                (float) (t.getTy() * S),
                (float) (t.getTz() * S)
        );
    }

    /** Punto más bajo (mayor Y) de la pieza en coordenadas del mundo. */
    private static double lowestY(Node n) {
        javafx.scene.transform.Transform t = n.getLocalToSceneTransform();
        if (n instanceof Box b) {
            double hx = b.getWidth() / 2, hy = b.getHeight() / 2, hz = b.getDepth() / 2;
            double max = -1e9;
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sy = -1; sy <= 1; sy += 2)
                    for (int sz = -1; sz <= 1; sz += 2)
                        max = Math.max(max, t.transform(sx * hx, sy * hy, sz * hz).getY());
            return max;
        }
        if (n instanceof Cylinder c) {
            double ay = t.deltaTransform(0, 1, 0).getY();
            return t.getTy() + Math.abs(ay) * c.getHeight() / 2
                    + c.getRadius() * Math.sqrt(Math.max(0, 1 - ay * ay));
        }
        return -1e9;
    }

    public boolean isHolding() { return held != null; }

    /** Pega a la garra la caja que ambos dedos presionan. Devuelve false si no hay ninguna. */
    public boolean grab() {
        if (held != null || gripCandidate == null || holder == null) return false;
        Crate c = gripCandidate;

        Transform h = new Transform();
        fill(h, holder.getLocalToSceneTransform());
        Transform crateT = new Transform();
        c.body.getWorldTransform(crateT);

        heldOffset.set(h);          // offset = garra⁻¹ × caja
        heldOffset.inverse();
        heldOffset.mul(crateT);

        world.removeRigidBody(c.body);
        held = c;
        return true;
    }

    /** Devuelve la caja a la simulación, quieta, para que caiga. */
    public void release() {
        if (held == null) return;
        Crate c = held;
        held = null;

        Transform t = new Transform();
        c.body.getWorldTransform(t);
        c.body.setCenterOfMassTransform(t);
        c.body.setLinearVelocity(new Vector3f(0, 0, 0));
        c.body.setAngularVelocity(new Vector3f(0, 0, 0));
        c.body.clearForces();
        world.addRigidBody(c.body);
        c.body.activate(true);
    }

    /** Coloca la caja sostenida donde le corresponde según la garra. */
    private void followHolder() {
        if (held == null || holder == null) return;
        Transform h = new Transform();
        fill(h, holder.getLocalToSceneTransform());
        h.mul(heldOffset);
        held.body.setWorldTransform(h);
    }

    /** Punto más bajo (mayor Y) de la caja sostenida, en unidades de la escena. */
    private double heldLowestY() {
        Transform t = new Transform();
        held.body.getWorldTransform(t);
        double h = held.size / 2;
        return t.origin.y / S + h * (Math.abs(t.basis.m10) + Math.abs(t.basis.m11) + Math.abs(t.basis.m12));
    }
    /** Frena la deriva lateral de las cajas apoyadas que ninguna pieza del brazo está tocando. */
    private void settleCrates() {
        for (Crate c : crates) {
            if (c == held || touched.contains(c)) continue;
            c.body.getLinearVelocity(vel);
            if (Math.abs(vel.y) < REST_SPEED && Math.hypot(vel.x, vel.z) < REST_SPEED) {
                c.body.setLinearVelocity(new Vector3f(0, vel.y, 0));
            }
        }
    }
}