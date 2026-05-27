package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.BallastVentBlock;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class BallastVentBlockEntity extends KineticBlockEntity {
    private BallastTankBlockEntity cachedTank;
    private int scanCooldown = 0;

    public BallastVentBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BALLAST_VENT_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        scanCooldown--;
        float speed = getSpeed();
        if (Math.abs(speed) < 0.1f)
            return;
        if (scanCooldown <= 0) {
            cachedTank = findBallastTank();
            scanCooldown = 40;
        }
        if (cachedTank == null)
            return;

        int signal = level.getBestNeighborSignal(worldPosition);

        IFluidHandler handler = cachedTank.getClusterFluidHandler(Direction.UP);
        if (handler == null)
            return;
        long totalCapacity = 0, totalAmount = 0;
        for (int t = 0; t < handler.getTanks(); t++) {
            totalCapacity += handler.getTankCapacity(t);
            totalAmount += handler.getFluidInTank(t).getAmount();
        }
        if (signal == 0)
            return;

        double speedMultiplier = signal / 15.0;

        boolean filling = speed > 0;
        boolean draining = speed < 0;

        if (!filling && !draining)
            return;

        if (!isAnyHolesFaceSubmerged())
            return;

        float absSpeed = Math.abs(speed);
        int baseTransferRate = (int) (absSpeed * 50.0f);
        double rateMult = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.BALLAST_TRANSFER_RATE_MULTIPLIER
                .get();
        int transferRate = (int) (baseTransferRate * speedMultiplier * rateMult);

        // It doesn't want to work :(
        int minRateForFullTransfer = (int) Math.ceil((double) totalCapacity / 600.0);
        transferRate = Math.max(transferRate, minRateForFullTransfer);

        if (transferRate <= 0)
            return;

        if (filling) {
            long toFillLong = totalCapacity - totalAmount;
            int toFill = (int) Math.min(Integer.MAX_VALUE, toFillLong);
            if (toFill <= 0)
                return;
            int filled = handler.fill(
                    new FluidStack(net.minecraft.world.level.material.Fluids.WATER, Math.min(transferRate, toFill)),
                    IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0 && level.getGameTime() % 4 == 0)
                spawnHolesFaceParticles(true);
        } else if (draining) {
            long toDrainLong = totalAmount;
            int toDrain = (int) Math.min(Integer.MAX_VALUE, toDrainLong);
            if (toDrain <= 0)
                return;
            FluidStack drained = handler.drain(
                    Math.min(transferRate, toDrain),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty() && level.getGameTime() % 4 == 0)
                spawnHolesFaceParticles(false);
        }
    }

    public IFluidHandler getFluidHandlerForSide(Direction side) {
        if (side == null)
            return null;
        BlockState state = getBlockState();
        Direction shaftFace = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (side == shaftFace)
            return null;
        if (level == null || level.isClientSide)
            return null;
        return new PassthroughHandler(side);
    }

    private class PassthroughHandler implements IFluidHandler {
        private final Direction side;

        PassthroughHandler(Direction side) {
            this.side = side;
        }

        private IFluidHandler delegate() {
            if (cachedTank == null && scanCooldown <= 0) {
                cachedTank = findBallastTank();
                scanCooldown = 40;
            }
            return cachedTank == null ? null : cachedTank.getClusterFluidHandler(side);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            IFluidHandler d = delegate();
            return d == null ? FluidStack.EMPTY : d.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler d = delegate();
            return d == null ? 0 : d.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
        }

        @Override
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            IFluidHandler d = delegate();
            return d == null ? 0 : d.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            IFluidHandler d = delegate();
            return d == null ? FluidStack.EMPTY : d.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            IFluidHandler d = delegate();
            return d == null ? FluidStack.EMPTY : d.drain(maxDrain, action);
        }
    }

    private BallastTankBlockEntity findBallastTank() {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        visited.add(worldPosition);
        BlockState myState = getBlockState();
        Direction shaftFace = myState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

        for (Direction dir : Direction.values()) {
            if (dir == shaftFace)
                continue;
            boolean isHole = myState.getValue(BallastVentBlock.propertyForDirection(dir));

            if (!isHole) {
                BlockPos start = worldPosition.relative(dir);
                queue.add(start);
                visited.add(start);
            }
        }
        int maxDepth = 64;
        while (!queue.isEmpty() && visited.size() < maxDepth) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BallastTankBlockEntity tank)
                return tank;
            BlockState state = level.getBlockState(pos);
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(state.getBlock());
            if (id != null && id.getNamespace().equals("create") &&
                    (id.getPath().contains("pipe") || id.getPath().contains("pump")
                            || id.getPath().contains("valve"))) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    private boolean isAnyHolesFaceSubmerged() {
        for (Direction dir : getHolesFaces()) {
            if (isSubmerged(level, worldPosition.relative(dir)))
                return true;
        }
        return false;
    }

    private java.util.List<Direction> getHolesFaces() {
        BlockState state = getBlockState();
        Direction shaftFace = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        java.util.List<Direction> faces = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (dir == shaftFace)
                continue;
            if (state.getValue(BallastVentBlock.propertyForDirection(dir)))
                continue;
            faces.add(dir);
        }
        return faces;
    }

    private void spawnHolesFaceParticles(boolean filling) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        for (Direction dir : getHolesFaces()) {
            if (!isSubmerged(level, worldPosition.relative(dir)))
                continue;
            double cx = worldPosition.getX() + 0.5;
            double cy = worldPosition.getY() + 0.5;
            double cz = worldPosition.getZ() + 0.5;
            double fx = cx + dir.getStepX() * 0.6;
            double fy = cy + dir.getStepY() * 0.6;
            double fz = cz + dir.getStepZ() * 0.6;
            int count = 50;
            double spread = 0.9;
            double speedMagnitude = 0.5;
            if (filling) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        fx, fy, fz, count, spread, spread, spread, speedMagnitude);
            } else {
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                        fx, fy, fz, count, spread, spread, spread, speedMagnitude);
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        fx, fy, fz, count / 2, spread, spread, spread, speedMagnitude * 0.6);
            }
        }
    }

    private boolean isSubmerged(Level level, BlockPos pos) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null)
            return level.getFluidState(pos).is(FluidTags.WATER);
        Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldPos);
        BlockPos wPos = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        Level parentLevel = SubLevelRegistry.getLevel(sub.getUniqueId());
        if (parentLevel == null && level.getServer() != null) {
            parentLevel = level.getServer().overworld();
        }
        if (parentLevel == null)
            return false;
        return parentLevel.getFluidState(wPos).is(FluidTags.WATER);
    }

    @Override
    public float calculateStressApplied() {
        return 4.0f;
    }
}