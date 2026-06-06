package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.system.SteelCablePhysicsSystem;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerRopeStrand.class, remap = false)
public abstract class ServerRopeStrandMixin implements SteelCableHolderAccessor {

    @Shadow
    private PhysicsConstraintHandle constraint;

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
