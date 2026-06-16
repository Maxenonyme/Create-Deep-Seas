package com.maxenonyme.createsubmarine.submarine.mixin;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState lied = CompartmentTracker.getLiedBlockState((Level) (Object) this, pos);
        if (lied != null) cir.setReturnValue(lied);
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$onGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        FluidState lied = CompartmentTracker.getLiedFluidState((Level) (Object) this, pos);
        if (lied != null) cir.setReturnValue(lied);
    }
}
