package garbi.com.robotarm.physics;

/** Punto de contacto entre dos cuerpos. La normal apunta de a hacia b. */
final class Contact {

    final Body a, b;
    final Vec3 p, n;
    final double depth;   // > 0: penetración; < 0: separación dentro del margen de contacto
    final double mu;

    // estado del solver, válido solo durante un paso
    Vec3 ra, rb, t1, t2;
    double kN, kT1, kT2, bias;
    double accN, accT1, accT2;

    Contact(Body a, Body b, Vec3 p, Vec3 n, double depth) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.n = n;
        this.depth = depth;
        this.mu = Math.sqrt(a.friction * b.friction);
    }
}
