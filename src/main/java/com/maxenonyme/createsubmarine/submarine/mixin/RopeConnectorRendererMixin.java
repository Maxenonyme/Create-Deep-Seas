package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RopeConnectorRenderer.class, remap = false)
public class RopeConnectorRendererMixin {

    @Redirect(
        method = "renderSafe",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/render/CachedBuffers;partialFacing(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lnet/createmod/catnip/render/SuperByteBuffer;"
        )
    )
    private SuperByteBuffer createsubmarine$redirectKnot(
        PartialModel model,
        BlockState state,
        Direction direction,
        RopeConnectorBlockEntity be
    ) {
        if (model == SimPartialModels.ROPE_CONNECTOR_KNOT
                && be instanceof SmartBlockEntity smartBe) {
            RopeStrandHolderBehavior behavior = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
            if (behavior instanceof SteelCableHolderAccessor accessor && accessor.createsubmarine$isSteelCable()) {
                return CachedBuffers.partialFacing(AllPartialModels.CONNECTOR_STEEL_KNOT, state, direction);
            }
        }
        return CachedBuffers.partialFacing(model, state, direction);
    }
}
