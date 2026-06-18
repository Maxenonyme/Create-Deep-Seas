package com.maxenonyme.createsubmarine.submarine.ponder;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SubmarinePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return "create_submarine";
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        SubmarinePonderScenes.register(helper);
    }

    @Override
    public void registerTags(net.createmod.ponder.api.registration.PonderTagRegistrationHelper<ResourceLocation> helper) {
        SubmarinePonderTags.register(helper);
    }
}