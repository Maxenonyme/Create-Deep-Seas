package com.maxenonyme.createsubmarine.submarine.block.entity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
public class BallastTankBlockEntity extends BlockEntity implements IHaveGoggleInformation, dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
    private org.joml.Vector3d recordedForceVec = null;
    private static final int    CAPACITY        = 8000;
    private static final double MAX_ACCEL_LIMIT = 0.2;
    private static long lastClearTick = -1;
    private static final Map<UUID, Double> TICK_TOTAL_FORCE = new HashMap<>();
    public final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    private List<BallastTankBlockEntity> cachedCluster;
    private long clusterCacheTick = -1;
    public BallastTankBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BALLAST_TANK_BE.get(), pos, state);
    }
    private List<BallastTankBlockEntity> getCluster() {
        if (level == null) return List.of(this);
        long tick = level.getGameTime();
        if (cachedCluster != null && tick - clusterCacheTick < 5) return cachedCluster;
        List<BallastTankBlockEntity> cluster = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof BallastTankBlockEntity be) {
                cluster.add(be);
                for (Direction dir : Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (!visited.contains(next) && level.getBlockState(next).getBlock() == CreateSubmarine.BALLAST_TANK.get()) {
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
    private boolean checkVentConnection(Direction side) {
        if (level == null) return false;
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> clusterPositions = new HashSet<>();
        for (BallastTankBlockEntity be : getCluster()) {
            clusterPositions.add(be.getBlockPos());
        }
        for (BallastTankBlockEntity be : getCluster()) {
            for (Direction d : Direction.values()) {
                BlockPos n = be.getBlockPos().relative(d);
                if (!clusterPositions.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        int maxDepth = 60;
        int count = 0;
        while (!queue.isEmpty() && count < maxDepth) {
            BlockPos pos = queue.poll();
            count++;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BallastVentBlockEntity) return true;
            BlockState state = level.getBlockState(pos);
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id != null && id.getNamespace().equals("create") &&
                (id.getPath().contains("pump") || id.getPath().contains("pipe") || id.getPath().contains("valve"))) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }
    public int getClusterTotalCapacity() {
        return getCluster().size() * CAPACITY;
    }

    public int getClusterTotalAmount() {
        int total = 0;
        for (BallastTankBlockEntity be : getCluster()) total += be.tank.getFluidAmount();
        return total;
    }

    public int fillCluster(int amount, FluidAction action) {
        int filled = 0, toFill = amount;
        for (BallastTankBlockEntity be : getCluster()) {
            int added = be.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, toFill), action);
            filled += added;
            toFill -= added;
            if (toFill <= 0) break;
        }
        return filled;
    }

    public int drainCluster(int amount, FluidAction action) {
        int drained = 0, toDrain = amount;
        for (BallastTankBlockEntity be : getCluster()) {
            FluidStack stack = be.tank.drain(toDrain, action);
            drained += stack.getAmount();
            toDrain -= stack.getAmount();
            if (toDrain <= 0) break;
        }
        return drained;
    }

    public IFluidHandler getClusterFluidHandler(Direction side) {
        return new IFluidHandler() {
            private long lastCheckTick = -1;
            private boolean isVent = false;
            private int getMaxRate() {
                if (level != null) {
                    long tick = level.getGameTime();
                    if (tick - lastCheckTick > 10) {
                        isVent = checkVentConnection(side);
                        lastCheckTick = tick;
                    }
                }
                return isVent ? Integer.MAX_VALUE : 81;
            }
            @Override public int getTanks() { return 1; }
            @Override public @NotNull FluidStack getFluidInTank(int tank) {
                int total = 0;
                for (BallastTankBlockEntity be : getCluster()) total += be.tank.getFluidAmount();
                return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, total);
            }
            @Override public int getTankCapacity(int tank) {
                return getCluster().size() * CAPACITY;
            }
            @Override public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
            }
            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
                int filled = 0;
                int maxRate = getMaxRate();
                int toFill = Math.min(resource.getAmount(), maxRate);
                if (toFill <= 0) return 0;
                for (BallastTankBlockEntity be : getCluster()) {
                    int added = be.tank.fill(resource.copyWithAmount(toFill), action);
                    filled += added;
                    toFill -= added;
                    if (toFill <= 0) break;
                }
                return filled;
            }
            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !isFluidValid(0, resource)) return FluidStack.EMPTY;
                return drain(resource.getAmount(), action);
            }
            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                int drained = 0;
                int maxRate = getMaxRate();
                int toDrain = Math.min(maxDrain, maxRate);
                if (toDrain <= 0) return FluidStack.EMPTY;
                for (BallastTankBlockEntity be : getCluster()) {
                    FluidStack stack = be.tank.drain(toDrain, action);
                    drained += stack.getAmount();
                    toDrain -= stack.getAmount();
                    if (toDrain <= 0) break;
                }
                return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, drained);
            }
        };
    }
    public static void serverTick(Level level, BlockPos pos, BallastTankBlockEntity be) {
        if (level.isClientSide()) return;
        be.shareFluidWithNeighbors();
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null) return;
        double fillRatio = (double) be.tank.getFluidAmount() / CAPACITY;
        long gameTick = level.getGameTime();
        if (gameTick != lastClearTick) {
            TICK_TOTAL_FORCE.clear();
            lastClearTick = gameTick;
        }
        UUID subId = sub.getUniqueId();

        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);

        Object handle = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.getHandle(sub);
        Vector3dc currentVel = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.getVelocity(handle);
        double currentVelY = (currentVel != null) ? currentVel.y() : 0;

        Level parentLevel = com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = sl.getPlot();
            if (plot != null && sl.getLevel() != null) {
                parentLevel = sl.getLevel();
                dev.ryanhcode.sable.companion.math.BoundingBox3ic bounds = plot.getBoundingBox();
                com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.register(
                    sub.getUniqueId(), sub, parentLevel,
                    new com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.PlotBounds(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ())
                );
            }
        }

        if (parentLevel == null) return;

        BlockPos parentPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        double localWaterSurfaceY = -999.0;

        net.minecraft.world.level.material.FluidState fluidState = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, parentPos);
        if (fluidState.is(net.minecraft.tags.FluidTags.WATER)) {
            float h = fluidState.getHeight(parentLevel, parentPos);
            localWaterSurfaceY = parentPos.getY() + h + countWaterAbove(parentLevel, parentPos);
        } else {
            BlockPos belowPos = parentPos.below();
            net.minecraft.world.level.material.FluidState belowFluid = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, belowPos);
            if (belowFluid.is(net.minecraft.tags.FluidTags.WATER)) {
                float h = belowFluid.getHeight(parentLevel, belowPos);
                localWaterSurfaceY = belowPos.getY() + h + countWaterAbove(parentLevel, belowPos);
            }
        }

        double depth = localWaterSurfaceY - (worldPos.y - 0.5);
        boolean isUnderWater = (depth > 0.0);

        if (!isUnderWater) return;

        double submergedRatio = Math.max(0.0, Math.min(1.0, depth));

        double maxSpeed = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.BALLAST_VERTICAL_SPEED.get();
        double baseTarget = (0.5 - fillRatio) * 2.0 * maxSpeed;
        double distanceToSurface = localWaterSurfaceY - worldPos.y;
        double targetVelY;
        if (baseTarget > 0) {
            targetVelY = Math.max(-0.1, Math.min(baseTarget, distanceToSurface));
        } else {
            targetVelY = baseTarget;
        }

        // cap on both sides so the climb doesn't stall: matching the target used to zero the force and let gravity win
        double perceivedVelY = Math.max(-0.2, Math.min(0.2, currentVelY));
        double errorY = targetVelY - perceivedVelY;
        double mass = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.readMass(sub);

        double forceMult = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.BALLAST_FORCE_MULTIPLIER.get();
        double liftPerTank = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.BALLAST_LIFT_PER_TANK.get();
        double forceToApply = errorY * liftPerTank * 0.16 * forceMult * submergedRatio;

        if (Double.isFinite(forceToApply)) {
            applyForce(sub, forceToApply);
            double finalForce = (Math.abs(currentVelY) < 0.01 && forceToApply < 0) ? forceToApply * 0.1 : forceToApply;
            org.joml.Vector3d forceVec = new org.joml.Vector3d(0, finalForce, 0);
            sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(forceVec);
            be.recordedForceVec = forceVec;
        }

        if (subId != null && !TICK_TOTAL_FORCE.containsKey(subId)) {
            TICK_TOTAL_FORCE.put(subId, 1.0);
            if (handle != null && currentVel != null) {
                double dragCoefficient = 0.035;
                double dragX = -currentVel.x() * mass * dragCoefficient;
                double dragZ = -currentVel.z() * mass * dragCoefficient;
                if (Math.abs(dragX) > 0.01 || Math.abs(dragZ) > 0.01) {
                    Vector3d dragVec = new Vector3d(dragX, 0, dragZ);
                    sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(dragVec);
                    com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.applyLinearImpulse(handle, dragVec);
                }
            }
        }
    }
    private void shareFluidWithNeighbors() {
        if (level == null || level.isClientSide) return;
        int myAmount = tank.getFluidAmount();
        if (myAmount <= 2) return;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockEntity otherBE = level.getBlockEntity(neighborPos);
            if (!(otherBE instanceof BallastTankBlockEntity other)) continue;
            int otherAmount = other.tank.getFluidAmount();
            if (myAmount > otherAmount + 2) {
                int toTransfer = Math.max(1, (myAmount - otherAmount) / 2);
                FluidStack drained = tank.drain(toTransfer, FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    other.tank.fill(drained, FluidAction.EXECUTE);
                    myAmount -= drained.getAmount();
                }
            }
        }
    }
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_submarine.gui.goggles.ballast_status").withStyle(ChatFormatting.GRAY)));
        List<BallastTankBlockEntity> cluster = getCluster();
        int totalWater = 0;
        for (BallastTankBlockEntity be : cluster) totalWater += be.tank.getFluidAmount();
        int totalCapacity = cluster.size() * CAPACITY;
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_submarine.gui.goggles.water").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(": " + totalWater + " / " + totalCapacity + " mB").withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_submarine.gui.goggles.air").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(": " + (totalCapacity - totalWater) + " / " + totalCapacity + " mB").withStyle(ChatFormatting.WHITE)));
        if (cluster.size() > 1) {
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.connected_tanks").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(": " + cluster.size()).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }
    private static int countWaterAbove(Level level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < pos.getY() + 1 + 200; y++) {
            m.set(pos.getX(), y, pos.getZ());
            if (com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(level, m).is(net.minecraft.tags.FluidTags.WATER)) {
                depth++;
            } else {
                break;
            }
        }
        return depth;
    }

    private static void applyForce(SubLevelAccess sub, double forceY) {
        Object handle = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.getHandle(sub);
        if (handle == null) return;
        com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.wakeUp(handle);
        double velY = 0;
        Vector3dc vel = com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.getVelocity(handle);
        if (vel != null) velY = vel.y();
        double finalForce = (Math.abs(velY) < 0.01 && forceY < 0) ? forceY * 0.1 : forceY;
        Vector3d forceVec = new Vector3d(0, finalForce, 0);
        sub.logicalPose().orientation().conjugate(new org.joml.Quaterniond()).transform(forceVec);
        com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper.applyLinearImpulse(handle, forceVec);
    }
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
    private BallastTankBlockEntity getMaster() {
        List<BallastTankBlockEntity> cluster = getCluster();
        BallastTankBlockEntity master = this;
        for (BallastTankBlockEntity be : cluster) {
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
            dev.ryanhcode.sable.api.physics.force.QueuedForceGroup forceGroup = sub.getOrCreateQueuedForceGroup(com.maxenonyme.createsubmarine.CreateSubmarine.BALLAST_FORCE_GROUP.get());
            org.joml.Vector3d totalForce = new org.joml.Vector3d();
            org.joml.Vector3d centerPos = new org.joml.Vector3d();
            List<BallastTankBlockEntity> cluster = getCluster();
            int count = 0;
            for (BallastTankBlockEntity be : cluster) {
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
            for (BallastTankBlockEntity be : getCluster()) {
                be.recordedForceVec = null;
            }
        }
    }
}
