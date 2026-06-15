package com.maxenonyme.createsubmarine.submarine.client.renderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.resources.ResourceLocation;
public class AllPartialModels {
    public static final PartialModel BALLAST_WHEEL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/ballast_vent/gear"));
    public static final PartialModel ELECTROLYZER_GLASS = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/electrolyzer_glass"));
    public static final PartialModel PURGE_STAFF_CORE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/core"));
    public static final PartialModel PURGE_STAFF_CASING = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/casing"));
    public static final PartialModel PURGE_STAFF_EYE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/eye"));
    public static void init() {}
}