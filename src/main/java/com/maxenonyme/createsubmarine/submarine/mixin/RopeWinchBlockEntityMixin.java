package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.system.CableElectrificationSystem;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(value = RopeWinchBlockEntity.class, remap = false)
public abstract class RopeWinchBlockEntityMixin implements IHaveGoggleInformation {

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        RopeStrandHolderBehavior behavior = ((SmartBlockEntity) (Object) this).getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.steel_cable_network").withStyle(ChatFormatting.GRAY)));
            CableElectrificationSystem.ElectrifiedEnergyStorage storage = CableElectrificationSystem.WINCH_ENERGY.get(this);
            int energy = storage != null ? storage.getEnergyStored() : 0;
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_submarine.gui.goggles.energy").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": " + energy + " / " + 1000000 + " FE").withStyle(ChatFormatting.WHITE)));
            return true;
        }
        return false;
    }
}
