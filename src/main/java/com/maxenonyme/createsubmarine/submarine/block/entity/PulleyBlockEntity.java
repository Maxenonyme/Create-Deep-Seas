package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.PulleyBlock;
import com.maxenonyme.createsubmarine.submarine.system.SteelCablePhysicsSystem;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.List;

public class PulleyBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static double getMaxSlideSpeed() {
        return com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.PULLEY_MAX_SLIDE_SPEED.get();
    }

    public static final java.util.Set<java.util.UUID> SLIDING_SUBLEVELS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Map<java.util.UUID, java.util.Set<PulleyBlockEntity>> SLIDERS_BY_SUBLEVEL = new java.util.concurrent.ConcurrentHashMap<>();

    public float clientWheelAngle = 0f;
    public long clientLastNanos = 0L;

    private int stressTicks = 0;
    private transient boolean lastTickClampedAndConnected = false;

    public PulleyBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.PULLEY_BE.get(), pos, state);
    }

    public float getHeat() {
        if (this.stressTicks <= 400) {
            return 0.0f;
        }
        return Math.min(1.0f, (this.stressTicks - 400) / 200.0f);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            dev.ryanhcode.sable.companion.SubLevelAccess sub = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(this.level, this.worldPosition);
            if (sub != null) {
                java.util.Set<PulleyBlockEntity> peers = SLIDERS_BY_SUBLEVEL.get(sub.getUniqueId());
                if (peers != null) {
                    peers.remove(this);
                }
                boolean anyConnected = false;
                if (peers != null) {
                    for (PulleyBlockEntity peer : peers) {
                        if (peer != this && !peer.isRemoved() && peer.lastTickClampedAndConnected) {
                            anyConnected = true;
                            break;
                        }
                    }
                }
                if (!anyConnected) {
                    SLIDING_SUBLEVELS.remove(sub.getUniqueId());
                }
            }
        }
    }

    private void updateSlidingStatus(ServerSubLevel subLevel) {
        boolean anyConnected = false;
        java.util.Set<PulleyBlockEntity> peers = SLIDERS_BY_SUBLEVEL.get(subLevel.getUniqueId());
        if (peers != null) {
            for (PulleyBlockEntity peer : peers) {
                if (peer.isRemoved()) continue;
                if (peer.lastTickClampedAndConnected) {
                    anyConnected = true;
                    break;
                }
            }
        }
        if (anyConnected) {
            SLIDING_SUBLEVELS.add(subLevel.getUniqueId());
        } else {
            SLIDING_SUBLEVELS.remove(subLevel.getUniqueId());
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (subLevel.getPlot() == null) return;

        BlockState state = getBlockState();
        if (!state.hasProperty(PulleyBlock.FACING)) return;
        net.minecraft.core.Direction facing = state.getValue(PulleyBlock.FACING);

        java.util.Set<PulleyBlockEntity> peers = SLIDERS_BY_SUBLEVEL.computeIfAbsent(subLevel.getUniqueId(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        peers.removeIf(peer -> {
            if (peer == null || peer.isRemoved() || peer.getLevel() == null) {
                return true;
            }
            try {
                return peer.getLevel().getBlockEntity(peer.getBlockPos()) != peer;
            } catch (Exception e) {
                return true;
            }
        });
        peers.add(this);

        Vector3d localSnapPoint = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        localSnapPoint.add(0.0, 0.25, 0.0);
        net.minecraft.core.Direction wheelDir = facing.getAxis().isHorizontal() ? facing.getClockWise() : net.minecraft.core.Direction.EAST;
        localSnapPoint.add(wheelDir.getStepX() * 0.95, wheelDir.getStepY() * 0.95, wheelDir.getStepZ() * 0.95);

        PulleyBlockEntity partner = findClampPartner(subLevel);
        if (partner == null) {
            if (state.hasProperty(PulleyBlock.CONNECTED) && state.getValue(PulleyBlock.CONNECTED)) {
                level.setBlock(worldPosition, state.setValue(PulleyBlock.CONNECTED, false), 3);
            }
            float oldHeat = getHeat();
            this.stressTicks = Math.max(0, this.stressTicks - 2);
            this.lastTickClampedAndConnected = false;
            updateSlidingStatus(subLevel);
            if ((int)(oldHeat * 20) != (int)(getHeat() * 20)) {
                this.setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return;
        }

        if (worldPosition.compareTo(partner.getBlockPos()) > 0) {
            return;
        }

        Vector3d attachmentPointWorld = new Vector3d(localSnapPoint);
        subLevel.logicalPose().transformPosition(attachmentPointWorld);

        ServerLevel parentLevel = subLevel.getLevel();
        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(parentLevel);
        if (ropeManager == null) return;

        ServerRopeStrand closestStrand = null;
        double closestDistSq = Double.MAX_VALUE;
        Vector3d closestPoint = null;
        Vector3d segmentA = null;
        Vector3d segmentB = null;

        for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
            if (!strand.isActive()) continue;
            if (!SteelCablePhysicsSystem.isSteelCable(strand, parentLevel)) continue;

            List<Vector3d> points = strand.getPoints();
            for (int i = 0; i < points.size() - 1; i++) {
                Vector3d a = points.get(i);
                Vector3d b = points.get(i + 1);
                Vector3d c = getClosestPointOnSegment(a, b, attachmentPointWorld);
                double dSq = attachmentPointWorld.distanceSquared(c);
                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closestPoint = c;
                    segmentA = a;
                    segmentB = b;
                    closestStrand = strand;
                }
            }
        }

        boolean isConnected = (closestPoint != null && closestDistSq < 16.0);

        if (state.hasProperty(PulleyBlock.CONNECTED) && state.getValue(PulleyBlock.CONNECTED) != isConnected) {
            level.setBlock(worldPosition, state.setValue(PulleyBlock.CONNECTED, isConnected), 3);
        }
        BlockState partnerState = partner.getBlockState();
        if (partnerState.hasProperty(PulleyBlock.CONNECTED) && partnerState.getValue(PulleyBlock.CONNECTED) != isConnected) {
            partner.getLevel().setBlock(partner.getBlockPos(), partnerState.setValue(PulleyBlock.CONNECTED, isConnected), 3);
        }

        double speed = 0.0;
        boolean atMaxSpeed = false;

        if (isConnected) {
            Vector3d upWorld = new Vector3d(0, 1, 0);
            subLevel.logicalPose().orientation().transform(upWorld);

            Vector3d u = new Vector3d(segmentB).sub(segmentA);
            double uLen = u.length();
            if (uLen < 1e-6) {
                u.set(0, 1, 0);
            } else {
                u.mul(1.0 / uLen);
            }

            if (u.dot(upWorld) < 0) {
                u.negate();
            }

            Vector3d linearVelocity = new Vector3d();
            handle.getLinearVelocity(linearVelocity);
            double dot = linearVelocity.dot(u);
            speed = Math.abs(dot);

            double maxSlideSpeed = getMaxSlideSpeed();
            double clampedDot = Math.signum(dot) * Math.min(Math.abs(dot), maxSlideSpeed);
            atMaxSpeed = Math.abs(dot) >= maxSlideSpeed;
            dot = clampedDot;
            speed = Math.abs(clampedDot);

            boolean isPrimary = true;
            if (peers != null) {
                for (PulleyBlockEntity peer : peers) {
                    if (peer == this || peer.isRemoved()) continue;
                    if (peer.lastTickClampedAndConnected) {
                        if (peer.getBlockPos().compareTo(this.worldPosition) < 0) {
                            isPrimary = false;
                            break;
                        }
                    }
                }
            }

            if (isPrimary) {
                PulleyBlockEntity secondary = null;
                if (peers != null) {
                    for (PulleyBlockEntity peer : peers) {
                        if (peer != this && peer != partner && !peer.isRemoved() && peer.lastTickClampedAndConnected) {
                            secondary = peer;
                            break;
                        }
                    }
                }

                Quaterniond newOrientation = null;

                if (secondary != null) {
                    Vector3d L2 = getLocalSnapPoint(secondary);
                    Vector3d W2_world = new Vector3d(L2);
                    subLevel.logicalPose().transformPosition(W2_world);

                    double closestDistSq2 = Double.MAX_VALUE;
                    Vector3d closestPoint2 = null;

                    for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
                        if (!strand.isActive()) continue;
                        if (strand == closestStrand) continue;
                        if (!SteelCablePhysicsSystem.isSteelCable(strand, parentLevel)) continue;

                        List<Vector3d> points = strand.getPoints();
                        for (int i = 0; i < points.size() - 1; i++) {
                            Vector3d a = points.get(i);
                            Vector3d b = points.get(i + 1);
                            Vector3d c = getClosestPointOnSegment(a, b, W2_world);
                            double dSq = W2_world.distanceSquared(c);
                            if (dSq < closestDistSq2) {
                                closestDistSq2 = dSq;
                                closestPoint2 = c;
                            }
                        }
                    }

                    if (closestPoint2 != null && closestDistSq2 < 16.0) {
                        Vector3d V_local = new Vector3d(L2).sub(localSnapPoint);
                        Vector3d V_world = new Vector3d(closestPoint2).sub(closestPoint);

                        Quaterniond R_tilt = getRotationTo(new Vector3d(0, 1, 0), u);
                        Vector3d V_temp = new Vector3d(V_local);
                        R_tilt.transform(V_temp);

                        Quaterniond R_yaw = getRotationTo(V_temp, V_world);
                        newOrientation = new Quaterniond(R_yaw).mul(R_tilt);
                    }
                }

                if (newOrientation == null) {
                    Quaterniond rot = getRotationTo(upWorld, u);
                    newOrientation = new Quaterniond(rot).mul(subLevel.logicalPose().orientation());
                }

                if (newOrientation.lengthSquared() < 1e-6) {
                    newOrientation.set(0, 0, 0, 1);
                } else {
                    newOrientation.normalize();
                }

                Vector3d localOffset = new Vector3d(attachmentPointWorld).sub(subLevel.logicalPose().position());
                subLevel.logicalPose().orientation().conjugate(new Quaterniond()).transform(localOffset);

                Vector3d rotatedOffset = new Vector3d(localOffset);
                newOrientation.transform(rotatedOffset);
                Vector3d newSubPos = new Vector3d(closestPoint).sub(rotatedOffset);

                if (!Double.isNaN(newOrientation.x) && !Double.isNaN(newOrientation.y) && !Double.isNaN(newOrientation.z) && !Double.isNaN(newOrientation.w) &&
                    !Double.isNaN(newSubPos.x) && !Double.isNaN(newSubPos.y) && !Double.isNaN(newSubPos.z)) {

                    subLevel.logicalPose().position().set(newSubPos);
                    subLevel.logicalPose().orientation().set(newOrientation);
                    subLevel.updateLastPose();

                    ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(parentLevel);
                    if (container != null) {
                        container.physicsSystem().getPipeline().teleport(subLevel, newSubPos, newOrientation);
                    }
                }

                Vector3d angularVelocity = new Vector3d();
                handle.getAngularVelocity(angularVelocity);

                Vector3d alignedVelocity = new Vector3d(u).mul(dot);

                Vector3d velocityDiff = new Vector3d(alignedVelocity).sub(linearVelocity);
                Vector3d angularDiff = new Vector3d(angularVelocity).negate();
                handle.addLinearAndAngularVelocity(velocityDiff, angularDiff);
            }
        }

        float oldHeat = getHeat();
        if (isConnected && atMaxSpeed) {
            this.stressTicks = Math.min(600, this.stressTicks + 1);
        } else {
            this.stressTicks = Math.max(0, this.stressTicks - (speed < 0.1 ? 2 : 1));
        }

        partner.stressTicks = this.stressTicks;
        this.lastTickClampedAndConnected = isConnected;
        partner.lastTickClampedAndConnected = isConnected;

        updateSlidingStatus(subLevel);

        if ((int)(oldHeat * 20) != (int)(getHeat() * 20)) {
            this.setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            partner.setChanged();
            partner.getLevel().sendBlockUpdated(partner.getBlockPos(), partner.getBlockState(), partner.getBlockState(), 3);
        }

        if (this.stressTicks >= 600 && level instanceof ServerLevel serverLevel) {
            BlockPos selfPos = worldPosition;
            BlockPos partnerPos = partner.getBlockPos();
            ServerLevel partnerLevel = (ServerLevel) partner.getLevel();
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, selfPos.getX() + 0.5, selfPos.getY() + 0.5, selfPos.getZ() + 0.5, 20, 0.15, 0.15, 0.15, 0.05);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, selfPos.getX() + 0.5, selfPos.getY() + 0.5, selfPos.getZ() + 0.5, 10, 0.15, 0.15, 0.15, 0.05);
                partnerLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, partnerPos.getX() + 0.5, partnerPos.getY() + 0.5, partnerPos.getZ() + 0.5, 20, 0.15, 0.15, 0.15, 0.05);
                partnerLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, partnerPos.getX() + 0.5, partnerPos.getY() + 0.5, partnerPos.getZ() + 0.5, 10, 0.15, 0.15, 0.15, 0.05);
                serverLevel.destroyBlock(selfPos, true);
                partnerLevel.destroyBlock(partnerPos, true);
            }));
        }
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = pkt.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StressTicks", this.stressTicks);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.stressTicks = tag.getInt("StressTicks");
    }

    private PulleyBlockEntity findClampPartner(ServerSubLevel subLevel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(PulleyBlock.FACING)) return null;
        net.minecraft.core.Direction facing = state.getValue(PulleyBlock.FACING);
        net.minecraft.core.Direction wheelDir = facing.getAxis().isHorizontal() ? facing.getClockWise() : net.minecraft.core.Direction.EAST;
        BlockPos partnerPos = worldPosition.relative(wheelDir, 2);
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(partnerPos);
        if (be instanceof PulleyBlockEntity partner && !partner.isRemoved()) {
            BlockState partnerState = partner.getBlockState();
            if (partnerState.hasProperty(PulleyBlock.FACING)) {
                net.minecraft.core.Direction partnerFacing = partnerState.getValue(PulleyBlock.FACING);
                net.minecraft.core.Direction partnerWheelDir = partnerFacing.getAxis().isHorizontal() ? partnerFacing.getClockWise() : net.minecraft.core.Direction.EAST;
                if (partnerWheelDir == wheelDir.getOpposite()) {
                    return partner;
                }
            }
        }
        return null;
    }

    private Vector3d getClosestPointOnSegment(Vector3d a, Vector3d b, Vector3d p) {
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

    private Quaterniond getRotationTo(Vector3d a, Vector3d b) {
        if (a.lengthSquared() < 1e-6 || b.lengthSquared() < 1e-6) {
            return new Quaterniond();
        }
        double dot = a.dot(b);
        if (Double.isNaN(dot)) {
            return new Quaterniond();
        }
        if (dot < -0.999999) {
            Vector3d ortho = new Vector3d(1, 0, 0);
            if (Math.abs(a.x) > 0.9) {
                ortho.set(0, 1, 0);
            }
            Vector3d axis = new Vector3d(a).cross(ortho);
            if (axis.lengthSquared() < 1e-6) {
                return new Quaterniond();
            }
            axis.normalize();
            return new Quaterniond(axis.x, axis.y, axis.z, 0.0);
        } else if (dot > 0.999999) {
            return new Quaterniond();
        } else {
            Vector3d axis = new Vector3d(a).cross(b);
            double w = 1.0 + dot;
            Quaterniond q = new Quaterniond(axis.x, axis.y, axis.z, w);
            if (q.lengthSquared() < 1e-6) {
                return new Quaterniond();
            }
            q.normalize();
            return q;
        }
    }

    private static Vector3d getLocalSnapPoint(PulleyBlockEntity be) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PulleyBlock.FACING)) {
            return new Vector3d(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.75, be.getBlockPos().getZ() + 0.5);
        }
        net.minecraft.core.Direction facing = state.getValue(PulleyBlock.FACING);
        net.minecraft.core.Direction wheelDir = facing.getAxis().isHorizontal() ? facing.getClockWise() : net.minecraft.core.Direction.EAST;
        return new Vector3d(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.75, be.getBlockPos().getZ() + 0.5)
            .add(wheelDir.getStepX() * 0.95, wheelDir.getStepY() * 0.95, wheelDir.getStepZ() * 0.95);
    }
}
