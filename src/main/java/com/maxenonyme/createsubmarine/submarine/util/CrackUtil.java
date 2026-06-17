package com.maxenonyme.createsubmarine.submarine.util;

import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;

public class CrackUtil {
    private static BiPredicate<UUID, BlockPos> checker = (id, pos) -> false;

    public static void setChecker(BiPredicate<UUID, BlockPos> impl) {
        checker = impl;
    }

    public static boolean hasCrack(UUID subId, BlockPos pos) {
        return checker.test(subId, pos);
    }
}
