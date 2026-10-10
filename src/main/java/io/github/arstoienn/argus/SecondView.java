package io.github.arstoienn.argus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.Window;

/**
 * The framebuffers the second view is drawn into, and the redirect that sends the game's drawing
 * there.
 *
 * The world and the interface are kept apart. The world is drawn by the second pass, which the
 * frame budget may skip for several frames; the interface is replayed every frame onto a copy of
 * the latest world picture, so menus, dragged items and the pointer keep up even while the world
 * behind them is being presented again.
 */
public final class SecondView {
    /** Where the pointer was when this frame's interface was drawn, as a fraction of the main window. */
    record Cursor(double x, double y) {
    }

    /** While set, {@code MinecraftClient.getFramebuffer} returns this instead of the main window's. */
    public static Framebuffer redirect;

    private static SimpleFramebuffer world;
    /** The world framebuffer was just created or resized and holds nothing yet. */
    private static boolean worldEmpty;
    private static SimpleFramebuffer withInterface;
    private static boolean interfaceDrawn;
    private static Cursor cursor;

    private SecondView() {
    }

    /** The framebuffer the second pass draws the world into, sized by the resolution setting. */
    static Framebuffer world(int mainWidth, int mainHeight) {
        float scale = ArgusConfig.get().resolutionScale();
        int width = Math.max(1, Math.round(mainWidth * scale));
        int height = Math.max(1, Math.round(mainHeight * scale));
        if (world == null) {
            world = new SimpleFramebuffer("argus_world", width, height, true);
            worldEmpty = true;
        } else if (world.textureWidth != width || world.textureHeight != height) {
            world.resize(width, height);
            worldEmpty = true;
        }
        return world;
    }

    static boolean worldEmpty() {
        return worldEmpty;
    }

    static void worldDrawn() {
        worldEmpty = false;
    }

    public static boolean wantsInterface() {
        return ArgusConfig.get().secondViewHud
            && MinecraftClient.getInstance().world != null
            && SecondWindow.isVisible();
    }

    /**
     * Copies the latest world picture into the interface framebuffer and returns it for this frame's
     * interface to be drawn over, or null if there is no world picture yet.
     *
     * It is always the size of the main window's framebuffer, whatever the resolution setting: the
     * interface's scissor rectangles are in main-window pixels and would cut in the wrong places on
     * a smaller target.
     */
    public static Framebuffer beginInterface() {
        if (world == null || worldEmpty) {
            return null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();
        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();
        int source = Blit.texture(world);
        if (width <= 0 || height <= 0 || source < 0) {
            return null;
        }
        if (withInterface == null) {
            withInterface = new SimpleFramebuffer("argus_interface", width, height, true);
        } else if (withInterface.textureWidth != width || withInterface.textureHeight != height) {
            withInterface.resize(width, height);
        }
        Blit.copy(source, world.textureWidth, world.textureHeight, Blit.texture(withInterface), width, height);

        // The pointer is only drawn while a screen is open; in play it is locked to the centre.
        // Its position is the one the interface uses for hovering this frame, not a fresher one
        // from GLFW, so the pointer and what it highlights stay together.
        cursor = mc.currentScreen != null && window.getWidth() > 0 && window.getHeight() > 0
            ? new Cursor(mc.mouse.getX() / window.getWidth(), mc.mouse.getY() / window.getHeight())
            : null;
        interfaceDrawn = true;
        return withInterface;
    }

    /** This frame's world-plus-interface framebuffer, or null if none was drawn. Clears it. */
    static Framebuffer takeInterface() {
        boolean drawn = interfaceDrawn;
        interfaceDrawn = false;
        return drawn ? withInterface : null;
    }

    static Cursor cursor() {
        return cursor;
    }
}
