package io.github.arstoienn.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import io.github.arstoienn.argus.SecondView;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replays each frame's interface onto the second view.
 *
 * GuiRenderer.render first prepares the frame's interface as meshes and draws, then submits them to
 * the main framebuffer, then clears them. Submitting them once more in between, with the
 * framebuffer redirected, gives the second view exactly what the main window got without running
 * any interface logic twice. Calling render again afterwards would not work: preparing adds text
 * and items back into the render state, so they would be drawn twice over.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
    @WrapOperation(
        method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;renderPreparedDraws(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void argus$replayOnSecondView(GuiRenderer renderer, GpuBufferSlice fogBuffer, Operation<Void> original) {
        original.call(renderer, fogBuffer);
        if (SecondView.redirect != null || !SecondView.wantsInterface()) {
            return;
        }
        Framebuffer target = SecondView.beginInterface();
        if (target == null) {
            return;
        }
        SecondView.redirect = target;
        try {
            original.call(renderer, fogBuffer);
        } finally {
            SecondView.redirect = null;
        }
    }
}
