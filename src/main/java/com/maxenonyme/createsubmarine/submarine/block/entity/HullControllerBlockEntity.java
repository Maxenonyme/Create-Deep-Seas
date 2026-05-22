package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.stress.SubLevelStressAnalyzer;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.system.SubmarineHullManager;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class HullControllerBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final int SCAN_BUDGET = 1500;
    private UUID currentSubLevelId = null;
    private boolean subLevelRegistered = false;

    // Synced stress data for client goggle tooltip
    private int syncedBlockCount = 0;
    private int syncedHullCount = 0;
    private double syncedWaterDepth = 0;
    private double syncedStressMin = Double.NaN;
    private double syncedStressMax = Double.NaN;

    public HullControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.CREATIVE_OXYGENATOR_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null) return;
        SubLevelAccess subAccess = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        if (subAccess instanceof SubLevel sub) {
            currentSubLevelId = sub.getUniqueId();
            long gameTick = level.getGameTime();
            if (!CompartmentTracker.isScanActive(currentSubLevelId)
                    && gameTick - CompartmentTracker.lastUpdateTick(currentSubLevelId) >= 20) {
                CompartmentTracker.beginScanIfIdle(currentSubLevelId, sub);
            }
            if (CompartmentTracker.isScanActive(currentSubLevelId)) {
                boolean done = CompartmentTracker.stepScan(currentSubLevelId, sub, SCAN_BUDGET, gameTick);
                if (done && !level.isClientSide) {
                    SubmarinePressureSystem.setSealedCompartments(
                        currentSubLevelId, CompartmentTracker.getCompartments(currentSubLevelId));
                }
            }
            LevelPlot plot = sub.getPlot();
            if (plot != null) {
                BoundingBox3ic bounds = plot.getBoundingBox();
                Vector3d dimensions = CompartmentTracker.getOrComputeDimensions(currentSubLevelId, bounds);
                Pose3dc pose = sub.logicalPose();
                if (CompartmentTracker.poseMovedEnough(currentSubLevelId, pose, 0.01, 1e-6)) {
                    SubmarineHullManager.updateHull(currentSubLevelId, pose.position(), dimensions, pose.orientation());
                    CompartmentTracker.updateAABB(currentSubLevelId, pose.position(), dimensions);
                    CompartmentTracker.recordPose(currentSubLevelId, pose);
                }
                if (!level.isClientSide && !subLevelRegistered) {
                    SubLevelRegistry.register(
                        currentSubLevelId, sub, level,
                        new SubLevelRegistry.PlotBounds(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ())
                    );
                    subLevelRegistered = true;
                }

                // Server-only: update synced stress data every 20 ticks
                if (!level.isClientSide && gameTick % 20 == 0) {
                    dev.ryanhcode.sable.sublevel.ServerSubLevel ssl = (dev.ryanhcode.sable.sublevel.ServerSubLevel) sub;
                    SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.getOrCreate(ssl.getLevel());
                    var solver = analyzer.getSolver(ssl);
                    if (solver != null && solver.blockCount() > 0) {
                        int newBlockCount = solver.blockCount();
                        int newHullCount = solver.hullBlockCount();
                        double newDepth = Math.max(0, SubLevelStressAnalyzer.getWaterSurfaceWorldY(ssl) - pose.position().y());
                        double[] cd = analyzer.getCrushDepths(ssl);
                        double newSmn = Double.NaN;
                        double newSmx = Double.NaN;
                        if (cd != null && cd.length > solver.blockCount()) {
                            int worstIdx = (int) cd[solver.blockCount()];
                            newSmx = worstIdx >= 0 ? cd[worstIdx] : Double.POSITIVE_INFINITY;
                            newSmn = cd[0];
                        }
                        if (newBlockCount != syncedBlockCount || newHullCount != syncedHullCount
                            || Math.abs(newDepth - syncedWaterDepth) > 0.1
                            || Double.compare(newSmn, syncedStressMin) != 0
                            || Double.compare(newSmx, syncedStressMax) != 0) {
                            syncedBlockCount = newBlockCount;
                            syncedHullCount = newHullCount;
                            syncedWaterDepth = newDepth;
                            syncedStressMin = newSmn;
                            syncedStressMax = newSmx;
                            setChanged();
                        }
                    }
                }
            }
        } else if (currentSubLevelId != null) {
            SubmarineHullManager.removeHull(currentSubLevelId);
            if (!level.isClientSide) {
                SubLevelRegistry.unregister(currentSubLevelId);
                SubmarinePressureSystem.clearSubmarine(currentSubLevelId);
            }
            CompartmentTracker.remove(currentSubLevelId);
            currentSubLevelId = null;
            subLevelRegistered = false;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (currentSubLevelId != null) {
            SubmarineHullManager.removeHull(currentSubLevelId);
            SubLevelRegistry.unregister(currentSubLevelId);
            CompartmentTracker.remove(currentSubLevelId);
            SubmarinePressureSystem.clearSubmarine(currentSubLevelId);
            subLevelRegistered = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("blockCount", syncedBlockCount);
        tag.putInt("hullCount", syncedHullCount);
        tag.putDouble("waterDepth", syncedWaterDepth);
        tag.putDouble("stressMin", syncedStressMin);
        tag.putDouble("stressMax", syncedStressMax);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("blockCount")) syncedBlockCount = tag.getInt("blockCount");
        if (tag.contains("hullCount")) syncedHullCount = tag.getInt("hullCount");
        if (tag.contains("waterDepth")) syncedWaterDepth = tag.getDouble("waterDepth");
        if (tag.contains("stressMin")) syncedStressMin = tag.getDouble("stressMin");
        if (tag.contains("stressMax")) syncedStressMax = tag.getDouble("stressMax");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (currentSubLevelId == null) return false;
        tooltip.add(Component.literal(" ")
            .append(Component.translatable("create_submarine.gui.goggles.hull_controller").withStyle(ChatFormatting.GRAY)));
        if (syncedBlockCount == 0) {
            tooltip.add(Component.literal(" ")
                .append(Component.literal("No stress data").withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }
        double depth = syncedWaterDepth;
        tooltip.add(Component.literal(" ")
            .append(Component.literal("Depth: ").withStyle(ChatFormatting.DARK_AQUA))
            .append(Component.literal(String.format("%.1f m", depth)).withStyle(depth > 0 ? ChatFormatting.RED : ChatFormatting.GREEN)));
        String stressStr = Double.isFinite(syncedStressMin) && Double.isFinite(syncedStressMax) && syncedStressMin > 0
            ? String.format("%.1f-%.1f", syncedStressMax, syncedStressMin)
            : "none";
        tooltip.add(Component.literal(" ")
            .append(Component.literal("Stress: ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(stressStr).withStyle(ChatFormatting.WHITE)));
        tooltip.add(Component.literal(" ")
            .append(Component.literal("Blocks: ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(syncedBlockCount + " (" + syncedHullCount + " hull)").withStyle(ChatFormatting.GRAY)));
        if (depth > 0 && Double.isFinite(syncedStressMin) && syncedStressMin > 0) {
            double healthPct = Math.min(100, depth / syncedStressMin * 100);
            ChatFormatting color = healthPct < 50 ? ChatFormatting.GREEN
                : healthPct < 80 ? ChatFormatting.YELLOW
                : healthPct < 95 ? ChatFormatting.GOLD
                : ChatFormatting.RED;
            tooltip.add(Component.literal(" ")
                .append(Component.literal("Hull: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.format("%.0f%%", healthPct)).withStyle(color)));
        }
        return true;
    }
}
