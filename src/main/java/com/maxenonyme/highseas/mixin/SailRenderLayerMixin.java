package com.maxenonyme.highseas.mixin;

import com.maxenonyme.highseas.client.IrisCompat;
import dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public class SailRenderLayerMixin {

    @Inject(method = "getRenderLayers", at = @At("HEAD"), cancellable = true, require = 0)
    private static void createhighseas$normalSailUnderIris(BlockState state, CallbackInfoReturnable<ChunkRenderTypeSet> cir) {
        if (state.getBlock() instanceof SymmetricSailBlock && IrisCompat.isShaderPackActive()) {
            cir.setReturnValue(ChunkRenderTypeSet.of(RenderType.cutout()));
        }
    }
}
