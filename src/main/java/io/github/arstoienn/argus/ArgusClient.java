package io.github.arstoienn.argus;

import main.walksy.lib.core.gui.impl.WalksyLibConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArgusClient implements ClientModInitializer {
    public static final String MOD_ID = "argus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /**
     * VulkanMod replaces the rendering backend, so the main window has no OpenGL context. The
     * second window is then created, filled and presented through {@link VulkanWindow} and the
     * game's own rendering API, and none of the code that calls GL directly may run.
     */
    public static final boolean VULKANMOD = FabricLoader.getInstance().isModLoaded("vulkanmod");

    /** Groups the keys under an "Argus" heading in the Controls screen. */
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "second_view"));

    @Override
    public void onInitializeClient() {
        ArgusConfig.get().config().load();
        WindowState.load();

        // The three bound keys sit in the cluster at the top right of the keyboard, which few mods
        // claim. Hiding the window and opening the settings are used too rarely to earn a default.
        KeyBinding follow = register("key.argus.follow_player", GLFW.GLFW_KEY_BACKSLASH);
        KeyBinding stopFollowing = register("key.argus.clear_follow", GLFW.GLFW_KEY_BACKSPACE);
        KeyBinding togglePerspective = register("key.argus.follow_perspective", GLFW.GLFW_KEY_RIGHT_BRACKET);
        KeyBinding hide = register("key.argus.hide", InputUtil.UNKNOWN_KEY.getCode());
        KeyBinding openSettings = register("key.argus.open_config", InputUtil.UNKNOWN_KEY.getCode());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.wasPressed()) {
                client.setScreen(new WalksyLibConfigScreen(client.currentScreen, ArgusConfig.get().config()));
            }
            while (follow.wasPressed()) {
                Follow.pressFollow(client);
            }
            while (stopFollowing.wasPressed()) {
                Follow.stop();
            }
            while (togglePerspective.wasPressed()) {
                Follow.togglePerspective();
            }
            while (hide.wasPressed()) {
                SecondWindow.toggleHidden();
            }
        });

        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
        LOGGER.info("[argus] v{} initialised", version);
    }

    /** Shows a short message above the hotbar. */
    static void actionBar(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), true);
        }
    }

    private static KeyBinding register(String translationKey, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, key, CATEGORY));
    }
}
