package io.github.arstoienn.argus.mixin;

import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ProjectionMatrix3;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fovMultiplier")
    float argus$fovMultiplier();

    @Accessor("fovMultiplier")
    void argus$setFovMultiplier(float value);

    @Accessor("lastFovMultiplier")
    float argus$lastFovMultiplier();

    @Accessor("lastFovMultiplier")
    void argus$setLastFovMultiplier(float value);

    // The four below hold the per-frame uniform buffers. They are final in the game; the second
    // pass swaps its own in for as long as it runs (see PassUniforms).

    @Accessor("globalSettings")
    GlobalSettings argus$globalSettings();

    @Mutable
    @Accessor("globalSettings")
    void argus$setGlobalSettings(GlobalSettings value);

    @Accessor("worldProjectionMatrix")
    RawProjectionMatrix argus$worldProjectionMatrix();

    @Mutable
    @Accessor("worldProjectionMatrix")
    void argus$setWorldProjectionMatrix(RawProjectionMatrix value);

    @Accessor("hudProjectionMatrix")
    ProjectionMatrix3 argus$hudProjectionMatrix();

    @Mutable
    @Accessor("hudProjectionMatrix")
    void argus$setHudProjectionMatrix(ProjectionMatrix3 value);

    @Accessor("fogRenderer")
    FogRenderer argus$fogRenderer();

    @Mutable
    @Accessor("fogRenderer")
    void argus$setFogRenderer(FogRenderer value);

    @Accessor("entityRenderDispatcher")
    RenderDispatcher argus$entityRenderDispatcher();
}
