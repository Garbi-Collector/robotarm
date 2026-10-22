package garbi.com.robotarm.physics;

/**
 * Cuerpo rígido con forma de caja orientada. Las cajas son dinámicas; las piezas del brazo
 * y el suelo son cuerpos de masa infinita (no se mueven por fuerzas, solo por velocidad).
 */
final class Body {

    final boolean dynamic;
    final double invMass;
    final double friction;
    boolean kinematic;              // pieza del brazo: la mueve un servo, no las fuerzas

    Vec3 pos = Vec3.ZERO;
    Mat3 rot = Mat3.identity();
    Vec3 half = Vec3.ZERO;          // semiejes, a lo largo de las columnas de rot
    Vec3 invInertia = Vec3.ZERO;    // inverso de la inercia en ejes locales (diagonal)
    Vec3 vel = Vec3.ZERO;
    Vec3 ang = Vec3.ZERO;

    Body(boolean dynamic, double friction) {
        this.dynamic = dynamic;
        this.invMass = dynamic ? 1 : 0;   // masa 1: el solver no depende de la masa absoluta
        this.friction = friction;
    }

    void setShape(Vec3 half) {
        this.half = half;
        if (dynamic) {
            // caja sólida de masa 1: I = (hy²+hz²)/3 en x, y así sucesivamente (con semiejes h)
            invInertia = new Vec3(
                    3 / (half.y() * half.y() + half.z() * half.z()),
                    3 / (half.x() * half.x() + half.z() * half.z()),
                    3 / (half.x() * half.x() + half.y() * half.y()));
        }
    }

    Vec3 axis(int i) { return rot.col(i); }

    /** Vértice k (0–7) en coordenadas del mundo. */
    Vec3 corner(int k) {
        double sx = (k & 1) == 0 ? -1 : 1;
        double sy = (k & 2) == 0 ? -1 : 1;
        double sz = (k & 4) == 0 ? -1 : 1;
        return pos.add(axis(0).scale(sx * half.x()))
                .add(axis(1).scale(sy * half.y()))
                .add(axis(2).scale(sz * half.z()));
    }

    /** Punto más bajo de la caja (mayor y, porque "abajo" es +Y). */
    double lowestY() {
        return pos.y() + Math.abs(axis(0).y()) * half.x()
                + Math.abs(axis(1).y()) * half.y()
                + Math.abs(axis(2).y()) * half.z();
    }

    /** Velocidad del punto que está a r del centro. */
    Vec3 velAt(Vec3 r) { return vel.add(ang.cross(r)); }

    /** I⁻¹ · t, en coordenadas del mundo. */
    Vec3 invInertiaTimes(Vec3 t) {
        if (!dynamic) return Vec3.ZERO;
        Vec3 l = rot.mulT(t);
        return rot.mul(new Vec3(l.x() * invInertia.x(), l.y() * invInertia.y(), l.z() * invInertia.z()));
    }
}
