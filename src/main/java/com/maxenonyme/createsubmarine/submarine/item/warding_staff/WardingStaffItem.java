package com.maxenonyme.createsubmarine.submarine.item.warding_staff;

import java.util.function.Consumer;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.WardingEffectPayload;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class WardingStaffItem extends Item {

    private static final int WARD_RADIUS = 20;
    private static final int MOB_RADIUS = 16;
    private static final double PUSH_FORCE = 6000.0;
    private static final int COOLDOWN = 100;

    public WardingStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 playerPos = player.position();

        Messenger.broadcastWard(serverLevel, playerPos);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new WardingEffectPayload(player.getId()));

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null) {
            org.joml.Vector3d playerJoml = new org.joml.Vector3d(playerPos.x, playerPos.y, playerPos.z);
            for (SubLevel sub : container.getAllSubLevels()) {
                if (sub == null || sub.isRemoved()) continue;
                org.joml.Vector3d subWorldPos = sub.logicalPose().position();
                double dx = subWorldPos.x - playerJoml.x;
                double dy = subWorldPos.y - playerJoml.y;
                double dz = subWorldPos.z - playerJoml.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > WARD_RADIUS * WARD_RADIUS || distSq < 1.0) continue;
                double dist = Math.sqrt(distSq);
                double falloff = 1.0 - (dist / WARD_RADIUS);
                double forceMag = PUSH_FORCE * falloff * falloff;
                org.joml.Vector3d dir = new org.joml.Vector3d(dx / dist, dy / dist + 0.4, dz / dist).normalize();
                dir.mul(forceMag);
                Object handle = SablePhysicsHelper.getHandle(sub);
                if (handle != null) {
                    SablePhysicsHelper.wakeUp(handle);
                    SablePhysicsHelper.applyLinearImpulse(handle, dir);
                }
            }
        }

        AABB mobBox = player.getBoundingBox().inflate(MOB_RADIUS);
        for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, mobBox, e ->
                e != player && e.isAlive())) {
            if (entity instanceof Player) continue;
            Vec3 away = entity.position().subtract(player.position()).normalize();
            if (away.lengthSqr() < 0.01) away = new Vec3(0, 1, 0);
            double mobDist = entity.distanceTo(player);
            double mobFalloff = 1.0 - (mobDist / MOB_RADIUS);
            double pushStr = 2.5 * mobFalloff;
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                if (mob.getBrain() != null) {
                    mob.getBrain().setMemory(MemoryModuleType.HURT_BY, level.damageSources().generic());
                    mob.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, player);
                    mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
                }
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                if (mob instanceof PathfinderMob pm) {
                    Vec3 fleeTarget = entity.position().add(away.scale(20));
                    pm.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.5 * mobFalloff + 0.5);
                }
            } else if (entity instanceof Animal animal) {
                animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
            }
            Vec3 vel = entity.getDeltaMovement().add(away.scale(pushStr)).add(0, 0.5 * mobFalloff, 0);
            entity.setDeltaMovement(vel);
            entity.hurtMarked = true;
        }

        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void initializeClient(Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new WardingStaffItemRenderer()));
    }

    private static final class Messenger {
        private static void broadcastWard(ServerLevel level, Vec3 pos) {
            level.players().forEach(p -> {
                if (p.distanceToSqr(pos) < 25600) {
                    p.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("\u2728").withStyle(
                                    net.minecraft.ChatFormatting.AQUA), true);
                }
            });
        }
    }
}
