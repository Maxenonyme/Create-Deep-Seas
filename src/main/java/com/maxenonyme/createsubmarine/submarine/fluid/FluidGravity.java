package com.maxenonyme.createsubmarine.submarine.fluid;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class FluidGravity {

    private FluidGravity() {}

    private static final Vector3d WORLD_DOWN = new Vector3d(0, -1, 0);

    public static Direction ofSubLevel(SubLevelAccess sub) {
        Vector3d g = new Vector3d(WORLD_DOWN);
        Quaterniond orientation = new Quaterniond(sub.logicalPose().orientation());
        orientation.conjugate().transform(g);

        double ax = Math.abs(g.x);
        double ay = Math.abs(g.y);
        double az = Math.abs(g.z);

        if (ay >= ax && ay >= az) {
            return g.y < 0 ? Direction.DOWN : Direction.UP;
        }
        if (ax >= az) {
            return g.x < 0 ? Direction.WEST : Direction.EAST;
        }
        return g.z < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static Direction[] lateral(Direction gravity) {
        Direction.Axis gravityAxis = gravity.getAxis();
        Direction[] out = new Direction[4];
        int i = 0;
        for (Direction d : Direction.values()) {
            if (d.getAxis() != gravityAxis) {
                out[i++] = d;
            }
        }
        return out;
    }
}
