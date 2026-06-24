package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.mixin.ChunkRenderTypeSetAccessor;
import dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

import java.util.List;

public final class SailRenderClient {
    private SailRenderClient() {
    }

    public static void init(IEventBus modEventBus) {
        VeilEventPlatform.INSTANCE.onVeilRegisterBlockLayers(registry -> registry.registerBlockLayer(SailRenderTypes.sail()));
        VeilEventPlatform.INSTANCE.onVeilRegisterFixedBuffers(registry ->
                registry.registerFixedBuffer(VeilRenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, SailRenderTypes.sail()));
        modEventBus.addListener(SailRenderClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChunkRenderTypeSet set = ChunkRenderTypeSet.of(SailRenderTypes.sail());
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof SymmetricSailBlock) {
                    ItemBlockRenderTypes.setRenderLayer(block, set);
                }
            }
            fixChunkRenderTypeSet();
        });
    }

    private static void fixChunkRenderTypeSet() {
        List<RenderType> list = RenderType.chunkBufferLayers();
        ChunkRenderTypeSetAccessor.setChunkRenderTypesList(list);
        ChunkRenderTypeSetAccessor.setChunkRenderTypes(list.toArray(new RenderType[0]));
        ((ChunkRenderTypeSetAccessor) (Object) ChunkRenderTypeSet.all()).getBits().set(0, list.size());
    }
}
