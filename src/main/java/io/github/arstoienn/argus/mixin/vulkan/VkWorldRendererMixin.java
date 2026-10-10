package io.github.arstoienn.argus.mixin.vulkan;

import io.github.arstoienn.argus.SecondPass;
import net.vulkanmod.render.chunk.SectionGrid;
import net.vulkanmod.vulkan.memory.buffer.IndirectBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the second camera from disturbing VulkanMod's terrain renderer.
 *
 * VulkanMod has a terrain renderer of its own with a single set of state, built for one camera a
 * frame. WorldRendererMixin does the same kind of thing for the vanilla state, which VulkanMod
 * does not use.
 *
 * Applied only when VulkanMod is installed; see ArgusMixinPlugin.
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.chunk.WorldRenderer", remap = false)
public class VkWorldRendererMixin {
    /**
     * The section grid moves with the chunk the camera is in. The two cameras are often either side
     * of a chunk border, and letting both move it would shift the grid there and back every frame,
     * dropping and rebuilding the sections along its edge. It stays with the main camera; the
     * second view can only see what the main view has loaded anyway.
     */
    @Redirect(method = "setupRenderer", at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/chunk/SectionGrid;repositionCamera(DD)V"))
    private void argus$keepGridOnMainCamera(SectionGrid grid, double x, double z) {
        if (!SecondPass.active) {
            grid.repositionCamera(x, z);
        }
    }

    /**
     * The main view's indirect draws for this frame are already recorded and point at the start of
     * this buffer. A reset would let the second pass write over them before they have run, so the
     * second pass appends instead.
     */
    @Redirect(method = "setupRenderer", at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/memory/buffer/IndirectBuffer;reset()V"))
    private void argus$appendIndirectDraws(IndirectBuffer buffer) {
        if (!SecondPass.active) {
            buffer.reset();
        }
    }

    /** Translucent geometry keeps the main camera's sort order, as it does without VulkanMod. */
    @Inject(method = "sortTranslucentSections", at = @At("HEAD"), cancellable = true)
    private void argus$noResortInSecondPass(CallbackInfo ci) {
        if (SecondPass.active) {
            ci.cancel();
        }
    }
}
