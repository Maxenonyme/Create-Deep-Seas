package com.maxenonyme.createsubmarine.submarine.math;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class OrientedBoundingBox3d {
    private final Vector3d position = new Vector3d();
    private final Vector3d dimensions = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();
    private final LevelReusedVectors sink;

    public OrientedBoundingBox3d(LevelReusedVectors sink) {
        this.sink = sink;
    }

    public void set(Vector3dc position, Vector3dc dimensions, Quaterniondc orientation) {
        this.position.set(position);
        this.dimensions.set(dimensions);
        this.orientation.set(orientation);
    }

    public void rotate(Quaterniondc rotation) {
        this.orientation.mul(rotation, this.orientation);
    }

    public void translate(Vector3dc delta) {
        this.position.add(delta);
    }

    public void scale(double factor) {
        this.dimensions.mul(factor);
    }

    public boolean contains(double x, double y, double z) {
        Vector3d local = sink.tempVert1.set(x - position.x, y - position.y, z - position.z);
        orientation.conjugate(new Quaterniond()).transform(local);
        return Math.abs(local.x) <= dimensions.x * 0.5 &&
               Math.abs(local.y) <= dimensions.y * 0.5 &&
               Math.abs(local.z) <= dimensions.z * 0.5;
    }

    public AABB getWorldAABB() {
        Vector3d hDim = sink.tempmin.set(dimensions).mul(0.5);
        Vector3d ax = sink.tempVert1.set(hDim.x, 0, 0);
        Vector3d ay = sink.tempVert2.set(0, hDim.y, 0);
        Vector3d az = sink.tempVert3.set(0, 0, hDim.z);
        orientation.transform(ax);
        orientation.transform(ay);
        orientation.transform(az);
        double hx = Math.abs(ax.x) + Math.abs(ay.x) + Math.abs(az.x);
        double hy = Math.abs(ax.y) + Math.abs(ay.y) + Math.abs(az.y);
        double hz = Math.abs(ax.z) + Math.abs(ay.z) + Math.abs(az.z);
        return new AABB(
            position.x - hx, position.y - hy, position.z - hz,
            position.x + hx, position.y + hy, position.z + hz
        );
    }

    public Vector3d[] getVertices() {
        Vector3d hDim = new Vector3d(dimensions).mul(0.5);
        Vector3d[] localCorners = {
            new Vector3d(-hDim.x, -hDim.y, -hDim.z),
            new Vector3d( hDim.x, -hDim.y, -hDim.z),
            new Vector3d( hDim.x, -hDim.y,  hDim.z),
            new Vector3d(-hDim.x, -hDim.y,  hDim.z),
            new Vector3d(-hDim.x,  hDim.y, -hDim.z),
            new Vector3d( hDim.x,  hDim.y, -hDim.z),
            new Vector3d( hDim.x,  hDim.y,  hDim.z),
            new Vector3d(-hDim.x,  hDim.y,  hDim.z),
        };
        Vector3d[] world = new Vector3d[8];
        for (int i = 0; i < 8; i++) {
            orientation.transform(localCorners[i]);
            localCorners[i].add(position);
            world[i] = localCorners[i];
        }
        return world;
    }

    public boolean intersectsOBB(OrientedBoundingBox3d other) {
        return satTest(this, other);
    }

    private static boolean satTest(OrientedBoundingBox3d a, OrientedBoundingBox3d b) {
        Vector3d[] axes = new Vector3d[15];
        int idx = 0;

        Vector3d aX = a.sink.tempVert1.set(1, 0, 0); a.orientation.transform(aX);
        Vector3d aY = a.sink.tempVert2.set(0, 1, 0); a.orientation.transform(aY);
        Vector3d aZ = a.sink.tempVert3.set(0, 0, 1); a.orientation.transform(aZ);
        Vector3d bX = b.sink.tempVert4.set(1, 0, 0); b.orientation.transform(bX);
        Vector3d bY = b.sink.tempVert5.set(0, 1, 0); b.orientation.transform(bY);
        Vector3d bZ = b.sink.tempVert6.set(0, 0, 1); b.orientation.transform(bZ);

        axes[idx++] = aX; axes[idx++] = aY; axes[idx++] = aZ;
        axes[idx++] = bX; axes[idx++] = bY; axes[idx++] = bZ;

        Vector3d[] cross = new Vector3d[9];
        cross[0] = aX.cross(bX, new Vector3d());
        cross[1] = aX.cross(bY, new Vector3d());
        cross[2] = aX.cross(bZ, new Vector3d());
        cross[3] = aY.cross(bX, new Vector3d());
        cross[4] = aY.cross(bY, new Vector3d());
        cross[5] = aY.cross(bZ, new Vector3d());
        cross[6] = aZ.cross(bX, new Vector3d());
        cross[7] = aZ.cross(bY, new Vector3d());
        cross[8] = aZ.cross(bZ, new Vector3d());
        for (int i = 0; i < 9; i++) {
            if (cross[i].lengthSquared() > 1e-12) axes[idx++] = cross[i];
        }

        Vector3d centerDiff = new Vector3d(b.position).sub(a.position);

        double[] ha = { a.dimensions.x * 0.5, a.dimensions.y * 0.5, a.dimensions.z * 0.5 };
        double[] hb = { b.dimensions.x * 0.5, b.dimensions.y * 0.5, b.dimensions.z * 0.5 };
        Vector3d[][] localAxes = { {aX, aY, aZ}, {bX, bY, bZ} };

        for (int i = 0; i < idx; i++) {
            Vector3d axis = axes[i];
            double d = Math.abs(centerDiff.dot(axis));

            double ra = 0;
            for (int j = 0; j < 3; j++)
                ra += ha[j] * Math.abs(localAxes[0][j].dot(axis));

            double rb = 0;
            for (int j = 0; j < 3; j++)
                rb += hb[j] * Math.abs(localAxes[1][j].dot(axis));

            if (d > ra + rb) return false;
        }
        return true;
    }

    public boolean intersectsAABB(AABB aabb) {
        Vector3d center = sink.tempmin.set(aabb.getCenter().x, aabb.getCenter().y, aabb.getCenter().z);
        Vector3d halfDim = sink.tempmax.set(
            (aabb.maxX - aabb.minX) * 0.5,
            (aabb.maxY - aabb.minY) * 0.5,
            (aabb.maxZ - aabb.minZ) * 0.5
        );
        OrientedBoundingBox3d temp = new OrientedBoundingBox3d(sink);
        temp.set(center, halfDim, new Quaterniond());
        return intersectsOBB(temp);
    }

    public double intersectRay(Vector3dc origin, Vector3dc direction) {
        Vector3d d = sink.tempmin.set(direction);
        Vector3d o = sink.tempmax.set(origin);
        Quaterniond inv = orientation.conjugate(new Quaterniond());
        inv.transform(d);
        inv.transform(o);
        o.sub(position.x, position.y, position.z);

        double[] tMin = new double[3];
        double[] tMax = new double[3];
        double[] half = { dimensions.x * 0.5, dimensions.y * 0.5, dimensions.z * 0.5 };

        for (int i = 0; i < 3; i++) {
            double di = d.get(i);
            double oi = o.get(i);
            if (Math.abs(di) < 1e-12) {
                if (Math.abs(oi) > half[i]) return -1;
                tMin[i] = Double.NEGATIVE_INFINITY;
                tMax[i] = Double.POSITIVE_INFINITY;
            } else {
                double invD = 1.0 / di;
                double t1 = (-half[i] - oi) * invD;
                double t2 = (half[i] - oi) * invD;
                tMin[i] = Math.min(t1, t2);
                tMax[i] = Math.max(t1, t2);
            }
        }

        double enter = Math.max(tMin[0], Math.max(tMin[1], tMin[2]));
        double exit = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
        return enter <= exit && exit >= 0 ? enter : -1;
    }

    public Vector3d closestPointTo(Vector3dc point) {
        Vector3d local = sink.tempVert1.set(point).sub(position);
        orientation.conjugate(new Quaterniond()).transform(local);
        Vector3d half = sink.tempVert2.set(dimensions).mul(0.5);
        local.set(
            Math.max(-half.x, Math.min(half.x, local.x)),
            Math.max(-half.y, Math.min(half.y, local.y)),
            Math.max(-half.z, Math.min(half.z, local.z))
        );
        orientation.transform(local);
        return local.add(position);
    }

    public Quaterniond getOrientation() {
        return orientation;
    }
    public Vector3d getPosition() {
        return position;
    }
    public Vector3d getDimensions() {
        return dimensions;
    }
}
