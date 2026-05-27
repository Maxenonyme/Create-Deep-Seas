package com.maxenonyme.createsubmarine.abyss.mixin;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StructurePiece.class)
public class AbyssStructurePieceMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceType;ILnet/minecraft/world/level/levelgen/structure/BoundingBox;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static BoundingBox createsubmarine$lowerAbyssPieces(BoundingBox boundingBox, StructurePieceType type) {
        //commented out for testing because of a bug re enable later
        //if (!com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.ENABLE_ABYSS_GENERATION.get())
        //    return boundingBox;
        if (type == StructurePieceType.OCEAN_MONUMENT_BUILDING || type == StructurePieceType.OCEAN_RUIN) {
            int x = boundingBox.getCenter().getX();
            int z = boundingBox.getCenter().getZ();

            long cellX = Math.floorDiv(x, 4000);
            long cellZ = Math.floorDiv(z, 4000);

            double minDistance = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long cx = cellX + dx;
                    long cz = cellZ + dz;
                    long hash = (cx * 31273709L) ^ (cz * 43903207L);
                    java.util.Random rand = new java.util.Random(hash);
                    double centerX = cx * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                    double centerZ = cz * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                    double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                    if (dist < minDistance) {
                        minDistance = dist;
                    }
                }
            }

            double t = 0.0;
            if (minDistance < 480) {
                t = 1.0;
            } else if (minDistance < 520) {
                t = (520 - minDistance) / 40.0;
                t = t * t * (3 - 2 * t);
            }

            int shift = (int) (-50 - 60 * t);
            return boundingBox.moved(0, shift, 0);
        }
        return boundingBox;
    }
}
