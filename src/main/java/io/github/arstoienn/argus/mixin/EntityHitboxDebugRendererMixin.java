package io.github.arstoienn.argus.mixin;

import io.github.arstoienn.argus.ArgusConfig;
import io.github.arstoienn.argus.SecondPass;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes follow F3+B in the second view too, unless the setting keeps it clean. */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @Inject(method = "render(DDDLnet/minecraft/world/debug/DebugDataStore;Lnet/minecraft/client/render/Frustum;F)V", at = @At("HEAD"), cancellable = true)
    private void argus$hitboxesInSecondPass(CallbackInfo ci) {
        if (SecondPass.active && !ArgusConfig.get().showHitboxes) {
            ci.cancel();
        }
    }
}
