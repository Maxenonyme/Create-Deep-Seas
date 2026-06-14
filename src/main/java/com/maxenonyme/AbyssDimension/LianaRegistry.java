package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.block.CreepvineSeedBlock;
import com.maxenonyme.AbyssDimension.block.SubmarineLianaBlock;
import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class LianaRegistry {
    public static final Supplier<Block> LIANA_BLOCK = CreateSubmarine.BLOCKS.register("submarine_liana",
            () -> new SubmarineLianaBlock());

    public static final Supplier<BlockEntityType<SubmarineLianaBlockEntity>> LIANA_BE = CreateSubmarine.BLOCK_ENTITIES.register("submarine_liana",
            () -> BlockEntityType.Builder.of(SubmarineLianaBlockEntity::new, LIANA_BLOCK.get()).build(null));

    public static final Supplier<Block> CREEPVINE_SEED = CreateSubmarine.BLOCKS.register("creepvine_seed",
            () -> new CreepvineSeedBlock());

    public static final Supplier<net.minecraft.world.item.Item> CREEPVINE_SEED_ITEM = CreateSubmarine.ITEMS.register("creepvine_seed",
            () -> new net.minecraft.world.item.BlockItem(CREEPVINE_SEED.get(), new net.minecraft.world.item.Item.Properties()));

    public static void init() {
    }
}
