package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class CableElectrificationSystem {

    public static final Map<RopeWinchBlockEntity, EnergyStorage> WINCH_ENERGY = Collections.synchronizedMap(new WeakHashMap<>());

    private static final int FE_CAPACITY = 10000;
    private static final int FE_DRAIN_PER_TICK = 50;
    private static final float DAMAGE_AMOUNT = 2.0f;
    private static final double DAMAGE_RADIUS = 1.0;
    private static final int DAMAGE_INTERVAL_TICKS = 20;

    private static int tickCounter = 0;

    public static EnergyStorage getOrCreateStorage(RopeWinchBlockEntity be) {
        return WINCH_ENERGY.computeIfAbsent(be, k -> new EnergyStorage(FE_CAPACITY));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null) continue;

            for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                if (!strand.isActive()) continue;
                if (!SteelCablePhysicsSystem.isSteelCable(strand, serverLevel)) continue;

                RopeWinchBlockEntity electrifiedWinch = findElectrifiedWinch(strand, serverLevel);
                if (electrifiedWinch == null) continue;

                EnergyStorage storage = WINCH_ENERGY.get(electrifiedWinch);
                if (storage == null || storage.getEnergyStored() <= 0) continue;

                storage.extractEnergy(FE_DRAIN_PER_TICK, false);

                List<Vector3d> points = strand.getPoints();
                spawnSparksAlongCable(serverLevel, points);

                if (tickCounter % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntitiesAlongCable(serverLevel, points, event.getServer());
                }
            }
        }
    }

    private static RopeWinchBlockEntity findElectrifiedWinch(ServerRopeStrand strand, ServerLevel level) {
        for (RopeAttachmentPoint point : new RopeAttachmentPoint[]{RopeAttachmentPoint.START, RopeAttachmentPoint.END}) {
            RopeAttachment attachment = strand.getAttachment(point);
            if (attachment == null) continue;
            BlockEntity be = level.getBlockEntity(attachment.blockAttachment());
            if (be instanceof RopeWinchBlockEntity winch) {
                EnergyStorage storage = WINCH_ENERGY.get(winch);
                if (storage != null && storage.getEnergyStored() > 0) return winch;
            }
            if (be instanceof SmartBlockEntity smartBe) {
                RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                    if (be instanceof RopeWinchBlockEntity winch) {
                        EnergyStorage storage = WINCH_ENERGY.get(winch);
                        if (storage != null && storage.getEnergyStored() > 0) return winch;
                    }
                }
            }
        }
        return null;
    }

    private static void spawnSparksAlongCable(ServerLevel level, List<Vector3d> points) {
        if (points.size() < 2) return;
        int sparkCount = Math.min(points.size() - 1, 3);
        for (int k = 0; k < sparkCount; k++) {
            int i = (tickCounter + k * 7) % (points.size() - 1);
            Vector3d a = points.get(i);
            Vector3d b = points.get(i + 1);
            double t = (Math.random() * 0.6 + 0.2);
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.05, 0.05, 0.05, 0.1);
        }
    }

    private static void damageEntitiesAlongCable(ServerLevel level, List<Vector3d> points, net.minecraft.server.MinecraftServer server) {
        if (points.size() < 2) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vector3d p : points) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        AABB bounds = new AABB(minX - DAMAGE_RADIUS, minY - DAMAGE_RADIUS, minZ - DAMAGE_RADIUS,
                               maxX + DAMAGE_RADIUS, maxY + DAMAGE_RADIUS, maxZ + DAMAGE_RADIUS);

        
        for (Entity entity : level.getEntities((Entity) null, bounds, e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Player))) {
            tryDamage(level, (LivingEntity) entity, new Vector3d(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ()), points);
        }

        for (Player player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) continue;

            if (player.level() == level) {
                Vector3d worldPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                tryDamage(level, player, worldPos, points);
                continue;
            }

            java.util.UUID subId = SubLevelRegistry.findUUID(player.level());
            SubLevelAccess sub = subId != null ? SubLevelRegistry.getAll().get(subId) : null;
            if (sub != null && SubLevelRegistry.getLevel(subId) == level) {
                Vector3d worldPos = new Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                sub.logicalPose().transformPosition(worldPos);
                tryDamage(level, player, worldPos, points);
            }
        }
    }

    private static void tryDamage(ServerLevel level, LivingEntity entity, Vector3d worldPos, List<Vector3d> points) {
        double closestDist = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d c = getClosestPoint(points.get(i), points.get(i + 1), worldPos);
            closestDist = Math.min(closestDist, worldPos.distance(c));
        }
        if (closestDist <= DAMAGE_RADIUS) {
            entity.hurt(level.damageSources().lightningBolt(), DAMAGE_AMOUNT);
        }
    }

    private static Vector3d getClosestPoint(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = new Vector3d(b).sub(a);
        double t = new Vector3d(p).sub(a).dot(ab) / ab.lengthSquared();
        t = Math.clamp(t, 0.0, 1.0);
        return new Vector3d(a).add(new Vector3d(ab).mul(t));
    }
}
