package io.github.arstoienn.argus;

import java.nio.ByteBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;

/**
 * The second GLFW window and how frames reach it.
 *
 * The window is created with the main window's OpenGL context shared, so textures rendered in the
 * main context can be read in its own. Each frame is copied into a texture Argus owns and drawn
 * from there; that copy is also what lets the window keep showing its last frame after the world,
 * and the game's framebuffers with it, have gone.
 */
final class SecondWindow {
    private static final String VERTEX_SHADER = """
        #version 150
        out vec2 uv;
        void main() {
            // One triangle that covers the whole viewport.
            uv = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String FRAGMENT_SHADER = """
        #version 150
        in vec2 uv;
        out vec4 color;
        uniform sampler2D frame;
        void main() {
            color = texture(frame, uv);
        }
        """;
    /** Window geometry is checked for changes this often, in presented frames. */
    private static final int GEOMETRY_CHECK_FRAMES = 120;

    private static long handle;
    private static boolean failed;
    private static boolean hidden;
    private static String status = "";

    private static GLCapabilities mainCaps;
    private static GLCapabilities ownCaps;
    private static int program;
    private static int frameUniform;
    private static int vao;
    private static int swapInterval;
    /** Signalled once the window has finished reading {@link #frame}, before it is written again. */
    private static long frameRead;

    private static int frame;
    private static int frameWidth;
    private static int frameHeight;

    private static GLFWKeyCallback keyCallback;
    private static boolean fullscreen;
    private static final int[] windowed = new int[4];
    private static int framesSinceGeometryCheck;

    private SecondWindow() {
    }

    /** Opens the window if it is not open yet. False if it could not be opened. */
    static boolean ensure() {
        if (handle == 0L && !failed) {
            create();
        }
        return !failed;
    }

    static boolean exists() {
        return handle != 0L;
    }

    static boolean isHidden() {
        return hidden;
    }

    static boolean isMinimized() {
        return handle != 0L && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
    }

    /** Whether the window is up and something in it can be seen. */
    static boolean isVisible() {
        return handle != 0L && !hidden && !isMinimized();
    }

    static void toggleHidden() {
        if (handle == 0L) {
            return;
        }
        hidden = !hidden;
        if (hidden) {
            GLFW.glfwHideWindow(handle);
        } else {
            GLFW.glfwShowWindow(handle);
        }
        ArgusClient.actionBar("Second window " + (hidden ? "hidden" : "shown"));
    }

    /** Ignores the close button: the window lives as long as the game. */
    static void keepOpen() {
        if (handle != 0L && GLFW.glfwWindowShouldClose(handle)) {
            GLFW.glfwSetWindowShouldClose(handle, false);
        }
    }

    /** Sets the status shown in the corner and in the title. Lines are separated by '\n'. */
    static void setStatus(String text) {
        if (text.equals(status)) {
            return;
        }
        status = text;
        if (handle != 0L) {
            GLFW.glfwSetWindowTitle(handle, text.isEmpty() ? "Argus" : "Argus — " + text.replace("\n", " · "));
        }
    }

    /** Presents a framebuffer, with the drawn pointer over it if {@code cursor} is not null. */
    static void present(Framebuffer source, SecondView.Cursor cursor) {
        int texture = Blit.texture(source);
        if (texture < 0) {
            presentBlank();
            return;
        }
        // Wait for the window to finish reading the last frame before overwriting it.
        if (frameRead != 0L) {
            GL32.glWaitSync(frameRead, 0, GL32.GL_TIMEOUT_IGNORED);
            GL32.glDeleteSync(frameRead);
            frameRead = 0L;
        }
        resizeFrame(source.textureWidth, source.textureHeight);
        Blit.copy(texture, source.textureWidth, source.textureHeight, frame, frameWidth, frameHeight);
        draw(frame, cursor);
    }

    static void presentBlank() {
        draw(0, null);
    }

    static boolean hasHeldFrame() {
        return ArgusConfig.get().freezeLastFrame && frame != 0;
    }

    /** With no world to draw: the last frame if the setting keeps it, otherwise a blank window. */
    static void presentHeld() {
        draw(hasHeldFrame() ? frame : 0, null);
    }

    private static void create() {
        if (FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            failed = true;
            ArgusClient.LOGGER.error("[argus] VulkanMod is installed, which this version does not support yet — second window disabled");
            return;
        }

        long mainHandle = MinecraftClient.getInstance().getWindow().getHandle();
        mainCaps = GL.getCapabilities();
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        // A shared context must have the main window's profile. macOS refuses to share a legacy
        // context, the default, with a core one; and it has to be 3.2 rather than the 4.1 the main
        // window reports, because macOS asks for 3.x and 4.x with different pixel formats.
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        WindowState state = WindowState.current;
        handle = GLFW.glfwCreateWindow(state.windowWidth, state.windowHeight, "Argus", 0L, mainHandle);
        if (handle == 0L) {
            failed = true;
            ArgusClient.LOGGER.error("[argus] glfwCreateWindow failed — second window disabled (main context {}.{}, profile 0x{}, forward-compatible {})",
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_CONTEXT_VERSION_MAJOR),
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_CONTEXT_VERSION_MINOR),
                Integer.toHexString(GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_OPENGL_PROFILE)),
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_OPENGL_FORWARD_COMPAT));
            // GLFW only creates windows on the main thread. Ixeris moves the game onto another
            // thread that it still names "Render thread", so nothing here looks wrong.
            if (FabricLoader.getInstance().isModLoaded("ixeris")) {
                ArgusClient.LOGGER.error("[argus] Ixeris is installed. It moves the game off the main thread, where GLFW requires windows to be created, "
                    + "which is almost certainly why this failed. Remove Ixeris to get the second window back.");
            }
            return;
        }
        ArgusClient.LOGGER.info("[argus] second window created (handle={})", handle);

        if (state.windowX >= 0 && state.windowY >= 0) {
            GLFW.glfwSetWindowPos(handle, state.windowX, state.windowY);
        }
        keyCallback = GLFWKeyCallback.create((window, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_F11 && action == GLFW.GLFW_PRESS) {
                toggleFullscreen();
            }
        });
        GLFW.glfwSetKeyCallback(handle, keyCallback);

        // Programs are shared between the two contexts; vertex arrays are not, so that one is made
        // in the window's own context.
        program = buildProgram();
        frameUniform = GL20.glGetUniformLocation(program, "frame");
        GLFW.glfwMakeContextCurrent(handle);
        ownCaps = GL.createCapabilities();
        GLFW.glfwSwapInterval(0);
        vao = GL30.glGenVertexArrays();
        GLFW.glfwMakeContextCurrent(mainHandle);
        GL.setCapabilities(mainCaps);
        GLFW.glfwShowWindow(handle);
    }

    private static void resizeFrame(int width, int height) {
        if (frame != 0 && frameWidth == width && frameHeight == height) {
            return;
        }
        if (frame == 0) {
            frame = GL11.glGenTextures();
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, frame);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        frameWidth = width;
        frameHeight = height;
    }

    /** Draws a texture (0 for a blank window), the status and the pointer, and swaps. */
    private static void draw(int texture, SecondView.Cursor cursor) {
        long mainHandle = MinecraftClient.getInstance().getWindow().getHandle();
        // The copy into the texture was issued in the main context; make the window's context wait for it.
        long copied = 0L;
        if (texture != 0) {
            copied = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
        }

        GLFW.glfwMakeContextCurrent(handle);
        GL.setCapabilities(ownCaps);
        if (copied != 0L) {
            GL32.glWaitSync(copied, 0, GL32.GL_TIMEOUT_IGNORED);
        }
        int wantedInterval = ArgusConfig.get().secondWindowVsync ? 1 : 0;
        if (wantedInterval != swapInterval) {
            GLFW.glfwSwapInterval(wantedInterval);
            swapInterval = wantedInterval;
        }

        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(handle, width, height);
        GL11.glViewport(0, 0, width[0], height[0]);
        if (texture == 0) {
            GL11.glClearColor(0.06F, 0.06F, 0.07F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } else {
            // No clear first: the triangle covers every pixel, and with vsync off a torn scan-out
            // could catch the cleared colour, which showed as a black flash along the top.
            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL20.glUniform1i(frameUniform, 0);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
        }
        if (ArgusConfig.get().showStatusText) {
            BitmapText.drawStatus(status, width[0], height[0]);
        }
        if (cursor != null) {
            // The pointer is placed as a fraction of the main window, so it lands in the same place
            // whatever size this window is. GL's origin is the bottom left, the pointer's the top left.
            int x = (int) Math.round(cursor.x() * width[0]);
            int y = (int) Math.round((1.0 - cursor.y()) * height[0]);
            BitmapText.drawCursor(x, y, Math.max(1, Math.round(height[0] / 700.0F)));
        }
        GLFW.glfwSwapBuffers(handle);
        if (texture != 0) {
            frameRead = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
        }

        GLFW.glfwMakeContextCurrent(mainHandle);
        GL.setCapabilities(mainCaps);
        if (copied != 0L) {
            GL32.glDeleteSync(copied);
        }
        rememberGeometry();
    }

    private static void rememberGeometry() {
        if (fullscreen || ++framesSinceGeometryCheck < GEOMETRY_CHECK_FRAMES) {
            return;
        }
        framesSinceGeometryCheck = 0;
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, x, y);
        GLFW.glfwGetWindowSize(handle, width, height);
        WindowState state = WindowState.current;
        boolean changed = x[0] != state.windowX || y[0] != state.windowY
            || width[0] != state.windowWidth || height[0] != state.windowHeight;
        if (width[0] > 0 && height[0] > 0 && changed) {
            state.windowX = x[0];
            state.windowY = y[0];
            state.windowWidth = width[0];
            state.windowHeight = height[0];
            WindowState.save();
        }
    }

    private static void toggleFullscreen() {
        if (fullscreen) {
            GLFW.glfwSetWindowMonitor(handle, 0L, windowed[0], windowed[1], windowed[2], windowed[3], GLFW.GLFW_DONT_CARE);
            fullscreen = false;
            return;
        }
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, x, y);
        GLFW.glfwGetWindowSize(handle, width, height);
        windowed[0] = x[0];
        windowed[1] = y[0];
        windowed[2] = width[0];
        windowed[3] = height[0];
        long monitor = monitorAt(x[0] + width[0] / 2, y[0] + height[0] / 2);
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
            fullscreen = true;
        }
    }

    /** The monitor containing a point in screen coordinates, or the primary one. */
    private static long monitorAt(int x, int y) {
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null) {
            while (monitors.hasRemaining()) {
                long monitor = monitors.get();
                int[] left = new int[1];
                int[] top = new int[1];
                GLFW.glfwGetMonitorPos(monitor, left, top);
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode != null && x >= left[0] && x < left[0] + mode.width() && y >= top[0] && y < top[0] + mode.height()) {
                    return monitor;
                }
            }
        }
        return GLFW.glfwGetPrimaryMonitor();
    }

    private static int buildProgram() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("[argus] shader link failed: " + GL20.glGetProgramInfoLog(linked));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return linked;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("[argus] shader compile failed: " + GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
