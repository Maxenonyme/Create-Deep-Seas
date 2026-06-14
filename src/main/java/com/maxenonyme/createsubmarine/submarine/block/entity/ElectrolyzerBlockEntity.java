package com.maxenonyme.createsubmarine.submarine.block.entity;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.ElectrolyzerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

public class ElectrolyzerBlockEntity extends BlockEntity {
    public final FluidTank waterTank = new FluidTank(4000, fluid -> fluid.getFluid().isSame(Fluids.WATER));
    public final FluidTank oxygenTank = new FluidTank(4000,
            fluid -> fluid.getFluid().builtInRegistryHolder().is(CreateSubmarine.OXYGEN_TAG));
    public final EnergyStorage energyStorage = new EnergyStorage(10000, 1000, 1000);

    public final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? waterTank.getFluid() : oxygenTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? waterTank.getCapacity() : oxygenTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 ? waterTank.isFluidValid(stack) : oxygenTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.getFluid().isSame(Fluids.WATER))
                return waterTank.fill(resource, action);
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.getFluid().builtInRegistryHolder().is(CreateSubmarine.OXYGEN_TAG))
                return oxygenTank.drain(resource, action);
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return oxygenTank.drain(maxDrain, action);
        }
    };

    private boolean isEnabled = false;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> waterTank.getFluidAmount();
                case 3 -> waterTank.getCapacity();
                case 4 -> oxygenTank.getFluidAmount();
                case 5 -> oxygenTank.getCapacity();
                case 6 -> isEnabled ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 6 -> isEnabled = (value == 1);
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateSubmarine.ELECTROLYZER_BE.get(), pos, state);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectrolyzerBlockEntity be) {
        if (level.isClientSide) {
            if (state.getValue(ElectrolyzerBlock.POWERED)) {
                if (level.random.nextInt(5) == 0) {
                    double x = pos.getX() + 0.4 + level.random.nextDouble() * 0.2;
                    double z = pos.getZ() + 0.4 + level.random.nextDouble() * 0.2;
                    double y = pos.getY() + 1.75;
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.1, 0);
                }
            }
            return;
        }

        boolean canWork = be.isEnabled &&
                be.energyStorage.getEnergyStored() >= 250 &&
                be.waterTank.getFluidAmount() >= 10 &&
                be.oxygenTank.getFluidAmount() + 10 <= be.oxygenTank.getCapacity();

        if (canWork) {
            be.energyStorage.extractEnergy(250, false);
            be.waterTank.drain(10, IFluidHandler.FluidAction.EXECUTE);
            be.oxygenTank.fill(new FluidStack(CreateSubmarine.OXYGEN.get(), 10), IFluidHandler.FluidAction.EXECUTE);
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);

            if (!state.getValue(ElectrolyzerBlock.POWERED)) {
                level.setBlock(pos, state.setValue(ElectrolyzerBlock.POWERED, true), 3);
            }
        } else {
            if (state.getValue(ElectrolyzerBlock.POWERED)) {
                level.setBlock(pos, state.setValue(ElectrolyzerBlock.POWERED, false), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("OxygenTank", oxygenTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putBoolean("Enabled", isEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        oxygenTank.readFromNBT(registries, tag.getCompound("OxygenTank"));
        int stored = tag.getInt("Energy");
        energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
        energyStorage.receiveEnergy(stored, false);
        isEnabled = tag.getBoolean("Enabled");
    }

    public void toggleEnabled() {
        isEnabled = !isEnabled;
        setChanged();
    }
}