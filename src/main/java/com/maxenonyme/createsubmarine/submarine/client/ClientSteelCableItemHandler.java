package com.maxenonyme.createsubmarine.submarine.client;

import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.config.server.blocks.SimBlockConfigs;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ClientSteelCableItemHandler {
    public static void tick() {
        final Player player = Minecraft.getInstance().player;
        final Level level = Minecraft.getInstance().level;

        if (player == null || level == null)
            return;
        if (Minecraft.getInstance().screen != null)
            return;

        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack heldItem = player.getItemInHand(hand);

            if (!heldItem.is(com.maxenonyme.createsubmarine.CreateSubmarine.STEEL_CABLE.get()))
                continue;

            if (!heldItem.has(SimDataComponents.ROPE_FIRST_CONNECTION))
                continue;

            final BlockPos firstBlock = heldItem.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            final HitResult rayTrace = Minecraft.getInstance().hitResult;

            if (rayTrace instanceof final BlockHitResult hitResult) {
                final BlockPos hitBlock = hitResult.getBlockPos();
                final Vec3 firstPoint = firstBlock.getCenter();

                final double maxRopeRange = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.STEEL_CABLE_MAX_LENGTH.get();

                boolean inRange = Sable.HELPER.distanceSquaredWithSubLevels(level, firstPoint, hitResult.getLocation()) < maxRopeRange * maxRopeRange;
                boolean valid = RopeItem.isValidRopeAttachment(level, hitBlock) && !hitBlock.equals(firstBlock) && inRange;

                final RopeStrandHolderBehavior holderA = RopeItem.getRopeHolder(level, hitBlock);
                final RopeStrandHolderBehavior holderB = RopeItem.getRopeHolder(level, firstBlock);

                if (valid &&
                        holderA != null && holderA.blockEntity instanceof RopeWinchBlockEntity &&
                        holderB != null && holderB.blockEntity instanceof RopeWinchBlockEntity)
                    valid = false;

                final Vec3 target = valid ? hitBlock.getCenter() : hitResult.getLocation();

                final Color color;
                if (valid) {
                    color = new Color(SimColors.SUCCESS_LIME);
                } else {
                    color = new Color(inRange ? SimColors.PERCHANCE_ORANGE : SimColors.NUH_UH_RED);
                }

                Outliner.getInstance().chaseAABB("FirstRopeAttachmentPoint", new AABB(firstPoint, firstPoint))
                        .colored(color)
                        .lineWidth(1 / 3f)
                        .disableLineNormals();

                final Vec3 globalFirstPoint = Sable.HELPER.projectOutOfSubLevel(level, firstPoint);
                Vec3 globalTarget = Sable.HELPER.projectOutOfSubLevel(level, target);

                if (valid) {
                    Outliner.getInstance().chaseAABB("SecondRopeAttachmentPoint", new AABB(target, target))
                            .colored(color)
                            .lineWidth(1 / 3f)
                            .disableLineNormals();

                    final double points = Math.floor(globalFirstPoint.distanceTo(globalTarget));
                    final Vec3 backwardsDiff = globalFirstPoint.subtract(globalTarget).normalize();
                    for (int i = 0; i < points; i++) {
                        final Vec3 point = globalTarget.add(backwardsDiff.scale(i));

                        Outliner.getInstance().chaseAABB("RopePoint" + i, new AABB(point, point))
                                .colored(color)
                                .lineWidth(1 / 8f)
                                .disableLineNormals();
                    }
                } else if (!inRange) {
                    globalTarget = globalTarget.subtract(globalFirstPoint).normalize().scale(maxRopeRange - 0.5).add(globalFirstPoint);
                    Outliner.getInstance().chaseAABB("SecondRopeAttachmentPoint", new AABB(globalTarget, globalTarget))
                            .colored(color)
                            .lineWidth(1 / 3f)
                            .disableLineNormals();
                }

                final DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1);
                final double totalFlyingTicks = 10;
                final int segments = (((int) totalFlyingTicks) / 3) + 1;

                for (int i = 0; i < segments; i++) {
                    final Vec3 vec = globalFirstPoint.lerp(globalTarget, level.random.nextFloat());
                    level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
                }
            }
        }
    }

    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        tick();
        tickPlayerCollision();
    }

    public static void tickPlayerCollision() {
        final Player player = Minecraft.getInstance().player;
        final Level level = Minecraft.getInstance().level;
        if (player == null || level == null) return;
        if (player.isSpectator()) return;

        dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager ropeManager = dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager.getOrCreate(level);
        if (ropeManager != null) {
            org.joml.Vector3d pPos = new org.joml.Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
            collidePlayerWithCables(player, ropeManager, pPos, null);
        }

        java.util.UUID subId = com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.findUUID(player.level());
        dev.ryanhcode.sable.companion.SubLevelAccess sub = subId != null ? com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.getAll().get(subId) : null;
        if (sub != null) {
            Level parent = com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.getLevel(subId);
            dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager parentManager =
                    parent != null ? dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager.getOrCreate(parent) : null;
            if (parentManager != null) {
                org.joml.Vector3d pPos = new org.joml.Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                sub.logicalPose().transformPosition(pPos);
                collidePlayerWithCables(player, parentManager, pPos, sub);
            }
        }
    }

    private static void collidePlayerWithCables(Player player,
                                                dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager ropeManager,
                                                org.joml.Vector3d pPos,
                                                dev.ryanhcode.sable.companion.SubLevelAccess sub) {
        for (dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand strand : ropeManager.getAllStrands()) {
            if (!(strand instanceof com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor accessor) || !accessor.createsubmarine$isSteelCable()) {
                continue;
            }

            it.unimi.dsi.fastutil.objects.ObjectArrayList<dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint> points = strand.getPoints();
            if (points.size() <= 1) continue;

            for (int i = 0; i < points.size() - 1; i++) {
                org.joml.Vector3d a = points.get(i).position();
                org.joml.Vector3d b = points.get(i + 1).position();

                org.joml.Vector3d pFeet = new org.joml.Vector3d(player.getX(), player.getY() + 0.2, player.getZ());
                org.joml.Vector3d pMid = new org.joml.Vector3d(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                org.joml.Vector3d pHead = new org.joml.Vector3d(player.getX(), player.getY() + player.getBbHeight() - 0.2, player.getZ());

                org.joml.Vector3d cFeet = getClosestPointOnSegment(a, b, pFeet);
                org.joml.Vector3d cMid = getClosestPointOnSegment(a, b, pMid);
                org.joml.Vector3d cHead = getClosestPointOnSegment(a, b, pHead);

                double dFeet = pFeet.distance(cFeet);
                double dMid = pMid.distance(cMid);
                double dHead = pHead.distance(cHead);

                org.joml.Vector3d bestP = pMid;
                org.joml.Vector3d bestC = cMid;
                double bestD = dMid;

                if (dFeet < bestD) {
                    bestD = dFeet;
                    bestP = pFeet;
                    bestC = cFeet;
                }
                if (dHead < bestD) {
                    bestD = dHead;
                    bestP = pHead;
                    bestC = cHead;
                }

                double worldX = player.getX();
                double worldZ = player.getZ();
                double worldFeetY = player.getY();
                double horizontalDist = Math.sqrt((worldX - cFeet.x) * (worldX - cFeet.x) + (worldZ - cFeet.z) * (worldZ - cFeet.z));
                double vertDiff = worldFeetY - cFeet.y;

                if (horizontalDist < 0.4 && vertDiff >= -0.15 && vertDiff <= 0.45 && player.getDeltaMovement().y <= 0.05) {
                    double targetWorldFeetY = cFeet.y + 0.1;
                    double pushY = targetWorldFeetY - worldFeetY;

                    org.joml.Vector3d pushVec = new org.joml.Vector3d(0, pushY, 0);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(pushVec);
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

                double collisionLimit = 0.4;
                if (bestD < collisionLimit) {
                    org.joml.Vector3d push = new org.joml.Vector3d(bestP).sub(bestC);
                    double dist = push.length();
                    if (dist < 1e-6) {
                        push.set(0, 1, 0);
                        dist = 1.0;
                    }
                    double overlap = collisionLimit - dist;
                    push.normalize();

                    org.joml.Vector3d pushVec = new org.joml.Vector3d(push).mul(overlap);
                    if (sub != null) {
                        sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(pushVec);
                    }

                    player.setPos(player.getX() + pushVec.x, player.getY() + pushVec.y, player.getZ() + pushVec.z);

                    Vec3 velocity = player.getDeltaMovement();
                    org.joml.Vector3d vel = new org.joml.Vector3d(velocity.x, velocity.y, velocity.z);
                    double dot = vel.dot(push);
                    if (dot < 0) {
                        vel.sub(new org.joml.Vector3d(push).mul(dot));
                    }
                    vel.add(new org.joml.Vector3d(push).mul(overlap * 0.8));
                    player.setDeltaMovement(new Vec3(vel.x, vel.y, vel.z));
                    player.hasImpulse = true;

                    pPos.set(player.getX(), player.getY() + player.getBbHeight() / 2.0, player.getZ());
                    if (sub != null) {
                        sub.logicalPose().transformPosition(pPos);
                    }
                }
            }
        }
    }

    private static org.joml.Vector3d getClosestPointOnSegment(org.joml.Vector3d a, org.joml.Vector3d b, org.joml.Vector3d p) {
        org.joml.Vector3d ab = new org.joml.Vector3d(b).sub(a);
        org.joml.Vector3d ap = new org.joml.Vector3d(p).sub(a);
        double abLenSq = ab.lengthSquared();
        if (abLenSq < 1e-6) {
            return new org.joml.Vector3d(a);
        }
        double t = ap.dot(ab) / abLenSq;
        t = Math.clamp(t, 0.0, 1.0);
        return new org.joml.Vector3d(a).add(ab.mul(t));
    }
}
