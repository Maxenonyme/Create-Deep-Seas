package com.maxenonyme.AbyssDimension.client.renderer;

import com.maxenonyme.AbyssDimension.client.model.CookiecutterShark;
import com.maxenonyme.AbyssDimension.entities.CookiecutterSharkEntity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CookiecutterSharkRenderer extends MobRenderer<CookiecutterSharkEntity, CookiecutterShark<CookiecutterSharkEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "textures/entity/cookiecutter_shark_texture.png");

    public CookiecutterSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new CookiecutterShark<>(context.bakeLayer(CookiecutterShark.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(CookiecutterSharkEntity entity) {
        return TEXTURE;
    }
}
