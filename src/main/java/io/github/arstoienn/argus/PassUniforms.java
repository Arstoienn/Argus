package io.github.arstoienn.argus;

import io.github.arstoienn.argus.mixin.GameRendererAccessor;
import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.render.ProjectionMatrix3;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.fog.FogRenderer;

/**
 * The second pass's own projection, fog and global-settings buffers.
 *
 * The game has one object for each, holding one GPU buffer that is written once a frame. A second
 * pass writing into the same buffers replaces the main view's values for the frame. On OpenGL that
 * does not matter, because every draw happens as it is issued. A backend that records draws and
 * executes them later (VulkanMod) would run the main view's pending draws with the second view's
 * field of view and fog, and its entities, name tags and hitbox lines would jump whenever the two
 * differ. With objects of its own the second pass never touches the main view's buffers.
 */
public final class PassUniforms {
    private static GlobalSettings globalSettings;
    private static RawProjectionMatrix worldProjection;
    private static ProjectionMatrix3 hudProjection;
    private static FogRenderer fog;

    private static GlobalSettings mainGlobalSettings;
    private static RawProjectionMatrix mainWorldProjection;
    private static ProjectionMatrix3 mainHudProjection;
    private static FogRenderer mainFog;
    private static boolean swapped;

    private PassUniforms() {
    }

    /** Points the renderer at the second pass's objects. Returns the global settings to fill in. */
    static GlobalSettings swapIn(GameRendererAccessor renderer) {
        if (globalSettings == null) {
            globalSettings = new GlobalSettings();
            worldProjection = new RawProjectionMatrix("argus level");
            hudProjection = new ProjectionMatrix3("argus 3d hud", 0.05F, 100.0F);
            fog = new FogRenderer();
        }
        mainGlobalSettings = renderer.argus$globalSettings();
        mainWorldProjection = renderer.argus$worldProjectionMatrix();
        mainHudProjection = renderer.argus$hudProjectionMatrix();
        mainFog = renderer.argus$fogRenderer();
        renderer.argus$setGlobalSettings(globalSettings);
        renderer.argus$setWorldProjectionMatrix(worldProjection);
        renderer.argus$setHudProjectionMatrix(hudProjection);
        renderer.argus$setFogRenderer(fog);
        swapped = true;
        return globalSettings;
    }

    /** Gives the renderer its own objects back. */
    static void swapOut(GameRendererAccessor renderer) {
        if (!swapped) {
            return;
        }
        swapped = false;
        // The fog buffers are a ring that the game advances once a frame, after drawing the world.
        fog.rotate();
        renderer.argus$setGlobalSettings(mainGlobalSettings);
        renderer.argus$setWorldProjectionMatrix(mainWorldProjection);
        renderer.argus$setHudProjectionMatrix(mainHudProjection);
        renderer.argus$setFogRenderer(mainFog);
    }

    /** Frees the buffers, when the game renderer closes. */
    public static void close() {
        if (globalSettings != null) {
            globalSettings.close();
            worldProjection.close();
            hudProjection.close();
            fog.close();
            globalSettings = null;
        }
    }
}
