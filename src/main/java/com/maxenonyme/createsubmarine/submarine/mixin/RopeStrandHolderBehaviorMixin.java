package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.RopeHolderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = RopeStrandHolderBehavior.class, remap = false)
public class RopeStrandHolderBehaviorMixin implements SteelCableHolderAccessor {

    @Unique
    private boolean createsubmarine$isSteelCable = false;

    @Shadow
    private ClientRopeStrand ownedClientStrand;

    @Shadow
    private ServerRopeStrand ownedServerStrand;

    @Inject(method = "destroyRopeIfAttachmentBroken", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$destroyRopeIfAttachmentBroken(CallbackInfo ci) {
        if (this.ownedServerStrand == null) return;
        Level level = ((com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour) (Object) this).blockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        RopeAttachment endAttachment = this.ownedServerStrand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment != null) {
            BlockPos blockAttachment = endAttachment.blockAttachment();
            if (serverLevel.isLoaded(blockAttachment)) {
                BlockEntity be = serverLevel.getBlockEntity(blockAttachment);
                if (be == null) {
                    if (serverLevel.getBlockState(blockAttachment).getBlock() instanceof RopeHolderBlock) {
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Override
    public boolean createsubmarine$isSteelCable() {
        return this.createsubmarine$isSteelCable;
    }

    @Override
    public void createsubmarine$setSteelCable(boolean val) {
        this.createsubmarine$isSteelCable = val;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createsubmarine$write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        nbt.putBoolean("createsubmarine$IsSteelCable", this.createsubmarine$isSteelCable);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createsubmarine$read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.createsubmarine$isSteelCable = nbt.getBoolean("createsubmarine$IsSteelCable");
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createsubmarine$tick(CallbackInfo ci) {
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @Inject(method = "receiveClientStrand", at = @At("TAIL"))
    private void createsubmarine$receiveClientStrand(int interpolationTick, List incomingPoints, UUID uuid, BlockPos startAttachmentPos, BlockPos endAttachmentPos, CallbackInfo ci) {
        if (this.createsubmarine$isSteelCable) {
            if (this.ownedClientStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
            if (this.ownedServerStrand instanceof SteelCableHolderAccessor accessor) {
                accessor.createsubmarine$setSteelCable(true);
            }
        }
    }

    @Redirect(method = "destroyRope", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack createsubmarine$redirectItemStack(net.minecraft.world.level.ItemLike item) {
        if (this.createsubmarine$isSteelCable) {
            return new ItemStack(com.maxenonyme.createsubmarine.CreateSubmarine.STEEL_CABLE.get());
        }
        return new ItemStack(item);
    }

    @Redirect(method = "createRope", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean createsubmarine$redirectCloserThan(net.minecraft.world.phys.Vec3 instance, net.minecraft.core.Position position, double distance) {
        if (this.createsubmarine$isSteelCable) {
            return true;
        }
        return instance.closerThan(position, distance);
    }
}
