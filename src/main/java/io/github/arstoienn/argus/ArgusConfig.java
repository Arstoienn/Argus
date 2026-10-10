package io.github.arstoienn.argus;

import main.walksy.lib.api.WalksyLibConfig;
import main.walksy.lib.core.config.impl.LocalConfig;
import main.walksy.lib.core.config.local.Category;
import main.walksy.lib.core.config.local.OptionDescription;
import main.walksy.lib.core.config.local.options.BooleanOption;
import main.walksy.lib.core.config.local.options.ButtonOption;
import main.walksy.lib.core.config.local.options.NumericalOption;
import main.walksy.lib.core.config.local.options.groups.OptionGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.KeybindsScreen;

/** The settings, with WalksyLib's screen for them, stored in {@code config/argus.json}. */
public final class ArgusConfig implements WalksyLibConfig {
    private static final ArgusConfig INSTANCE = new ArgusConfig();

    // Performance
    public int secondViewBudgetPercent = 25;
    public int secondViewRenderDistance = 0;
    public int resolutionPercent = 100;

    // Camera
    public boolean mirrorPerspective = true;
    public boolean cameraSmoothingEnabled = true;
    public int cameraSmoothingPercent = 50;
    public int cameraSnapDistance = 32;
    public double followDistance = 4.0;
    public double shoulderSide = 0.5;
    public double shoulderUp = 0.3;
    public boolean followProjectiles = true;
    public boolean followFirstPerson = false;

    // Behaviour
    public boolean secondViewHud = true;
    public boolean secondViewScreens = true;
    public boolean showStatusText = true;
    public boolean showHitboxes = true;
    public boolean freezeLastFrame = true;
    public boolean secondWindowVsync = false;

    private ArgusConfig() {
    }

    public static ArgusConfig get() {
        return INSTANCE;
    }

    public LocalConfig config() {
        return getOrCreateConfig();
    }

    float resolutionScale() {
        return resolutionPercent / 100.0F;
    }

    /** Smoothing strength from 0 to 1, where 0, like the switch being off, means none. */
    double cameraSmoothing() {
        return cameraSmoothingEnabled ? cameraSmoothingPercent / 100.0 : 0.0;
    }

