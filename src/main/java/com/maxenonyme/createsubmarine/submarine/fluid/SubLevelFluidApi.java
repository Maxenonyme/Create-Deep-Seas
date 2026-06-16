package com.maxenonyme.createsubmarine.submarine.fluid;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public final class SubLevelFluidApi {

    private SubLevelFluidApi() {}

    public static int addWater(UUID subLevelId, BlockPos localPos, int amount) {
        if (amount <= 0) return current(subLevelId, localPos);
        SubLevelFluidStore store = SubLevelFluidManager.getOrCreate(subLevelId);
        return store.add(localPos.asLong(), amount);
    }

    public static int removeWater(UUID subLevelId, BlockPos localPos, int amount) {
        if (amount <= 0) return 0;
        SubLevelFluidStore store = SubLevelFluidManager.get(subLevelId);
        if (store == null) return 0;
        long key = localPos.asLong();
        int have = store.get(key);
        int removed = Math.min(have, amount);
        store.add(key, -removed);
        return removed;
    }

    public static int current(UUID subLevelId, BlockPos localPos) {
        SubLevelFluidStore store = SubLevelFluidManager.get(subLevelId);
        return store == null ? 0 : store.get(localPos.asLong());
    }
}
