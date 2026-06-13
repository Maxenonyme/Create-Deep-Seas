package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.util.ILargeWaterWheel;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = LargeWaterWheelBlockEntity.class, remap = false)
public abstract class LargeWaterWheelPropulsionMixin extends GeneratingKineticBlockEntity implements BlockEntitySubLevelActor, ILargeWaterWheel {

    private float createsubmarine$shipSpeedOffset = 0f;

    public LargeWaterWheelPropulsionMixin(net.minecraft.world.level.block.entity.BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public float createsubmarine$getShipSpeedOffset() {
        return createsubmarine$shipSpeedOffset;
    }

    @Override
    public void createsubmarine$setShipSpeedOffset(float speed) {
        this.createsubmarine$shipSpeedOffset = speed;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (this.level == null || subLevel.getPlot() == null) return;

        Vector3d worldPos = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        subLevel.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        ServerLevel parentLevel = subLevel.getLevel();
        if (parentLevel == null) return;

        boolean inWater = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(wPos.getX() + dx, wPos.getY() + dy, wPos.getZ() + dz);
                    if (CompartmentTracker.realFluidState(parentLevel, m).is(FluidTags.WATER)) {
                        inWater = true;
                        break;
                    }
                }
                if (inWater) break;
            }
            if (inWater) break;
        }
        
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.AXIS)) return;
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);

        if (!inWater) {
            if (createsubmarine$shipSpeedOffset != 0) {
                createsubmarine$shipSpeedOffset = 0;
                this.updateGeneratedRotation();
            }
            return;
        }

        Vector3d localVelocity = new Vector3d();
        handle.getLinearVelocity(localVelocity);
        subLevel.logicalPose().orientation().transformInverse(localVelocity);

        float newRPM = 0;
        if (axis == Direction.Axis.X) {
            if (localVelocity.z > 0.5f) newRPM = 16.0f;
            else if (localVelocity.z < -0.5f) newRPM = -16.0f;
        } else if (axis == Direction.Axis.Z) {
            if (localVelocity.x > 0.5f) newRPM = -16.0f;
            else if (localVelocity.x < -0.5f) newRPM = 16.0f;
        }

        if (newRPM != createsubmarine$shipSpeedOffset) {
            createsubmarine$shipSpeedOffset = newRPM;
            this.updateGeneratedRotation();
        }

        float speed = this.getSpeed();
        if (Math.abs(speed) < 1.0f) return;

        if (Math.abs(speed) <= Math.abs(createsubmarine$shipSpeedOffset) + 0.1f) return;

        double thrust = AeroConfig.server().physics.andesitePropellerThrust.get() * SubmarineConfig.SUBMARINE_PROPELLER_POWER_MULTIPLIER.get();
        double speedMult = speed / 16.0;
        double finalThrust = thrust * speedMult;

        Vector3d localThrust = new Vector3d();
        if (axis == Direction.Axis.X) {
            localThrust.set(0, 0, finalThrust);
        } else if (axis == Direction.Axis.Z) {
            localThrust.set(-finalThrust, 0, 0);
        } else {
            return;
        }

        Vector3d impulseLocal = new Vector3d(localThrust).mul(timeStep);
        Vector3d pointLocal = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);

        handle.applyImpulseAtPoint(pointLocal, impulseLocal);

        if (parentLevel.random.nextFloat() < 0.05f) {
            Vector3d bubbleLocal = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() - 1.0, worldPosition.getZ() + 0.5);
            if (axis == Direction.Axis.X) {
                bubbleLocal.z += (parentLevel.random.nextFloat() - 0.5) * 2.0;
                bubbleLocal.x += (parentLevel.random.nextFloat() - 0.5) * 0.8;
            } else if (axis == Direction.Axis.Z) {
                bubbleLocal.x += (parentLevel.random.nextFloat() - 0.5) * 2.0;
                bubbleLocal.z += (parentLevel.random.nextFloat() - 0.5) * 0.8;
            }
            Vector3d bubbleWorld = subLevel.logicalPose().transformPosition(new Vector3d(bubbleLocal));
            parentLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, bubbleWorld.x, bubbleWorld.y, bubbleWorld.z, 2, 0.2, 0.2, 0.2, 0.02);

            Vector3d splashLocal = new Vector3d(bubbleLocal);
            splashLocal.y += 2.0;
            Vector3d splashWorld = subLevel.logicalPose().transformPosition(new Vector3d(splashLocal));
            parentLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, splashWorld.x, splashWorld.y, splashWorld.z, 1, 0.3, 0.1, 0.3, 0.05);
        }
    }
}
