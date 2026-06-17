package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FloaterBlockEntity extends BlockEntity implements dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
    private org.joml.Vector3d recordedForceVec = null;
    private static final int PRESSURE_THRESHOLD = 50;
    private static final int PRESSURE_CHECK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 200;

    private int pressureTickCounter = 0;

    public FloaterBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.FLOATER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, FloaterBlockEntity be) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        boolean sealed = sub != null
                && com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.hasAnySealed(sub.getUniqueId());

        if (++be.pressureTickCounter >= PRESSURE_CHECK_INTERVAL) {
            be.pressureTickCounter = 0;
            Level worldLevel = level;
            BlockPos worldPos = pos;
            if (sub != null) {
                Level parent = SubLevelRegistry.getLevel(sub.getUniqueId());
                if (parent != null)
                    worldLevel = parent;
                Vector3d wp = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                sub.logicalPose().transformPosition(wp);
                worldPos = BlockPos.containing(wp.x, wp.y, wp.z);
            }
            int threshold = PRESSURE_THRESHOLD;
            net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(pos);
            java.util.Optional<com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.HullProperty> propOpt = com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.getFor(blockState);
            if (propOpt.isPresent()) {
                threshold = propOpt.get().maxWaterDepth();
            }
            if (sealed && countWaterAbove(worldLevel, worldPos) > threshold) {
                burst(level, pos);
                return;
            }
        }

        if (sub == null)
            return;

        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Object handle = SablePhysicsHelper.getHandle(sub);
        Vector3dc currentVel = SablePhysicsHelper.getVelocity(handle);
        double currentVelY = (currentVel != null) ? currentVel.y() : 0;

        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            parentLevel = sl.getLevel();
        }

        if (parentLevel == null)
            return;

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        double localWaterSurfaceY = -999.0;

        net.minecraft.world.level.material.FluidState fluidState = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, parentPos);
        if (fluidState.is(FluidTags.WATER)) {
            float h = fluidState.getHeight(parentLevel, parentPos);
            localWaterSurfaceY = parentPos.getY() + h + countWaterAbove(parentLevel, parentPos);
        } else {
            BlockPos belowPos = parentPos.below();
            net.minecraft.world.level.material.FluidState belowFluid = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, belowPos);
            if (belowFluid.is(FluidTags.WATER)) {
                float h = belowFluid.getHeight(parentLevel, belowPos);
                localWaterSurfaceY = belowPos.getY() + h + countWaterAbove(parentLevel, belowPos);
            }
        }

        double depth = localWaterSurfaceY - (worldPos.y - 0.5);
        boolean isUnderWater = (depth > 0.0);

        if (!isUnderWater) {
            if (sealed) checkCrash(level, pos, parentLevel, parentPos, currentVel);
            return;
        }

        if (sealed) checkCrash(level, pos, parentLevel, parentPos, currentVel);

        double submergedRatio = Math.max(0.0, Math.min(1.0, depth));
        double distanceToSurface = localWaterSurfaceY - worldPos.y;
        double targetVelY = Math.max(-0.1, Math.min(1.0, distanceToSurface));

        double perceivedVelY = Math.max(-0.2, Math.min(0.2, currentVelY));
        double errorY = targetVelY - perceivedVelY;

        double forceMult = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.BALLAST_FORCE_MULTIPLIER.get();
        double liftPerFloater = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.FLOATER_LIFT.get();
        double forceToApply = errorY * liftPerFloater * 0.12 * forceMult * submergedRatio;

        if (Double.isFinite(forceToApply)) {
            org.joml.Vector3d applied = applyForce(sub, forceToApply);
            if (applied != null) be.recordedForceVec = applied;
        }
    }

    private static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < pos.getY() + 1 + MAX_WATER_SCAN; y++) {
            m.set(pos.getX(), y, pos.getZ());
            if (com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(level, m).is(FluidTags.WATER)) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }

    private static void burst(Level level, BlockPos pos) {
        level.playSound(null, pos, CreateSubmarine.IMPLOSION_SOUND.get(), SoundSource.BLOCKS, 1.2f, 1.4f);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20,
                    0.4, 0.2, 0.4, 0.1);
        }
        level.destroyBlock(pos, false);
    }

    private static void checkCrash(Level subLevel, BlockPos localPos, Level parentLevel, BlockPos parentPos,
            Vector3dc vel) {
        if (vel == null)
            return;
        double speed = vel.length();
        if (speed < 0.35)
            return;

        net.minecraft.world.level.block.state.BlockState parentState = parentLevel.getBlockState(parentPos);
        if (parentState.isSolid() && !parentState.is(net.minecraft.tags.BlockTags.LEAVES)) {
            subLevel.destroyBlock(localPos, false);
            parentLevel.playSound(null, parentPos, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.BLOCKS, 1.0F, 1.5F);
            parentLevel.playSound(null, parentPos, net.minecraft.sounds.SoundEvents.WOOL_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.8F);
        }
    }

    private static org.joml.Vector3d applyForce(SubLevelAccess sub, double forceY) {
        Object handle = SablePhysicsHelper.getHandle(sub);
        if (handle == null)
            return null;
        SablePhysicsHelper.wakeUp(handle);

        double velY = 0;
        Vector3dc vel = SablePhysicsHelper.getVelocity(handle);
        if (vel != null)
            velY = vel.y();

        double finalForce = (Math.abs(velY) < 0.01 && forceY < 0) ? forceY * 0.1 : forceY;

        Vector3d forceVec = new Vector3d(0, finalForce, 0);
        sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(forceVec);

        SablePhysicsHelper.applyLinearImpulse(handle, forceVec);
        return forceVec;
    }

    private java.util.List<FloaterBlockEntity> cachedCluster;
    private long clusterCacheTick = -1;

    public java.util.List<FloaterBlockEntity> getCluster() {
        if (level == null) return java.util.List.of(this);
        long tick = level.getGameTime();
        if (cachedCluster != null && tick - clusterCacheTick < 5) return cachedCluster;
        java.util.List<FloaterBlockEntity> cluster = new java.util.ArrayList<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof FloaterBlockEntity be) {
                cluster.add(be);
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next) && level.getBlockState(next).getBlock() == CreateSubmarine.FLOATER.get()) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        cachedCluster = cluster;
        clusterCacheTick = tick;
        return cluster;
    }

    private FloaterBlockEntity getMaster() {
        java.util.List<FloaterBlockEntity> cluster = getCluster();
        FloaterBlockEntity master = this;
        for (FloaterBlockEntity be : cluster) {
            if (be.worldPosition.compareTo(master.worldPosition) < 0) {
                master = be;
            }
        }
        return master;
    }

    @Override
    public void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel sub, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double timeStep) {
        if (this.recordedForceVec == null) return;
        if (this != getMaster()) return;

        if (sub.isTrackingIndividualQueuedForces()) {
            dev.ryanhcode.sable.api.physics.force.QueuedForceGroup forceGroup = sub.getOrCreateQueuedForceGroup(com.maxenonyme.createsubmarine.CreateSubmarine.FLOATER_FORCE_GROUP.get());
            org.joml.Vector3d totalForce = new org.joml.Vector3d();
            org.joml.Vector3d centerPos = new org.joml.Vector3d();
            java.util.List<FloaterBlockEntity> cluster = getCluster();
            int count = 0;
            for (FloaterBlockEntity be : cluster) {
                if (be.recordedForceVec != null) {
                    totalForce.add(be.recordedForceVec);
                    centerPos.add(be.worldPosition.getX() + 0.5, be.worldPosition.getY() + 0.5, be.worldPosition.getZ() + 0.5);
                    be.recordedForceVec = null;
                    count++;
                }
            }
            if (count > 0) {
                centerPos.div(count);
                org.joml.Vector3d recordVec = totalForce.mul(20.0 * timeStep);
                forceGroup.recordPointForce(centerPos, recordVec);
            }
        } else {
            for (FloaterBlockEntity be : getCluster()) {
                be.recordedForceVec = null;
            }
        }
    }
}
