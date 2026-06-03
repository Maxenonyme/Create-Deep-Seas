package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.client.model.Amphistium;
import com.maxenonyme.AbyssDimension.entities.AmphistiumEntity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AmphistiumRenderer extends MobRenderer<AmphistiumEntity, Amphistium<AmphistiumEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "textures/entity/amphistium.png");

    public AmphistiumRenderer(EntityRendererProvider.Context context) {
        super(context, new Amphistium<>(context.bakeLayer(Amphistium.LAYER_LOCATION)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(AmphistiumEntity entity) {
        return TEXTURE;
    }
}
