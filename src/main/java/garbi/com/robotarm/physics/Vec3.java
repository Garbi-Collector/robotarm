package garbi.com.robotarm.physics;

/** Vector inmutable de tres componentes. */
record Vec3(double x, double y, double z) {

    static final Vec3 ZERO = new Vec3(0, 0, 0);

    Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }

    Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }

    Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }

    double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }

    double length() { return Math.sqrt(dot(this)); }

    Vec3 normalized() {
        double l = length();
        return l < 1e-12 ? ZERO : scale(1 / l);
    }

    double get(int i) { return i == 0 ? x : i == 1 ? y : z; }
}
