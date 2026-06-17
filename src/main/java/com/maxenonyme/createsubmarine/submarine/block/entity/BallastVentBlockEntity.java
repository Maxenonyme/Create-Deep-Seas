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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;

public class BallastVentBlockEntity extends KineticBlockEntity {
    private enum Mode {
        NONE, TANK, CHAMBER, OCEAN
    }

    private BallastTankBlockEntity cachedTank;
    private int scanCooldown = 0;

    private Mode mode = Mode.NONE;
    private CompartmentDetector.Component cachedCompartment;
    private UUID cachedSubId;
    private int pendingFill;
    private int pendingDrain;
    private long lastFlowTick = Long.MIN_VALUE;
    private int lastFlowDir;

    public BallastVentBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.BALLAST_VENT_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        if (mode == Mode.CHAMBER && level.getGameTime() % 5 == 0 && checkChamberBreach())
            return;

        scanCooldown--;
        if (scanCooldown <= 0) {
            detectMode();
            scanCooldown = 40;
        }

        if (mode == Mode.CHAMBER) {
            processChamber();
        } else if (mode == Mode.TANK) {
            tickBallastTank();
        }
    }

    private void detectMode() {
        cachedTank = findBallastTank();
        if (cachedTank != null) {
            mode = Mode.TANK;
            cachedCompartment = null;
            cachedSubId = null;
            return;
        }
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        UUID id = sub == null ? null : sub.getUniqueId();
        CompartmentDetector.Component comp = id == null ? null : findChamberCompartment(id);
        if (comp != null) {
            mode = Mode.CHAMBER;
            cachedCompartment = comp;
            cachedSubId = id;
            return;
        }
        if (mode == Mode.CHAMBER)
            handleChamberLost();
        cachedCompartment = null;
        cachedSubId = null;
        mode = isAnyHolesFaceSubmerged() ? Mode.OCEAN : Mode.NONE;
    }

    private CompartmentDetector.Component findChamberCompartment(UUID id) {
        List<CompartmentDetector.Component> comps = CompartmentTracker.getCompartments(id);
        if (comps.isEmpty())
            return null;
        for (Direction dir : getHolesFaces()) {
            BlockPos neighbor = worldPosition.relative(dir);
            for (CompartmentDetector.Component c : comps) {
                if (!c.sealed() || CompartmentTracker.isCompromised(id, c.anchor()))
                    continue;
                if (c.internal().contains(neighbor))
                    return c;
            }
        }
        return null;
    }

    private boolean checkChamberBreach() {
        if (cachedCompartment == null || cachedSubId == null)
            return false;
        if (!isChamberBreached(cachedSubId, cachedCompartment))
            return false;
        handleChamberLost();
        mode = Mode.NONE;
        cachedCompartment = null;
        cachedSubId = null;
        return true;
    }

    private void handleChamberLost() {
        if (cachedCompartment == null || cachedSubId == null)
            return;
        if (SubmarineConfig.DISABLE_IMPLOSION.get())
            return;
        if (!chamberHasAir(cachedCompartment))
            return;
        if (!SubmarinePressureSystem.isUnderHighPressure(cachedSubId, level))
            return;
        if (!isChamberBreached(cachedSubId, cachedCompartment))
            return;
        implodeChamber(cachedSubId, cachedCompartment);
    }

    private boolean isChamberBreached(UUID id, CompartmentDetector.Component old) {
        BlockPos air = sampleChamberCell();
        if (air == null)
            return false;
        for (CompartmentDetector.Component c : CompartmentTracker.getCompartments(id)) {
            if (c.internal().contains(air))
                return !c.sealed() || CompartmentTracker.isCompromised(id, c.anchor());
        }
        return false;
    }

    private BlockPos sampleChamberCell() {
        if (cachedCompartment == null)
            return null;
        for (Direction dir : getHolesFaces()) {
            BlockPos n = worldPosition.relative(dir);
            if (cachedCompartment.internal().contains(n))
                return n;
        }
        return null;
    }

    private boolean chamberHasAir(CompartmentDetector.Component comp) {
        for (BlockPos p : comp.internal()) {
            if (isEmptyCell(p))
                return true;
        }
        return false;
    }

    private void implodeChamber(UUID id, CompartmentDetector.Component comp) {
        Level parentLevel = SubLevelRegistry.getLevel(id);
        if (parentLevel == null)
            parentLevel = level;
        SubLevelAccess sub = SubLevelRegistry.getAll().get(id);
        if (sub == null)
            return;
        pendingFill = 0;
        pendingDrain = 0;
        com.maxenonyme.createsubmarine.submarine.system.SubmarineSinkingSystem
                .implodeCompartment(id, sub, parentLevel, comp);
    }

    private void processChamber() {
        if (cachedCompartment == null)
            return;

        int water = chamberWaterVolume();
        pendingFill = Math.min(pendingFill, Math.max(0, chamberCapacity() - water));
        pendingDrain = Math.min(pendingDrain, water);

        int budget = 8;
        while (pendingFill >= 1000 && budget-- > 0) {
            List<BlockPos> layer = lowestEmptyLayer();
            if (layer.isEmpty()) {
                pendingFill = 0;
                break;
            }
            int cost = layer.size() * 1000;
            if (pendingFill < cost)
                break;
            for (BlockPos p : layer)
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            pendingFill -= cost;
        }

        budget = 8;
        while (pendingDrain >= 1000 && budget-- > 0) {
            List<BlockPos> layer = highestWaterLayer();
            if (layer.isEmpty()) {
                pendingDrain = 0;
                break;
            }
            int cost = layer.size() * 1000;
            if (pendingDrain < cost)
                break;
            for (BlockPos p : layer)
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            pendingDrain -= cost;
        }

        spawnChamberParticles();
    }

    private void spawnChamberParticles() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        if (level.getGameTime() % 4 != 0)
            return;
        if (lastFlowDir == 0 || level.getGameTime() - lastFlowTick > 5)
            return;

        boolean filling = lastFlowDir > 0;
        boolean dripping = filling && chamberHasAir(cachedCompartment);
        for (Direction dir : getHolesFaces()) {
            double fx = worldPosition.getX() + 0.5 + dir.getStepX() * 0.55;
            double fy = worldPosition.getY() + 0.5 + dir.getStepY() * 0.55;
            double fz = worldPosition.getZ() + 0.5 + dir.getStepZ() * 0.55;
            double sign = filling ? 1.0 : -1.0;
            double vx = dir.getStepX() * 0.18 * sign;
            double vy = dir.getStepY() * 0.18 * sign;
            double vz = dir.getStepZ() * 0.18 * sign;
            for (int i = 0; i < 2; i++)
                serverLevel.sendParticles(ParticleTypes.BUBBLE, fx, fy, fz, 0, vx, vy, vz, 1.0);
            if (dripping)
                serverLevel.sendParticles(ParticleTypes.FALLING_WATER, fx, fy, fz, 2, 0.12, 0.0, 0.12, 0.0);
        }
    }

    private List<BlockPos> lowestEmptyLayer() {
        Set<BlockPos> internal = cachedCompartment.internal();
        int bestY = Integer.MAX_VALUE;
        for (BlockPos p : internal) {
            if (p.getY() < bestY && isEmptyCell(p))
                bestY = p.getY();
        }
        List<BlockPos> layer = new ArrayList<>();
        if (bestY == Integer.MAX_VALUE)
            return layer;
        for (BlockPos p : internal) {
            if (p.getY() == bestY && isEmptyCell(p))
                layer.add(p);
        }
        return layer;
    }

    private List<BlockPos> highestWaterLayer() {
        Set<BlockPos> internal = cachedCompartment.internal();
        int bestY = Integer.MIN_VALUE;
        for (BlockPos p : internal) {
            if (p.getY() > bestY && isWaterCell(p))
                bestY = p.getY();
        }
        List<BlockPos> layer = new ArrayList<>();
        if (bestY == Integer.MIN_VALUE)
            return layer;
        for (BlockPos p : internal) {
            if (p.getY() == bestY && isWaterCell(p))
                layer.add(p);
        }
        return layer;
    }

    private boolean isEmptyCell(BlockPos p) {
        return level.getBlockState(p).isAir();
    }

    private boolean isWaterCell(BlockPos p) {
        return level.getBlockState(p).getBlock() == Blocks.WATER;
    }

    private int chamberWaterVolume() {
        if (cachedCompartment == null)
            return 0;
        int count = 0;
        for (BlockPos p : cachedCompartment.internal()) {
            if (isWaterCell(p))
                count++;
        }
        return count * 1000;
    }

    private int chamberCapacity() {
        if (cachedCompartment == null)
            return 0;
        int count = 0;
        for (BlockPos p : cachedCompartment.internal()) {
            if (isEmptyCell(p) || isWaterCell(p))
                count++;
        }
        return count * 1000;
    }

    private void tickBallastTank() {
        float speed = getSpeed();
        if (Math.abs(speed) < 0.1f)
            return;
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
        if (mode == Mode.NONE)
            detectMode();
        return switch (mode) {
            case CHAMBER -> new ChamberHandler();
            case OCEAN -> new OceanHandler();
            default -> new PassthroughHandler(side);
        };
    }

    private class ChamberHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            int volume = chamberWaterVolume() + pendingFill;
            return volume <= 0 ? FluidStack.EMPTY
                    : new FluidStack(net.minecraft.world.level.material.Fluids.WATER, volume);
        }

        @Override
        public int getTankCapacity(int tank) {
            return chamberCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
        }

        @Override
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return 0;
            int room = chamberCapacity() - (chamberWaterVolume() + pendingFill);
            if (room <= 0)
                return 0;
            int accepted = Math.min(resource.getAmount(), room);
            if (action.execute()) {
                pendingFill += accepted;
                lastFlowTick = level.getGameTime();
                lastFlowDir = 1;
            }
            return accepted;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            int available = chamberWaterVolume() - pendingDrain;
            int amount = Math.min(maxDrain, available);
            if (amount <= 0)
                return FluidStack.EMPTY;
            if (action.execute()) {
                pendingDrain += amount;
                lastFlowTick = level.getGameTime();
                lastFlowDir = -1;
            }
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, amount);
        }
    }

    private static class OceanHandler implements IFluidHandler {
        private static final int OCEAN = 1_000_000;

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, OCEAN);
        }

        @Override
        public int getTankCapacity(int tank) {
            return OCEAN;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER);
        }

        @Override
        public int fill(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            return isFluidValid(0, resource) ? resource.getAmount() : 0;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, IFluidHandler.FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource))
                return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (maxDrain <= 0)
                return FluidStack.EMPTY;
            return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, maxDrain);
        }
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
            int count = 5;
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
        if (parentLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
            parentLevel = sl.getLevel();
        }
        if (parentLevel == null)
            return false;
        return com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.realFluidState(parentLevel, wPos)
                .is(FluidTags.WATER);
    }

    @Override
    public float calculateStressApplied() {
        return 4.0f;
    }
}