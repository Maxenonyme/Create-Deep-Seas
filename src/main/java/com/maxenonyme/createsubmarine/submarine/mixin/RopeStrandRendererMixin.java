package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RopeStrandRenderer.class, remap = false)
public class RopeStrandRendererMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/render/CachedBuffers;partialFacing(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lnet/createmod/catnip/render/SuperByteBuffer;"
        )
    )
    private static SuperByteBuffer createsubmarine$redirectPartialFacing(
        PartialModel model,
        BlockState state,
        Direction direction,
        SmartBlockEntity be,
        RopeStrandHolderBehavior ropeHolder
    ) {
        if (ropeHolder instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
            if (model == SimPartialModels.ROPE) {
                return CachedBuffers.partialFacing(AllPartialModels.STEEL_CABLE, state, direction);
            } else if (model == SimPartialModels.ROPE_KNOT) {
                return CachedBuffers.partialFacing(AllPartialModels.STEEL_CABLE_KNOT, state, direction);
            }
        }
        return CachedBuffers.partialFacing(model, state, direction);
    }
}
