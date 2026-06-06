package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.system.CableElectrificationSystem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmartBlockEntity.class, remap = false)
public abstract class SmartBlockEntityMixin {

    @Inject(method = "write", at = @At("TAIL"))
    private void createsubmarine$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        Object self = this;
        if (self instanceof RopeWinchBlockEntity || self instanceof RopeConnectorBlockEntity) {
            CableElectrificationSystem.ElectrifiedEnergyStorage storage = CableElectrificationSystem.WINCH_ENERGY.get(self);
            if (storage != null) {
                tag.putInt("createsubmarine$Energy", storage.getEnergyStored());
            }
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createsubmarine$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        Object self = this;
        if (self instanceof RopeWinchBlockEntity || self instanceof RopeConnectorBlockEntity) {
            if (tag.contains("createsubmarine$Energy")) {
                CableElectrificationSystem.ElectrifiedEnergyStorage storage = CableElectrificationSystem.getOrCreateStorage((BlockEntity) self);
                if (storage != null) {
                    storage.setEnergy(tag.getInt("createsubmarine$Energy"));
                }
            }
        }
    }
}
