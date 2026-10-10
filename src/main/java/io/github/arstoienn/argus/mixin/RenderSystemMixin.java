package io.github.arstoienn.argus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.arstoienn.argus.ArgusClient;
import io.github.arstoienn.argus.VulkanWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Presents the second window after the main frame when VulkanMod is installed.
 *
 * VulkanMod turns the buffer swap in flipFrame into "submit and present the main frame". The second
 * window's blit has to be submitted after that, so it hangs here. Without VulkanMod the second
 * window has already been swapped at the end of the second pass and this does nothing.
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Inject(method = "flipFrame", at = @At("TAIL"))
    private static void argus$presentSecondWindow(CallbackInfo ci) {
        if (ArgusClient.VULKANMOD) {
            VulkanWindow.presentPending();
        }
    }
}
