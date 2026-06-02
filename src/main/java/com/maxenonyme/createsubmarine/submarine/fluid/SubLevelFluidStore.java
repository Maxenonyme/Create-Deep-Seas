package com.maxenonyme.createsubmarine.submarine.fluid;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

public class SubLevelFluidStore {

    public static final int FULL = 1000;

    private final Long2IntOpenHashMap amounts = new Long2IntOpenHashMap();

    public SubLevelFluidStore() {
        amounts.defaultReturnValue(0);
    }

    public int get(long packed) {
        return amounts.get(packed);
    }

    public int get(BlockPos pos) {
        return amounts.get(pos.asLong());
    }

    public void set(long packed, int amount) {
        if (amount <= 0) {
            amounts.remove(packed);
        } else {
            amounts.put(packed, Math.min(amount, FULL));
        }
    }

    public int add(long packed, int delta) {
        int next = amounts.get(packed) + delta;
        set(packed, next);
        return Math.max(0, next);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public LongArrayList snapshotCells() {
        return new LongArrayList(amounts.keySet());
    }

    public Long2IntMap raw() {
        return amounts;
    }

    public long totalAmount() {
        long sum = 0;
        for (int v : amounts.values()) sum += v;
        return sum;
    }
}
