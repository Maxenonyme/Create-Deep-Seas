package com.maxenonyme.createsubmarine.submarine.fluid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SubLevelFluidManager {

    private static final Map<UUID, SubLevelFluidStore> STORES = new ConcurrentHashMap<>();

    public static SubLevelFluidStore getOrCreate(UUID subLevelId) {
        return STORES.computeIfAbsent(subLevelId, k -> new SubLevelFluidStore());
    }

    public static SubLevelFluidStore get(UUID subLevelId) {
        return STORES.get(subLevelId);
    }

    public static void remove(UUID subLevelId) {
        STORES.remove(subLevelId);
    }

    public static Map<UUID, SubLevelFluidStore> all() {
        return STORES;
    }

    public static void clear() {
        STORES.clear();
    }
}
