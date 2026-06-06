package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.SimplePropellerVisual;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock.REVERSED;

public class SubmarinePropellerVisual extends SimplePropellerVisual<SubmarinePropellerBlockEntity> {

    protected final dev.engine_room.flywheel.lib.instance.OrientedInstance contraPropeller;

    public SubmarinePropellerVisual(final VisualizationContext context, final SubmarinePropellerBlockEntity blockEntity, final float partialTick) {
        super(context, blockEntity, partialTick);

        final net.minecraft.core.Direction facing = this.blockState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
        final net.minecraft.core.Vec3i normal = facing.getNormal();
        final net.minecraft.world.phys.Vec3 normalPos = new net.minecraft.world.phys.Vec3(normal.getX(), normal.getY(), normal.getZ());
        final org.joml.Vector3f pos = net.minecraft.world.phys.Vec3.atLowerCornerOf(this.getVisualPosition()).add(normalPos.scale(3 / 16f)).toVector3f();

        this.contraPropeller = this.instancerProvider().instancer(dev.engine_room.flywheel.lib.instance.InstanceTypes.ORIENTED, dev.engine_room.flywheel.lib.model.Models.partial(this.getContraModel(this.blockState))).createInstance();
        this.contraPropeller.position(pos).rotation(this.blockOrientation).setChanged();
    }

    @Override
    public PartialModel getModel(final BlockState state) {
        return state.getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED : AllPartialModels.SUBMARINE_PROPELLER;
    }

    public PartialModel getContraModel(final BlockState state) {
        return state.getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED_CONTRA : AllPartialModels.SUBMARINE_PROPELLER_CONTRA;
    }

    @Override
    public float getAngle(final float partialTicks) {
        final BlockState state = this.blockEntity.getBlockState();
        final BlockPos pos = this.blockEntity.getBlockPos();
        return super.getAngle(partialTicks) + rotationOffset(state, state.getValue(SubmarinePropellerBlock.FACING).getAxis(), pos);
    }

    @Override
    public void beginFrame(final Context context) {
        super.beginFrame(context);

        final float angle = this.getAngle(context.partialTick());
        this.contraPropeller.identityRotation()
                .rotate(net.minecraft.util.Mth.DEG_TO_RAD * -angle, this.rotationAxis.x, this.rotationAxis.y, this.rotationAxis.z)
                .rotate(this.blockOrientation)
                .setChanged();
    }

    @Override
    public void updateLight(final float partialTick) {
        super.updateLight(partialTick);
        this.relight(this.pos, this.contraPropeller);
    }

    @Override
    protected void _delete() {
        super._delete();
        this.contraPropeller.delete();
    }

    @Override
    public void collectCrumblingInstances(final java.util.function.Consumer<dev.engine_room.flywheel.api.instance.Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(this.contraPropeller);
    }
}
