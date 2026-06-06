package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public class SubmarinePropellerBlockEntity extends BasePropellerBlockEntity {

    public SubmarinePropellerBlockEntity(final BlockPos pos, final BlockState state) {
        super(com.maxenonyme.createsubmarine.CreateSubmarine.SUBMARINE_PROPELLER_BE.get(), pos, state);
    }

    @Override
    public double getConfigThrust() {
        if (isSubmerged()) {
            return com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.SUBMARINE_PROPELLER_POWER_MULTIPLIER.get() * AeroConfig.server().physics.andesitePropellerThrust.get();
        }
        return 0.0;
    }

    @Override
    public double getConfigAirflow() {
        if (isSubmerged()) {
            return com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.SUBMARINE_PROPELLER_POWER_MULTIPLIER.get() * AeroConfig.server().physics.andesitePropellerAirflow.get();
        }
        return 0.0;
    }

    @Override
    public float getRadius() {
        return 1.0f;
    }

    @Override
    public float getOffset() {
        return 3 / 16f;
    }

    private boolean isSubmerged() {
        if (level == null) {
            return false;
        }
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (sub == null) {
            return level.getFluidState(worldPosition).is(FluidTags.WATER);
        }
        Vector3d worldPos = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null) {
            return false;
        }
        return com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, wPos).is(FluidTags.WATER);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void onActiveTick() {
        if (this.prop != null) {
            this.prop.pushEntities();
        }
        if (level != null && level.isClientSide()) {
            if (isSubmerged()) {
                spawnSubmarineBubbles();
            } else if (this.prop != null) {
                this.prop.spawnParticles();
            }
        }
    }

    private void spawnSubmarineBubbles() {
        if (level == null) return;
        int particleCount = (int) (Math.abs(this.rotationSpeed) * 0.05f) + 1;
        float speed = (float)(this.getConfigAirflow() * getDirectionIndependentSpeed() / 20f);
        speed = net.minecraft.util.Mth.clamp(speed, -5f, 5f);

        net.minecraft.core.Direction dir = getBlockDirection();
        org.joml.Vector3d thrustDir = dev.ryanhcode.sable.companion.math.JOMLConversion.toJOML(net.minecraft.world.phys.Vec3.atLowerCornerOf(dir.getNormal()));

        dev.ryanhcode.sable.companion.SubLevelAccess subLevel = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, worldPosition);
        org.joml.Vector3d origin = new org.joml.Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        org.joml.Vector3d pos = new org.joml.Vector3d();
        org.joml.Vector3d mutSpeed = new org.joml.Vector3d();

        for (int i = 0; i < particleCount; i++) {
            double R = level.random.nextFloat() * getRadius();
            double angle = Math.PI * 2.0 * level.random.nextFloat();
            pos.set(Math.cos(angle) * R, getOffset(), Math.sin(angle) * R);
            dir.getRotation().transform(pos);
            pos.add(origin);


            double spawnOffset = 0.75 + level.random.nextFloat() * 0.5;
            pos.fma(Math.signum(speed) * spawnOffset, thrustDir);


            mutSpeed.set(thrustDir).mul(speed * 0.6 * (0.8 + level.random.nextFloat() * 0.4));

            if (subLevel != null) {
                subLevel.logicalPose().transformPosition(pos);
                subLevel.logicalPose().transformNormal(mutSpeed);
            }

            level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, mutSpeed.x, mutSpeed.y, mutSpeed.z);
        }
    }
}

