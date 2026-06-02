package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.math.OrientedBoundingBox3d;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SteelCablePhysicsSystem {

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;

            UUID subId = SubLevelRegistry.findUUID(player.level());
            SubLevelAccess sub = subId != null ? SubLevelRegistry.getAll().get(subId) : null;
            ServerLevel ropeSearchLevel = (player.level() instanceof ServerLevel sl) ? sl : null;

            if (sub != null) {
                Level parent = SubLevelRegistry.getLevel(subId);
                if (parent instanceof ServerLevel sl) {
                    ropeSearchLevel = sl;
                }
            }

            if (ropeSearchLevel == null) continue;

            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(ropeSearchLevel);
            if (ropeManager == null) continue;

            Vector3d pPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
            if (sub != null) {
                sub.logicalPose().transformPosition(pPos);
            }

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive()) continue;
                if (!isSteelCable(strand, ropeSearchLevel)) continue;

                List<Vector3d> points = strand.getPoints();
                for (int i = 0; i < points.size() - 1; i++) {
                    Vector3d a = points.get(i);
                    Vector3d b = points.get(i + 1);

                     Vector3d c = getClosestPointOnSegment(a, b, pPos);
                     double d = pPos.distance(c);

                     double worldX = pPos.x;
                     double worldZ = pPos.z;
                     double worldFeetY = pPos.y - player.getBbHeight() / 2.0;
                     double horizontalDist = Math.sqrt((worldX - c.x) * (worldX - c.x) + (worldZ - c.z) * (worldZ - c.z));
                     double vertDiff = worldFeetY - c.y;

                     if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45 && player.getDeltaMovement().y <= 0.05) {
                         double targetWorldFeetY = c.y + 0.1;
                         double pushY = targetWorldFeetY - worldFeetY;

                         Vector3d pushVec = new Vector3d(0, pushY, 0);
                         if (sub != null) {
                             sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                         }

                         player.setPos(player.getX() + pushVec.x, player.getY() + pushVec.y, player.getZ() + pushVec.z);
                         player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                         player.setOnGround(true);
                         player.fallDistance = 0.0f;

                         pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                         if (sub != null) {
                             sub.logicalPose().transformPosition(pPos);
                         }
                         continue;
                     }

                     double collisionLimit = 0.5;
                     if (d < collisionLimit) {
                         Vector3d push = new Vector3d(pPos).sub(c);
                         double dist = push.length();
                         if (dist < 1e-6) {
                             push.set(0, 1, 0);
                             dist = 1.0;
                         }
                         double overlap = collisionLimit - dist;
                         push.normalize();

                        Vector3d pushVec = new Vector3d(push).mul(overlap);
                        if (sub != null) {
                            sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(pushVec);
                        }

                        player.setPos(player.getX() + pushVec.x, player.getY() + pushVec.y, player.getZ() + pushVec.z);

                        net.minecraft.world.phys.Vec3 velocity = player.getDeltaMovement();
                        Vector3d vel = new Vector3d(velocity.x, velocity.y, velocity.z);
                        double dot = vel.dot(push);
                        if (dot < 0) {
                            vel.sub(new Vector3d(push).mul(dot));
                        }
                        vel.add(new Vector3d(push).mul(overlap * 0.8));
                        player.setDeltaMovement(new net.minecraft.world.phys.Vec3(vel.x, vel.y, vel.z));
                        player.hasImpulse = true;

                        pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                        if (sub != null) {
                            sub.logicalPose().transformPosition(pPos);
                        }
                    }
                }
            }
        }

        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null) continue;

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive()) continue;
                if (!isSteelCable(strand, serverLevel)) continue;

                List<Vector3d> points = strand.getPoints();
                for (int i = 0; i < points.size() - 1; i++) {
                    Vector3d a = points.get(i);
                    Vector3d b = points.get(i + 1);

                    AABB segmentBox = new AABB(
                        Math.min(a.x, b.x) - 1.0, Math.min(a.y, b.y) - 1.0, Math.min(a.z, b.z) - 1.0,
                        Math.max(a.x, b.x) + 1.0, Math.max(a.y, b.y) + 1.0, Math.max(a.z, b.z) + 1.0
                    );

                    for (Entity entity : serverLevel.getEntities((Entity) null, segmentBox,
                            e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger())) {
                        collideEntityWithCableSegment(entity, a, b);
                    }
                }
            }
        }

        Map<UUID, OrientedBoundingBox3d> hulls = SubmarineHullManager.getActiveHulls();
        if (hulls.isEmpty()) return;

        for (Map.Entry<UUID, OrientedBoundingBox3d> entry : hulls.entrySet()) {
            UUID id = entry.getKey();
            if (com.maxenonyme.createsubmarine.submarine.block.entity.PoulisBlockEntity.SLIDING_SUBLEVELS.contains(id)) {
                continue;
            }
            OrientedBoundingBox3d obb = entry.getValue();
            SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
            Level parentLevel = SubLevelRegistry.getLevel(id);
            if (sub == null || !(parentLevel instanceof ServerLevel serverLevel)) continue;

            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null) continue;

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive()) continue;
                if (!isSteelCable(strand, serverLevel)) continue;

                List<Vector3d> points = strand.getPoints();
                for (int i = 0; i < points.size() - 1; i++) {
                    Vector3d a = points.get(i);
                    Vector3d b = points.get(i + 1);

                    checkSublevelCollision(sub, obb, a, b);
                }
            }
        }
    }

    private static void collideEntityWithCableSegment(Entity entity, Vector3d a, Vector3d b) {
        Vector3d ePos = new Vector3d(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
        Vector3d c = getClosestPointOnSegment(a, b, ePos);

        double horizontalDist = Math.sqrt((ePos.x - c.x) * (ePos.x - c.x) + (ePos.z - c.z) * (ePos.z - c.z));
        double feetY = entity.getY();
        double vertDiff = feetY - c.y;

        if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45 && entity.getDeltaMovement().y <= 0.05) {
            entity.setPos(entity.getX(), c.y + 0.1, entity.getZ());
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.0, entity.getDeltaMovement().z);
            entity.setOnGround(true);
            entity.fallDistance = 0.0f;
            return;
        }

        double d = ePos.distance(c);
        double collisionLimit = 0.5;
        if (d < collisionLimit) {
            Vector3d push = new Vector3d(ePos).sub(c);
            double dist = push.length();
            if (dist < 1e-6) { push.set(0, 1, 0); dist = 1.0; }
            double overlap = collisionLimit - dist;
            push.normalize();

            entity.setPos(entity.getX() + push.x * overlap, entity.getY() + push.y * overlap, entity.getZ() + push.z * overlap);

            net.minecraft.world.phys.Vec3 velocity = entity.getDeltaMovement();
            Vector3d vel = new Vector3d(velocity.x, velocity.y, velocity.z);
            double dot = vel.dot(push);
            if (dot < 0) vel.sub(new Vector3d(push).mul(dot));
            vel.add(new Vector3d(push).mul(overlap * 0.8));
            entity.setDeltaMovement(new net.minecraft.world.phys.Vec3(vel.x, vel.y, vel.z));
            entity.hasImpulse = true;
        }
    }

    private static void checkSublevelCollision(SubLevelAccess sub, OrientedBoundingBox3d obb, Vector3d a, Vector3d b) {
        Vector3d center = obb.getPosition();
        Vector3d c = getClosestPointOnSegment(a, b, center);

        if (SubmarineHullManager.contains(obb, c.x, c.y, c.z)) {
            Vector3d dim = obb.getDimensions();
            Vector3d localC = new Vector3d(c.x - center.x, c.y - center.y, c.z - center.z);
            obb.getOrientation().conjugate(new Quaterniond()).transform(localC);

            double overlapX = dim.x * 0.5 - Math.abs(localC.x);
            double overlapY = dim.y * 0.5 - Math.abs(localC.y);
            double overlapZ = dim.z * 0.5 - Math.abs(localC.z);

            double minOverlap = overlapX;
            Vector3d localPush = new Vector3d(Math.signum(localC.x) * overlapX, 0, 0);

            if (overlapY < minOverlap) {
                minOverlap = overlapY;
                localPush.set(0, Math.signum(localC.y) * overlapY, 0);
            }
            if (overlapZ < minOverlap) {
                localPush.set(0, 0, Math.signum(localC.z) * overlapZ);
            }

            Vector3d worldPush = new Vector3d(localPush);
            obb.getOrientation().transform(worldPush);

            Object handle = SablePhysicsHelper.getHandle(sub);
            if (handle != null) {
                SablePhysicsHelper.wakeUp(handle);
                double mass = SablePhysicsHelper.readMass(sub);
                Vector3d forceVec = new Vector3d(worldPush).mul(mass * 0.05);
                sub.logicalPose().orientation().conjugate(new Quaterniond()).transform(forceVec);
                SablePhysicsHelper.applyLinearImpulse(handle, forceVec);
            }
        }
    }

    private static Vector3d getClosestPointOnSegment(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ap = new Vector3d(p).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < 1e-6) {
            return new Vector3d(a);
        }
        double t = ap.dot(ab) / abLenSq;
        t = Math.clamp(t, 0.0, 1.0);
        return new Vector3d(a).add(ab.mul(t));
    }

    public static boolean isSteelCable(ServerRopeStrand strand, ServerLevel level) {
        if (strand instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
            return true;
        }
        RopeAttachment startAttachment = strand.getAttachment(RopeAttachmentPoint.START);
        if (startAttachment != null) {
            BlockEntity be = level.getBlockEntity(startAttachment.blockAttachment());
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    return true;
                }
            }
        }
        RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment != null) {
            BlockEntity be = level.getBlockEntity(endAttachment.blockAttachment());
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    return true;
                }
            }
        }
        return false;
    }
}
