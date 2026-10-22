package garbi.com.robotarm.physics;

/** Matriz 3×3 inmutable (fila mayor). Se usa como matriz de rotación. */
final class Mat3 {

    private final double[] m;

    private Mat3(double[] m) { this.m = m; }

    static Mat3 identity() {
        return new Mat3(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1});
    }

    /** Matriz cuyas columnas son c0, c1, c2. */
    static Mat3 fromColumns(Vec3 c0, Vec3 c1, Vec3 c2) {
        return new Mat3(new double[]{
                c0.x(), c1.x(), c2.x(),
                c0.y(), c1.y(), c2.y(),
                c0.z(), c1.z(), c2.z()});
    }

    /** Rotación de ángulo |w| alrededor del eje w (fórmula de Rodrigues). */
    static Mat3 rotation(Vec3 w) {
        double theta = w.length();
        if (theta < 1e-12) return identity();
        Vec3 k = w.scale(1 / theta);
        double c = Math.cos(theta), s = Math.sin(theta), t = 1 - c;
        return new Mat3(new double[]{
                c + t * k.x() * k.x(), t * k.x() * k.y() - s * k.z(), t * k.x() * k.z() + s * k.y(),
                t * k.x() * k.y() + s * k.z(), c + t * k.y() * k.y(), t * k.y() * k.z() - s * k.x(),
                t * k.x() * k.z() - s * k.y(), t * k.y() * k.z() + s * k.x(), c + t * k.z() * k.z()});
    }

    double get(int i, int j) { return m[i * 3 + j]; }

    Vec3 col(int j) { return new Vec3(m[j], m[3 + j], m[6 + j]); }

    /** this · v */
    Vec3 mul(Vec3 v) {
        return new Vec3(
                m[0] * v.x() + m[1] * v.y() + m[2] * v.z(),
                m[3] * v.x() + m[4] * v.y() + m[5] * v.z(),
                m[6] * v.x() + m[7] * v.y() + m[8] * v.z());
    }

    /** thisᵀ · v (para una rotación, es la inversa). */
    Vec3 mulT(Vec3 v) {
        return new Vec3(
                m[0] * v.x() + m[3] * v.y() + m[6] * v.z(),
                m[1] * v.x() + m[4] * v.y() + m[7] * v.z(),
                m[2] * v.x() + m[5] * v.y() + m[8] * v.z());
    }

    Mat3 mul(Mat3 o) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += m[i * 3 + k] * o.m[k * 3 + j];
                r[i * 3 + j] = s;
            }
        }
        return new Mat3(r);
    }

    Mat3 transpose() {
        return new Mat3(new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]});
    }

    /** Vuelve a ser una rotación pura (Gram-Schmidt sobre las columnas). Evita que los errores se acumulen. */
    Mat3 orthonormalized() {
        Vec3 c0 = col(0).normalized();
        Vec3 c1 = col(1).sub(c0.scale(c0.dot(col(1)))).normalized();
        return fromColumns(c0, c1, c0.cross(c1));
    }
}
