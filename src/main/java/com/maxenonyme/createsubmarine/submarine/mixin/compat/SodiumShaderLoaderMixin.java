package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumShaderLoaderMixin {

    private static final String UNIFORM_BLOCK = "uniform vec2 ScreenSize;\n" +
            "uniform sampler2D SableCloseSampler;\n" +
            "uniform sampler2D SableFarSampler;\n" +
            "uniform float SableWaterOcclusionEnabled;\n";

    private static final String DISCARD_BLOCK = "    if (SableWaterOcclusionEnabled > 0.0) {\n" +
            "        float _csubCloseDepth = texture(SableCloseSampler, gl_FragCoord.xy / ScreenSize).r;\n" +
            "        float _csubFarDepth = texture(SableFarSampler, gl_FragCoord.xy / ScreenSize).r;\n" +
            "        float _csubFragDepth = gl_FragCoord.z;\n" +
            "        if (_csubCloseDepth < 1.0 && _csubFragDepth > _csubCloseDepth - 0.0015 && _csubFragDepth < _csubFarDepth) { discard; }\n" +
            "    }\n";

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void createsubmarine$injectOcclusion(ResourceLocation location, CallbackInfoReturnable<String> cir) {
        String path = location.getPath();
        if (!path.endsWith(".fsh"))
            return;
        if (!path.contains("block_layer"))
            return;

        String source = cir.getReturnValue();
        if (source == null || source.contains("SableWaterOcclusionEnabled"))
            return;

        StringBuilder out = new StringBuilder(source.length() + 1024);

        int versionEnd = -1;
        int versionIdx = source.indexOf("#version");
        if (versionIdx >= 0) {
            versionEnd = source.indexOf('\n', versionIdx);
        }
        if (versionEnd >= 0) {
            out.append(source, 0, versionEnd + 1);
            out.append(UNIFORM_BLOCK);
            String rest = source.substring(versionEnd + 1);
            String injected = injectDiscardInMain(rest);
            out.append(injected);
        } else {
            out.append(UNIFORM_BLOCK);
            out.append(injectDiscardInMain(source));
        }

        cir.setReturnValue(out.toString());
    }

    private static String injectDiscardInMain(String source) {
        int mainIdx = source.indexOf("void main()");
        if (mainIdx < 0)
            mainIdx = source.indexOf("void main(");
        if (mainIdx < 0)
            return source;
        int braceIdx = source.indexOf('{', mainIdx);
        if (braceIdx < 0)
            return source;
        return source.substring(0, braceIdx + 1) + "\n" + DISCARD_BLOCK + source.substring(braceIdx + 1);
    }
}
