package com.maxenonyme.createsubmarine.submarine.math;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class RotatedEntityHitbox {

    private static class Part {
        final Vector3d offset = new Vector3d();
        final Vector3d dimensions = new Vector3d();
        AABB aabb = new AABB(0, 0, 0, 0, 0, 0);
    }

    private final List<Part> parts = new ArrayList<>();
    private final LevelReusedVectors sink;

    public RotatedEntityHitbox(LevelReusedVectors sink) {
        this.sink = sink;
    }

    public void setSize(double width, double height) {
        setSize(width, height, width);
    }

    public void setSize(double width, double height, double depth) {
        parts.clear();
        addPart(width, height, depth, 0, 0, 0);
    }

    public void addPart(double width, double height, double depth, double offsetX, double offsetY, double offsetZ) {
        Part part = new Part();
        part.dimensions.set(width, height, depth);
        part.offset.set(offsetX, offsetY, offsetZ);
        parts.add(part);
    }

    private void syncParts(Entity entity, @Nullable Quaterniondc extraRotation) {
        Quaterniond orientation = new Quaterniond()
            .rotateY(-Math.toRadians(entity.getYRot()))
            .rotateX(-Math.toRadians(entity.getXRot()));
        if (extraRotation != null) {
            orientation.mul(extraRotation, orientation);
        }
        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        for (Part part : parts) {
            double ox = part.offset.x, oy = part.offset.y, oz = part.offset.z;
            double px = ex, py = ey, pz = ez;
            if (ox != 0 || oy != 0 || oz != 0) {
                sink.tempVert1.set(ox, oy, oz);
                orientation.transform(sink.tempVert1);
                px += sink.tempVert1.x;
                py += sink.tempVert1.y;
                pz += sink.tempVert1.z;
            }
            double hw = part.dimensions.x * 0.5;
            double hh = part.dimensions.y * 0.5;
            double hd = part.dimensions.z * 0.5;
            part.aabb = new AABB(px - hw, py - hh, pz - hd, px + hw, py + hh, pz + hd);
        }
    }

    public void syncFromEntity(Entity entity) {
        syncParts(entity, null);
    }

    public void syncFromEntity(Entity entity, Quaterniondc extraRotation) {
        syncParts(entity, extraRotation);
    }

    public boolean contains(double x, double y, double z) {
        for (Part part : parts) {
            if (part.aabb.contains(x, y, z)) return true;
        }
        return false;
    }

    public boolean contains(Vec3 point) {
        return contains(point.x, point.y, point.z);
    }

    public AABB getWorldAABB() {
        if (parts.isEmpty()) return new AABB(0, 0, 0, 0, 0, 0);
        AABB result = parts.get(0).aabb;
        for (int i = 1; i < parts.size(); i++) {
            result = result.minmax(parts.get(i).aabb);
        }
        return result;
    }

    public List<AABB> getParts() {
        List<AABB> list = new ArrayList<>(parts.size());
        for (Part part : parts) list.add(part.aabb);
        return list;
    }
}
