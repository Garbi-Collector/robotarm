package garbi.com.robotarm.physics;

import java.util.List;

/** Detección de colisiones entre cajas orientadas (SAT) y contra el suelo. */
final class Collision {

    /** Distancia (unidades) a la que dos cuerpos ya generan contacto. Evita que una caja atraviese un hueco al apoyarse. */
    static final double MARGIN = 0.05;

    private Collision() {}

    /** Suelo infinito en y = 0. Como "abajo" es +Y, un vértice con y > 0 está bajo la superficie. */
    static void floor(Body floor, Body box, List<Contact> out) {
        Vec3 up = new Vec3(0, -1, 0);   // del suelo hacia la caja
        for (int k = 0; k < 8; k++) {
            Vec3 p = box.corner(k);
            if (p.y() >= -MARGIN) out.add(new Contact(floor, box, p, up, p.y()));
        }
    }

    /** Colisión entre dos cajas orientadas. Los contactos son los vértices de cada caja que están dentro de la otra. */
    static void boxBox(Body a, Body b, List<Contact> out) {
        Vec3 t = b.pos.sub(a.pos);
        double best = Double.MAX_VALUE;
        Vec3 normal = null;
        for (int i = 0; i < 15; i++) {
            Vec3 axis = axis(a, b, i);
            if (axis == null) continue;
            double overlap = project(a, axis) + project(b, axis) - Math.abs(t.dot(axis));
            if (overlap < -MARGIN) return;           // eje separador: no hay colisión
            if (overlap < best) {
                best = overlap;
                normal = t.dot(axis) < 0 ? axis.scale(-1) : axis;   // de a hacia b
            }
        }

        // la penetración se mide a lo largo de la normal (eje de mínimo solapamiento), no hasta la cara más cercana
        int before = out.size();
        for (int k = 0; k < 8; k++) {
            Vec3 p = b.corner(k);
            if (inside(a, p)) out.add(new Contact(a, b, p, normal, best));
        }
        for (int k = 0; k < 8; k++) {
            Vec3 p = a.corner(k);
            if (inside(b, p)) out.add(new Contact(a, b, p, normal, best));
        }
        if (out.size() == before) {   // caso raro (cajas que se cruzan por aristas): un contacto en el medio
            out.add(new Contact(a, b, a.pos.add(t.scale(0.5)), normal, best));
        }
    }

    /** ¿Las dos cajas se solapan (con el margen)? No genera contactos: solo responde si hay choque. */
    static boolean intersects(Body a, Body b) {
        Vec3 t = b.pos.sub(a.pos);
        for (int i = 0; i < 15; i++) {
            Vec3 axis = axis(a, b, i);
            if (axis == null) continue;
            if (project(a, axis) + project(b, axis) - Math.abs(t.dot(axis)) <= MARGIN) return false;
        }
        return true;
    }

    /** Eje de separación número i: 3 de a, 3 de b y 9 productos cruzados. null si son paralelos. */
    private static Vec3 axis(Body a, Body b, int i) {
        if (i < 3) return a.axis(i);
        if (i < 6) return b.axis(i - 3);
        Vec3 c = a.axis((i - 6) / 3).cross(b.axis((i - 6) % 3));
        double len = c.length();
        return len < 1e-9 ? null : c.scale(1 / len);
    }

    /** Radio de la caja proyectado sobre un eje. */
    private static double project(Body o, Vec3 axis) {
        return Math.abs(axis.dot(o.axis(0))) * o.half.x()
                + Math.abs(axis.dot(o.axis(1))) * o.half.y()
                + Math.abs(axis.dot(o.axis(2))) * o.half.z();
    }

    /** ¿El punto p está dentro de la caja o, con el margen de contacto? */
    private static boolean inside(Body o, Vec3 p) {
        Vec3 d = p.sub(o.pos);
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d.dot(o.axis(i))) > o.half.get(i) + MARGIN) return false;
        }
        return true;
    }
}