    @Override
    public LocalConfig define() {
        OptionGroup camera = OptionGroup.createBuilder("Camera")
            .setExpanded(true)
            .addOption(BooleanOption.createBuilder("Complementary view",
                    () -> mirrorPerspective, true, v -> mirrorPerspective = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "When you put the main window into third person, the second window\n"
                        + "flips to your own first-person view instead of showing another\n"
                        + "third-person one.\n"
                        + "Two third-person views tell you the same thing twice. What third\n"
                        + "person actually costs you is the near field your own body covers\n"
                        + "and the hand animations - mining progress, bow draw, eating,\n"
                        + "attack swing. This gives both back.\n"
                        + "It follows the direction the main window's camera is actually\n"
                        + "looking, not the direction your character's head is facing, so it\n"
                        + "stays lined up under mods that decouple the two.\n"
                        + "Off leaves the second window on the over-the-shoulder view whatever\n"
                        + "the main window does.\n"
                        + "Following a player or a projectile still takes priority."))
                .build())
            .addOption(BooleanOption.createBuilder("Camera smoothing",
                    () -> cameraSmoothingEnabled, true, v -> cameraSmoothingEnabled = v)
                .onChange(CameraRig::reset)
                .description(OptionDescription.ofOrderedString(() ->
                    "Ease the second camera into position instead of snapping to it.\n"
                        + "Most noticeable when you switch follow targets.\n"
                        + "Off puts the camera exactly where it belongs on every frame."))
                .build())
            .addOption(NumericalOption.createBuilder("Smoothing amount",
                    () -> cameraSmoothingPercent, 50, v -> cameraSmoothingPercent = v)
                .values(0, 100, 5)
                .availability(() -> cameraSmoothingEnabled, "Turn Camera smoothing on first.")
                .description(OptionDescription.ofOrderedString(() ->
                    "How far the camera lags behind where it should be.\n"
                        + "Higher is smoother but slower to catch up.\n"
                        + "Frame-rate independent: 30 fps and 200 fps trace the same path.\n"
                        + "That matters here because the second view deliberately drops\n"
                        + "frames to stay inside its frame-time budget.\n"
                        + "Projectile tracking uses a much shorter time constant internally -\n"
                        + "an arrow moves ~60 blocks/s and would otherwise leave the frame."))
                .build())
            .addOption(NumericalOption.createBuilder("Snap distance",
                    () -> cameraSnapDistance, 32, v -> cameraSnapDistance = v)
                .values(1, 256, 1)
                .availability(() -> cameraSmoothingEnabled, "Turn Camera smoothing on first.")
                .description(OptionDescription.ofOrderedString(() ->
                    "If the camera would have to travel further than this in one step it\n"
                        + "jumps instead of gliding, in blocks.\n"
                        + "Stops a long swoop across the world on a dimension change,\n"
                        + "a teleport, or locking onto someone far away."))
                .build())
            .addOption(NumericalOption.createBuilder("Follow distance",
                    () -> followDistance, 4.0, v -> followDistance = v)
                .values(1.0, 12.0, 0.5)
                .description(OptionDescription.ofOrderedString(() ->
                    "How far behind the followed player or projectile the camera sits.\n"
                        + "Walls still pull it closer so it never ends up inside a block."))
                .build())
            .addOption(NumericalOption.createBuilder("Shoulder offset",
                    () -> shoulderSide, 0.5, v -> shoulderSide = v)
                .values(-2.0, 2.0, 0.1)
                .description(OptionDescription.ofOrderedString(() ->
                    "Slide the second camera sideways instead of sitting dead behind you.\n"
                        + "Positive is over your right shoulder, negative over your left,\n"
                        + "0 is straight behind.\n"
                        + "Dead centre means your own back fills the middle of the frame,\n"
                        + "which is exactly where you want to be looking. Sliding the camera\n"
                        + "off to one side clears it.\n"
                        + "Walls still push the camera back in, so it never ends up inside\n"
                        + "a block when you hug a corner."))
                .build())
            .addOption(NumericalOption.createBuilder("Height offset",
                    () -> shoulderUp, 0.3, v -> shoulderUp = v)
                .values(-1.0, 1.5, 0.1)
                .description(OptionDescription.ofOrderedString(() ->
                    "Raise or lower the second camera relative to your eyes.\n"
                        + "A little height helps when you look down: the camera sees over\n"
                        + "your head instead of through it.\n"
                        + "Measured along world up, not your view direction, so looking up\n"
                        + "and down does not swing the camera around."))
                .build())
            .addOption(BooleanOption.createBuilder("Follow your projectiles",
                    () -> followProjectiles, true, v -> followProjectiles = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Swing the second window onto your own arrows, tridents and ender pearls\n"
                        + "while they are in the air, then hand it back when they land.\n"
                        + "This takes priority over everything else, including following a\n"
                        + "player, so every shot pulls the window away from whatever you had\n"
                        + "it on. Turn it off if you are using the second window to watch your\n"
                        + "own surroundings and want it to stay there.\n"
                        + "Off also skips the per-tick scan over every entity in the world\n"
                        + "that finds the projectile."))
                .build())
            .addOption(BooleanOption.createBuilder("First-person follow",
                    () -> followFirstPerson, false, v -> followFirstPerson = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Watch a followed player from their own eyes instead of behind them.\n"
                        + "Their hands, held items and swing animation are drawn for you."))
                .build())
            .build();

        OptionGroup performance = OptionGroup.createBuilder("Performance")
            .setExpanded(true)
            .addOption(NumericalOption.createBuilder("Frame-time budget %",
                    () -> secondViewBudgetPercent, 25, v -> secondViewBudgetPercent = v)
                .values(0, 75, 5)
                .description(OptionDescription.ofOrderedString(() ->
                    "If the second view costs more than this share of frame time it starts\n"
                        + "skipping frames to stay inside the budget. 0 turns the throttle off.\n"
                        + "This is the thing protecting your main FPS."))
                .build())
            .addOption(NumericalOption.createBuilder("Render distance (chunks)",
                    () -> secondViewRenderDistance, 0, v -> secondViewRenderDistance = v)
                .values(0, 32, 1)
                .description(OptionDescription.ofOrderedString(() ->
                    "Shorter far plane and fog for the second view only. 0 means the same as\n"
                        + "your main view, and it can only be shorter, never further.\n"
                        + "Big GPU saving; your main render distance is untouched."))
                .build())
            .addOption(NumericalOption.createBuilder("Resolution %",
                    () -> resolutionPercent, 100, v -> resolutionPercent = v)
                .values(10, 100, 5)
                .description(OptionDescription.ofOrderedString(() ->
                    "Render resolution of the second view's world, as a share of the main\n"
                        + "window's. Pure fill rate, so this is the cheapest thing to turn down\n"
                        + "when you need frames back."))
                .build())
            .build();

        OptionGroup behaviour = OptionGroup.createBuilder("Behaviour")
            .setExpanded(true)
            .addOption(BooleanOption.createBuilder("Interface on the second view",
                    () -> secondViewHud, true, v -> secondViewHud = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Lay the interface over the second view as well - hotbar, health, chat,\n"
                        + "the inventory and any other screen, plus a drawn-in mouse pointer\n"
                        + "while one is open.\n"
                        + "Turn this on if you are recording the second window: without it the\n"
                        + "footage is a bare world view that goes blank-looking whenever you\n"
                        + "open a menu. With it, the second window is a complete picture of\n"
                        + "the session from the over-the-shoulder camera.\n"
                        + "The interface is replayed, not drawn a second time, so nothing in\n"
                        + "it ticks twice and it costs almost nothing.\n"
                        + "The game draws no cursor of its own - the one over the main window\n"
                        + "belongs to the operating system - so Argus draws one to match."))
                .build())
            .addOption(BooleanOption.createBuilder("Screens on the second view",
                    () -> secondViewScreens, true, v -> secondViewScreens = v)
                .availability(() -> secondViewHud, "Turn Interface on the second view on first.")
                .description(OptionDescription.ofOrderedString(() ->
                    "Show the screens you open - the inventory, chests, chat, the pause\n"
                        + "menu - on the second view too, with the drawn mouse pointer.\n"
                        + "Off keeps the HUD there but leaves screens out, so a recording of the\n"
                        + "second window shows the game and not your menus.\n"
                        + "The HUD and an open screen are built as one picture, so with this off\n"
                        + "the HUD is built twice a frame while a screen is open: once for each\n"
                        + "window."))
                .build())
            .addOption(BooleanOption.createBuilder("Show status text",
                    () -> showStatusText, true, v -> showStatusText = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Draw the small label in the corner of the second window telling you what\n"
                        + "it is currently showing - who you are following, which perspective,\n"
                        + "whether the game is paused.\n"
                        + "Off gives you a clean picture. The window title still says the same\n"
                        + "thing, so you do not lose the information entirely."))
                .build())
            .addOption(BooleanOption.createBuilder("Show hitboxes",
                    () -> showHitboxes, true, v -> showHitboxes = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Draw entity hitboxes in the second window too, whenever you have them\n"
                        + "turned on with F3+B. This does not turn them on by itself.\n"
                        + "Worth having: the second window is where you can actually see your\n"
                        + "own box, which the main window cannot show you in first person.\n"
                        + "Off keeps the second window clean while the main one has them on."))
                .build())
            .addOption(BooleanOption.createBuilder("Hold last frame when you leave",
                    () -> freezeLastFrame, true, v -> freezeLastFrame = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Leaving a world normally blanks the second window to flat grey.\n"
                        + "With this on it keeps showing the last frame it drew, frozen,\n"
                        + "until you join a world again."))
                .build())
            .addOption(BooleanOption.createBuilder("Second window vsync",
                    () -> secondWindowVsync, false, v -> secondWindowVsync = v)
                .description(OptionDescription.ofOrderedString(() ->
                    "Sync the second window to your monitor instead of presenting as fast as\n"
                        + "it can. Removes tearing there completely.\n"
                        + "Costs main FPS: the swap happens on the render thread, so with this\n"
                        + "on that thread waits for the second window's vblank every frame.\n"
                        + "Off is the right default - the tearing usually shows only as a faint\n"
                        + "seam."))
                .build())
            .addOption(ButtonOption.createBuilder("Open key bindings", () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(new KeybindsScreen(mc.currentScreen, mc.options));
                })
                .description(OptionDescription.ofOrderedString(() ->
                    "Jump to the vanilla Controls screen. The Argus keys are grouped under\n"
                        + "their own \"Argus\" heading there."))
                .build())
            .build();

        Category category = Category.createBuilder("Argus")
            .group(camera)
            .group(performance)
            .group(behaviour)
            .build();

        return LocalConfig.createBuilder("Argus")
            .path(FabricLoader.getInstance().getConfigDir().resolve("argus.json"))
            .category(category)
            .build();
    }
}
