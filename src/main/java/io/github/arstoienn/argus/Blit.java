package io.github.arstoienn.argus;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Copies the colour of one GL texture into another, scaling if the sizes differ. */
final class Blit {
    private static int readFbo;
    private static int drawFbo;

    private Blit() {
    }

    /** The GL name of a framebuffer's colour texture, or -1 if it has none. */
    static int texture(Framebuffer framebuffer) {
        return framebuffer != null && framebuffer.getColorAttachment() instanceof GlTexture texture
            ? texture.getGlId()
            : -1;
    }

    static void copy(int source, int sourceWidth, int sourceHeight, int target, int targetWidth, int targetHeight) {
        if (readFbo == 0) {
            readFbo = GL30.glGenFramebuffers();
            drawFbo = GL30.glGenFramebuffers();
        }
        // A blit is clipped by the scissor, and the last draw of the interface often leaves it on.
        GlStateManager._disableScissorTest();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, source, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, target, 0);
        boolean sameSize = sourceWidth == targetWidth && sourceHeight == targetHeight;
        GL30.glBlitFramebuffer(0, 0, sourceWidth, sourceHeight, 0, 0, targetWidth, targetHeight,
            GL11.GL_COLOR_BUFFER_BIT, sameSize ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
    }
}
