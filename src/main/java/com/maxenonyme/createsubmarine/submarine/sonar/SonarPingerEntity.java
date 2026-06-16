package com.maxenonyme.createsubmarine.submarine.sonar;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.SonarOpenPayload;
import com.maxenonyme.createsubmarine.submarine.network.SonarScanPayload;
import com.simibubi.create.api.schematic.requirement.SpecialEntityItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.networking.ISyncPersistentData;
import com.simibubi.create.foundation.utility.IInteractionChecker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class SonarPingerEntity extends HangingEntity implements ISyncPersistentData, IInteractionChecker, SpecialEntityItemRequirement {

    private static final int SCAN_INTERVAL = 20;
    private static final int SCAN_RADIUS = 32;
    private static final int SCAN_STEP = 2;

    private int scanCooldown = 0;
    private SubLevel cachedSubLevel;
    private float sonarYaw = 0;
    private float sonarPitch = -90;

    public SonarPingerEntity(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    public SonarPingerEntity(Level world, BlockPos pos, Direction facing) {
        super(CreateSubmarine.SONAR_PINGER_ENTITY.get(), world, pos);
        this.setPingerDirection(facing);
        this.recalculatePingerBoundingBox();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Facing", (byte) this.direction.get3DDataValue());
        tag.putFloat("SonarYaw", this.sonarYaw);
        tag.putFloat("SonarPitch", this.sonarPitch);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Facing", Tag.TAG_ANY_NUMERIC)) {
            this.direction = Direction.from3DDataValue(tag.getByte("Facing"));
        } else {
            this.direction = Direction.SOUTH;
        }
        if (tag.contains("SonarYaw")) {
            this.sonarYaw = tag.getFloat("SonarYaw");
        }
        if (tag.contains("SonarPitch")) {
            this.sonarPitch = tag.getFloat("SonarPitch");
        }
        this.recalculatePingerBoundingBox();
    }

    public void setPingerDirection(Direction facing) {
        this.direction = facing;
        if (facing.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot(facing.get2DDataValue() * 90);
        } else {
            this.setXRot(-90 * facing.getAxisDirection().getStep());
            this.setYRot(0);
        }
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculatePingerBoundingBox();
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
        Vec3 pos = Vec3.atLowerCornerOf(this.getPos()).add(0.5, 0.5, 0.5)
                .subtract(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.46875));
        this.setPosRaw(pos.x, pos.y, pos.z);

        double w = 0.25;
        double h = 0.25;
        double d = 0.03125;

        return switch (direction.getAxis()) {
            case X -> new AABB(pos.x - d, pos.y - h, pos.z - w, pos.x + d, pos.y + h, pos.z + w);
            case Y -> new AABB(pos.x - w, pos.y - d, pos.z - w, pos.x + w, pos.y + d, pos.z + w);
            case Z -> new AABB(pos.x - w, pos.y - h, pos.z - d, pos.x + w, pos.y + h, pos.z + d);
        };
    }

    public void recalculatePingerBoundingBox() {
        if (this.direction != null) {
            this.setBoundingBox(this.calculateBoundingBox(this.pos, this.direction));
        }
    }

    @Override
    public boolean survives() {
        if (!this.level().noCollision(this)) return false;
        BlockPos blockpos = this.pos.relative(this.direction.getOpposite());
        BlockState blockstate = this.level().getBlockState(blockpos);
        return Block.canSupportCenter(this.level(), blockpos, this.direction) && this.level().getEntities(this, this.getBoundingBox(), HANGING_ENTITY).isEmpty();
    }

    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        return this.position();
    }

    public float getSonarYaw() {
        return this.sonarYaw;
    }

    public float getSonarPitch() {
        return this.sonarPitch;
    }

    public void setSonarYaw(float yaw) {
        this.sonarYaw = yaw;
    }

    public void setSonarPitch(float pitch) {
        this.sonarPitch = pitch;
    }

    public SubLevel getTrackingSubLevel() {
        if (this.cachedSubLevel == null || this.cachedSubLevel.isRemoved()) {
            this.cachedSubLevel = Sable.HELPER.getContaining(this);
        }
        return this.cachedSubLevel;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (--this.scanCooldown > 0) return;
        this.scanCooldown = SCAN_INTERVAL;

        List<ServerPlayer> trackingPlayers = ((ServerLevel) this.level()).getPlayers(p -> p.distanceToSqr(this) < 64 * 64);
        if (trackingPlayers.isEmpty()) return;

        SubLevel subLevel = getTrackingSubLevel();
        int centerX, centerY, centerZ;
        if (subLevel instanceof ServerSubLevel ssl) {
            centerX = (int) java.lang.Math.floor(ssl.logicalPose().position().x());
            centerY = (int) java.lang.Math.floor(ssl.logicalPose().position().y());
            centerZ = (int) java.lang.Math.floor(ssl.logicalPose().position().z());
        } else {
            BlockPos pos = this.blockPosition();
            centerX = pos.getX();
            centerY = pos.getY();
            centerZ = pos.getZ();
        }

        int gridSize = (SCAN_RADIUS * 2) / SCAN_STEP + 1;
        byte[] heights = new byte[gridSize * gridSize];
        int[] colors = new int[gridSize * gridSize];
        Arrays.fill(heights, (byte) -128);
        Arrays.fill(colors, 0);

        for (int gx = 0; gx < gridSize; gx++) {
            for (int gz = 0; gz < gridSize; gz++) {
                int wx = centerX - SCAN_RADIUS + gx * SCAN_STEP;
                int wz = centerZ - SCAN_RADIUS + gz * SCAN_STEP;

                LevelChunk chunk = this.level().getChunkAt(new BlockPos(wx, 0, wz));
                if (chunk.isEmpty()) continue;

                boolean underwater = false;
                int surfaceBlockY = -1;

                for (int wy = this.level().getMaxBuildHeight() - 1; wy >= this.level().getMinBuildHeight(); wy--) {
                    BlockPos bp = new BlockPos(wx, wy, wz);
                    BlockState bs = this.level().getBlockState(bp);
                    if ((bs.isAir() || bs.liquid()) && !underwater) {
                        if (bs.liquid()) {
                            underwater = true;
                            surfaceBlockY = wy;
                        }
                        continue;
                    }
                    if (bs.isAir() || bs.liquid()) continue;
                    int idx = gx * gridSize + gz;
                    int relY = wy - centerY;
                    if (relY < -128 || relY > 127) relY = 0;
                    heights[idx] = (byte) relY;
                    int color = bs.getMapColor(this.level(), bp).col;
                    if (underwater) {
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;
                        r = r * 3 / 4;
                        g = g * 3 / 4;
                        b = Math.min(255, b + 40);
                        color = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                    colors[idx] = color;
                    break;
                }
            }
        }

        var packet = new SonarScanPayload(heights, colors, gridSize, SCAN_STEP, SCAN_RADIUS,
                centerX, centerY, centerZ);

        for (ServerPlayer player : trackingPlayers) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        }
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (!this.level().isClientSide) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((ServerPlayer) player, new SonarOpenPayload(this.getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) return;
        this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        if (entity instanceof Player player && player.getAbilities().instabuild) return;
        this.spawnAtLocation(new ItemStack(CreateSubmarine.SONAR_PINGER_ITEM.get()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(CreateSubmarine.SONAR_PINGER_ITEM.get());
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        this.setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).withEyeHeight(0);
    }

    @Override
    public boolean canPlayerUse(Player player) {
        AABB box = this.getBoundingBox();
        double dx = box.minX > player.getX() ? box.minX - player.getX() : player.getX() > box.maxX ? player.getX() - box.maxX : 0;
        double dy = box.minY > player.getY() ? box.minY - player.getY() : player.getY() > box.maxY ? player.getY() - box.maxY : 0;
        double dz = box.minZ > player.getZ() ? box.minZ - player.getZ() : player.getZ() > box.maxZ ? player.getZ() - box.maxZ : 0;
        return (dx * dx + dy * dy + dz * dz) <= 64.0D;
    }

    @Override
    public ItemRequirement getRequiredItems() {
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, CreateSubmarine.SONAR_PINGER_ITEM.get());
    }

    @Override
    public void onPersistentDataUpdated() {
    }
}
