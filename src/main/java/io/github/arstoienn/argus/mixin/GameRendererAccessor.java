package io.github.arstoienn.argus.mixin;

import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.command.RenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
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

    @Accessor("globalSettings")
    GlobalSettings argus$globalSettings();

    @Accessor("entityRenderDispatcher")
    RenderDispatcher argus$entityRenderDispatcher();
}
