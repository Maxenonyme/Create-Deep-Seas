package com.maxenonyme.AbyssDimension.block.entity;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.UUID;

public class SubmarineLianaBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private UUID parentId;
    private UUID childId;
    private UUID seedId;
    private boolean isController;
    private int worldLightLevel = 10;

    private final ForceTotal forceTotal = new ForceTotal();
    private final ForceTotal parentForceTotal = new ForceTotal();
    private final Vector3d totalForce = new Vector3d();
    private final Vector3d totalTorque = new Vector3d();
    private final Vector3d linearVelocity = new Vector3d();
    private final Vector3d angularVelocity = new Vector3d();
    private final Vector3d tempVec = new Vector3d();
    private final Vector3d tempVec2 = new Vector3d();
    private final Vector3d upA = new Vector3d();
    private final Vector3d upB = new Vector3d();

    public SubmarineLianaBlockEntity(BlockPos pos, BlockState state) {
        super(LianaRegistry.LIANA_BE.get(), pos, state);
    }

    public void setParent(UUID parentId) {
        this.parentId = parentId;
        this.setChanged();
    }

    public void setChild(UUID childId) {
        this.childId = childId;
        this.setChanged();
    }

    public void setController(boolean controller) {
        this.isController = controller;
        this.setChanged();
    }

    public void setSeed(UUID seedId) {
        this.seedId = seedId;
        this.setChanged();
    }

    public int getWorldLightLevel() {
        return worldLightLevel;
    }

    public void setWorldLightLevel(int level) {
        if (this.worldLightLevel != level) {
            this.worldLightLevel = level;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("WorldLight", worldLightLevel);
        return tag;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        handle.getLinearVelocity(linearVelocity);
        handle.getAngularVelocity(angularVelocity);

        totalForce.set(0, 0, 0);
        totalTorque.set(0, 0, 0);

        double mass = subLevel.getMassTracker().getMass();
        double lianaBuoyancy = mass * (9.81 + 2.0);
        totalForce.add(0, lianaBuoyancy, 0);

        double waterDragCoeff = 2.0;
        tempVec.set(linearVelocity).mul(-waterDragCoeff);
        totalForce.add(tempVec);

        double swayFreq = 1.0;
        double swayAmp = 1.5;
        double time = subLevel.getLevel().getGameTime() * timeStep;
        double waveSway = Math.sin(time * swayFreq + worldPosition.getX() * 0.15 + worldPosition.getZ() * 0.15) * swayAmp;
        totalForce.add(waveSway, 0, 0);

        if (parentId != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            if (container != null) {
                ServerSubLevel parentSubLevel = (ServerSubLevel) container.getSubLevel(parentId);
                if (parentSubLevel != null) {
                    Quaterniond orientA = subLevel.logicalPose().orientation();
                    Quaterniond orientB = parentSubLevel.logicalPose().orientation();

                    upA.set(0, 1, 0);
                    orientA.transform(upA);

                    upB.set(0, 1, 0);
                    orientB.transform(upB);

                    double stiffness = 45.0;
                    upA.cross(upB, totalTorque).mul(stiffness);

                    RigidBodyHandle parentHandle = RigidBodyHandle.of(parentSubLevel);
                    if (parentHandle != null) {
                        parentForceTotal.reset();
                        tempVec2.set(totalTorque).negate();
                        parentForceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO, parentSubLevel.logicalPose().transformNormalInverse(tempVec2, tempVec2).mul(timeStep));
                        parentHandle.applyForcesAndReset(parentForceTotal);
                    }
                }
            }
        }

        Quaterniond orient = subLevel.logicalPose().orientation();

        upA.set(0, 1, 0);
        orient.transform(upA);

        Vector3d worldVertical = tempVec2.set(0, 1, 0);
        double cosTheta = upA.y;
        double theta = Math.acos(Math.max(-1.0, Math.min(1.0, cosTheta)));

        double verticalRestorationStiffness = 30.0;
        if (theta > 0.5236) {
            verticalRestorationStiffness += (theta - 0.5236) * 500.0;
        }

        upA.cross(worldVertical, tempVec).mul(verticalRestorationStiffness);
        totalTorque.add(tempVec);

        orient.transform(totalForce);

        forceTotal.reset();
        forceTotal.applyImpulseAtPoint(
                subLevel,
                JOMLConversion.atCenterOf(worldPosition),
                subLevel.logicalPose().transformNormalInverse(totalForce, tempVec).mul(timeStep)
        );
        forceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO, subLevel.logicalPose().transformNormalInverse(totalTorque, tempVec).mul(timeStep));
        handle.applyForcesAndReset(forceTotal);
    }


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Controller", isController);
        tag.putInt("WorldLight", worldLightLevel);
        if (parentId != null) tag.putUUID("ParentId", parentId);
        if (childId != null) tag.putUUID("ChildId", childId);
        if (seedId != null) tag.putUUID("SeedId", seedId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        isController = tag.getBoolean("Controller");
        if (tag.contains("WorldLight")) worldLightLevel = tag.getInt("WorldLight");
        if (tag.hasUUID("ParentId")) parentId = tag.getUUID("ParentId");
        if (tag.hasUUID("ChildId")) childId = tag.getUUID("ChildId");
        if (tag.hasUUID("SeedId")) seedId = tag.getUUID("SeedId");
    }
}
