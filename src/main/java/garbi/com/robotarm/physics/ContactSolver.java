package garbi.com.robotarm.physics;

import java.util.List;

/** Solver de impulsos secuenciales: no deja que los cuerpos se atraviesen y aplica fricción de Coulomb. */
final class ContactSolver {

    static final int ITERATIONS = 40;
    private static final double BETA = 0.2;     // fracción de la penetración que se corrige en cada paso
    private static final double SLOP = 0.05;    // penetración tolerada sin corrección (unidades)

    private ContactSolver() {}

    /**
     * Prepara los contactos. Los impulsos acumulados (accN, accT1, accT2) ya traen el valor del paso anterior
     * (arranque en caliente): se aplican de antemano, así el solver converge en pocas iteraciones.
     */
    static void prepare(List<Contact> cs, double dt) {
        for (Contact c : cs) {
            c.ra = c.p.sub(c.a.pos);
            c.rb = c.p.sub(c.b.pos);
            c.t1 = tangent(c.n);
            c.t2 = c.n.cross(c.t1);
            c.kN = effectiveMass(c, c.n);
            c.kT1 = effectiveMass(c, c.t1);
            c.kT2 = effectiveMass(c, c.t2);
            // sin corrección de posición con el brazo: una pieza cinemática no puede retroceder, y empujar la caja
            // contra el suelo para "arreglar" la penetración produce impulsos que nunca se resuelven
            boolean correctable = !c.a.kinematic && !c.b.kinematic;
            c.bias = correctable && c.depth > SLOP ? BETA * (c.depth - SLOP) / dt : 0;
            apply(c, c.n.scale(c.accN).add(c.t1.scale(c.accT1)).add(c.t2.scale(c.accT2)));
        }
    }

    static void solve(List<Contact> cs) {
        for (int it = 0; it < ITERATIONS; it++) {
            for (Contact c : cs) solveOne(c);
        }
    }

    private static void solveOne(Contact c) {
        if (c.kN > 1e-12) {
            // la velocidad relativa a lo largo de la normal debe ser al menos "bias" (corrige la penetración)
            double vn = relVel(c).dot(c.n);
            double before = c.accN;
            c.accN = Math.max(before + (c.bias - vn) / c.kN, 0);   // impulso normal solo empuja, nunca tira
            apply(c, c.n.scale(c.accN - before));
        }
        double limit = c.mu * c.accN;
        c.accT1 = friction(c, c.t1, c.kT1, c.accT1, limit);
        c.accT2 = friction(c, c.t2, c.kT2, c.accT2, limit);
    }

    /** Impulso de fricción, limitado por la fuerza normal (cono de Coulomb). */
    private static double friction(Contact c, Vec3 t, double k, double acc, double limit) {
        if (k <= 1e-12) return acc;
        double next = acc - relVel(c).dot(t) / k;
        next = Math.max(-limit, Math.min(limit, next));
        apply(c, t.scale(next - acc));
        return next;
    }

    private static Vec3 relVel(Contact c) {
        return c.b.velAt(c.rb).sub(c.a.velAt(c.ra));
    }

    /** Aplica un impulso P: a recibe -P y b recibe +P, en el punto de contacto. */
    private static void apply(Contact c, Vec3 p) {
        c.a.vel = c.a.vel.sub(p.scale(c.a.invMass));
        c.a.ang = c.a.ang.sub(c.a.invInertiaTimes(c.ra.cross(p)));
        c.b.vel = c.b.vel.add(p.scale(c.b.invMass));
        c.b.ang = c.b.ang.add(c.b.invInertiaTimes(c.rb.cross(p)));
    }

    /** Masa efectiva en la dirección dir: cuánto cambia la velocidad relativa por unidad de impulso. */
    private static double effectiveMass(Contact c, Vec3 dir) {
        return massTerm(c.a, c.ra, dir) + massTerm(c.b, c.rb, dir);
    }

    private static double massTerm(Body x, Vec3 r, Vec3 dir) {
        if (x.invMass == 0) return 0;
        return x.invMass + dir.dot(x.invInertiaTimes(r.cross(dir)).cross(r));
    }

    private static Vec3 tangent(Vec3 n) {
        Vec3 helper = Math.abs(n.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return helper.cross(n).normalized();
    }
}
