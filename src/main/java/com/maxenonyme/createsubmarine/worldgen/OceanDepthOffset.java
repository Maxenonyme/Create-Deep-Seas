package com.maxenonyme.createsubmarine.worldgen;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record OceanDepthOffset(Holder<DensityFunction> input, Holder<DensityFunction> continents)
        implements DensityFunction {
    public static final MapCodec<OceanDepthOffset> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(OceanDepthOffset::input),
            DensityFunction.CODEC.fieldOf("continents").forGetter(OceanDepthOffset::continents))
            .apply(instance, OceanDepthOffset::new));

    public static final KeyDispatchDataCodec<OceanDepthOffset> CODEC_HOLDER = KeyDispatchDataCodec.of(CODEC);

    private static final double DENSITY_PER_BLOCK = 1.0 / 128.0;
    private static final double MAX_DEEPEN = 256.0 * DENSITY_PER_BLOCK;

    private static volatile boolean enabled = false;
    private static volatile double deepen = 10.0 * DENSITY_PER_BLOCK;

    public static void refreshConfig() {
        enabled = SubmarineConfig.ENABLE_DEEPER_OCEANS.get();
        deepen = SubmarineConfig.DEEPER_OCEANS_DEPTH.get() * DENSITY_PER_BLOCK;
    }

    @Override
    public double compute(FunctionContext context) {
        double offset = input.value().compute(context);
        if (!enabled) {
            return offset;
        }
        double c = continents.value().compute(context);
        double s;
        if (c <= -0.20) {
            s = 1.0;
        } else if (c < -0.08) {
            double u = (-0.08 - c) / 0.12;
            s = u * u * (3 - 2 * u);
        } else {
            return offset;
        }
        return offset - deepen * s;
    }

    @Override
    public void fillArray(double[] doubles, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(doubles, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new OceanDepthOffset(
                Holder.direct(input.value().mapAll(visitor)),
                Holder.direct(continents.value().mapAll(visitor))));
    }

    @Override
    public double minValue() {
        return input.value().minValue() - MAX_DEEPEN;
    }

    @Override
    public double maxValue() {
        return input.value().maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC_HOLDER;
    }
}
