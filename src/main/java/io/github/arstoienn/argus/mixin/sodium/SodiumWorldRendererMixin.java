package io.github.arstoienn.argus.mixin.sodium;

import io.github.arstoienn.argus.SecondPass;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium replaces the terrain renderer and assumes one camera per frame.
 *
 * It recomputes the visible sections for whichever camera setupTerrain is handed, so the second
 * view already draws correctly. What has to be kept off it is the trace the second camera leaves:
 *
 * - It remembers the previous camera to decide when to recompute and which translucent faces the
 *   camera crossed. With two cameras taking turns it would see the camera jumping back and forth
 *   every frame and keep re-sorting, half the time for the wrong camera. That record is saved
 *   before the second pass and put back after it.
 * - It times prepareFrame calls to set the chunk upload budget. Called twice a frame it would think
 *   the frame rate had doubled and halve the budget, so the second pass skips it.
 *
 * Its visible list is single too, and holds the second camera's after the pass, so it is marked
 * stale on the way out for the main view to recompute its own.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class SodiumWorldRendererMixin {
    @Shadow
    private Vector3d lastCameraPos;
    @Shadow
    private double lastCameraPitch;
    @Shadow
    private double lastCameraYaw;
    @Shadow
    private FogParameters lastFogParameters;
    @Shadow
    private Matrix4f lastProjectionMatrix;
    @Shadow
    private RenderSectionManager renderSectionManager;

    @Unique
    private boolean argus$saved;
    @Unique
    private Vector3d argus$cameraPos;
    @Unique
    private double argus$cameraPitch;
    @Unique
    private double argus$cameraYaw;
    @Unique
    private FogParameters argus$fogParameters;
    @Unique
    private Matrix4f argus$projectionMatrix;

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void argus$enterSecondPass(CallbackInfo ci) {
        // A pass that threw never reached RETURN; put the main camera's record back first.
        this.argus$restoreMainCamera();
        if (SecondPass.active && this.renderSectionManager != null) {
            this.argus$cameraPos = this.lastCameraPos;
            this.argus$cameraPitch = this.lastCameraPitch;
            this.argus$cameraYaw = this.lastCameraYaw;
            this.argus$fogParameters = this.lastFogParameters;
            // Sodium changes this matrix in place, so it needs a copy.
            this.argus$projectionMatrix = this.lastProjectionMatrix == null ? null : new Matrix4f(this.lastProjectionMatrix);
            this.argus$saved = true;
            this.renderSectionManager.markGraphDirty();
        }
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void argus$exitSecondPass(CallbackInfo ci) {
        this.argus$restoreMainCamera();
    }

    @Unique
    private void argus$restoreMainCamera() {
        if (!this.argus$saved) {
            return;
        }
        this.argus$saved = false;
        this.lastCameraPos = this.argus$cameraPos;
        this.lastCameraPitch = this.argus$cameraPitch;
        this.lastCameraYaw = this.argus$cameraYaw;
        this.lastFogParameters = this.argus$fogParameters;
        this.lastProjectionMatrix = this.argus$projectionMatrix;
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    @Redirect(
        method = "setupTerrain",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;prepareFrame(Lorg/joml/Vector3dc;)V"))
    private void argus$prepareOncePerFrame(RenderSectionManager manager, Vector3dc cameraPosition) {
        if (!SecondPass.active) {
            manager.prepareFrame(cameraPosition);
        }
    }

    @Redirect(
        method = "setupTerrain",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;processGFNIMovement(Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/trigger/CameraMovement;)V"))
    private void argus$noResortInSecondPass(RenderSectionManager manager, CameraMovement movement) {
        if (!SecondPass.active) {
            manager.processGFNIMovement(movement);
        }
    }
}
