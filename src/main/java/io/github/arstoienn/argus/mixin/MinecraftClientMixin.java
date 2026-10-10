package io.github.arstoienn.argus.mixin;

import io.github.arstoienn.argus.SecondView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void argus$redirectFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        if (SecondView.redirect != null) {
            cir.setReturnValue(SecondView.redirect);
        }
    }
}
