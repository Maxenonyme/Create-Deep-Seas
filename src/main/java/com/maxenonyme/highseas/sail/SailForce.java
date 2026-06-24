package com.maxenonyme.highseas.sail;

import com.maxenonyme.highseas.wind.WindConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.List;

public final class SailForce {
    private SailForce() {
    }

    public static Vector3d forward(Quaterniondc orientation, Vec3 rudderLocal, Vec3 centerLocal,
                                   int spanX, int spanZ, List<SailGroup> sails) {
        if (rudderLocal != null && centerLocal != null) {
            Vector3d fwd = new Vector3d(centerLocal.x - rudderLocal.x, 0.0, centerLocal.z - rudderLocal.z);
            orientation.transform(fwd);
            fwd.y = 0;
            if (fwd.lengthSquared() >= 1.0e-6) {
                fwd.normalize();
                return fwd;
            }
        }
        return keelForward(orientation, spanX, spanZ, sails);
    }

    public static Vector3d keelForward(Quaterniondc orientation, int spanX, int spanZ, List<SailGroup> sails) {
        Vector3d keel = spanX >= spanZ ? new Vector3d(1, 0, 0) : new Vector3d(0, 0, 1);
        orientation.transform(keel);
        keel.y = 0;
        if (keel.lengthSquared() < 1.0e-9) {
            return null;
        }
        keel.normalize();

        double sign = 0;
        Vector3d tmp = new Vector3d();
        for (SailGroup group : sails) {
            if (group.axis() == Direction.Axis.Y) {
                continue;
            }
            Vec3 ln = group.localNormal();
            tmp.set(ln.x, ln.y, ln.z);
            orientation.transform(tmp);
            sign += (tmp.x * keel.x + tmp.z * keel.z) * group.area();
        }
        if (sign < 0) {
            keel.negate();
        }
        return keel;
    }

    public static double power(Vec3 wind, double nx, double ny, double nz,
                               double fx, double fy, double fz, int area) {
        double across = Math.abs(nx * fx + ny * fy + nz * fz);
        double tailwind = Math.max(0.0, wind.x * fx + wind.y * fy + wind.z * fz);
        return area * across * (WindConfig.SAIL_BASE_THRUST + WindConfig.SAIL_WIND_GAIN * tailwind);
    }
}
