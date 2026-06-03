package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ClientRopeStrand.class, remap = false)
public class ClientRopeStrandMixin implements SteelCableHolderAccessor {

    @Unique
    private boolean createsubmarine$isSteelCable = false;

    @Override
    public boolean createsubmarine$isSteelCable() {
        return this.createsubmarine$isSteelCable;
    }

    @Override
    public void createsubmarine$setSteelCable(boolean val) {
        this.createsubmarine$isSteelCable = val;
    }
}
