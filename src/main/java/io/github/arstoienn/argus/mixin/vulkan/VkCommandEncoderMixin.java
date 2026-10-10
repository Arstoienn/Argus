package io.github.arstoienn.argus.mixin.vulkan;

import io.github.arstoienn.argus.SecondView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps VulkanMod from mistaking the second view's framebuffer for the main window.
 *
 * VulkanMod's command encoder decides whether a render target is the main window, and so whether
 * to draw into the swapchain, by comparing it with MinecraftClient.getFramebuffer. That is the very
 * call Argus redirects, so the second view would be drawn into the main window and its own
 * framebuffer never written. Inside this class the call always answers with the real main
 * framebuffer, which leaves Argus's as an ordinary offscreen texture.
 *
 * Applied only when VulkanMod is installed; see ArgusMixinPlugin.
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkCommandEncoder")
public class VkCommandEncoderMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private Framebuffer argus$realMainFramebuffer(MinecraftClient client) {
        Framebuffer redirect = SecondView.redirect;
        SecondView.redirect = null;
        try {
            return client.getFramebuffer();
        } finally {
            SecondView.redirect = redirect;
        }
    }
}
