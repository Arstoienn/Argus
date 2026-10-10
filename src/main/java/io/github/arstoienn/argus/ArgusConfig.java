package io.github.arstoienn.argus;

import java.util.function.Consumer;
import java.util.function.Supplier;
import main.walksy.lib.api.WalksyLibConfig;
import main.walksy.lib.core.config.impl.LocalConfig;
import main.walksy.lib.core.config.local.Category;
import main.walksy.lib.core.config.local.Option;
import main.walksy.lib.core.config.local.OptionDescription;
import main.walksy.lib.core.config.local.options.BooleanOption;
import main.walksy.lib.core.config.local.options.NumericalOption;
import main.walksy.lib.core.config.local.options.groups.OptionGroup;
import net.fabricmc.loader.api.FabricLoader;

/** The settings, with WalksyLib's screen for them, stored in {@code config/argus.json}. */
public final class ArgusConfig implements WalksyLibConfig {
    private static final ArgusConfig INSTANCE = new ArgusConfig();

    // View
    public boolean mirrorPerspective = false;
    public boolean followProjectiles = true;
    public boolean followThirdPerson = true;
    public boolean secondViewHud = false;
    public boolean secondViewScreens = false;
    public boolean showHitboxes = true;
    public boolean showStatusText = true;
    public boolean freezeLastFrame = true;

    // Camera
    public double followDistance = 4.0;
    public double shoulderSide = 0.5;
    public double shoulderUp = 0.3;
    public int cameraSmoothingPercent = 50;

    // Performance
    public int secondViewBudgetPercent = 25;
    public int secondViewRenderDistance = 0;
    public int resolutionPercent = 100;
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

    /** Smoothing strength from 0 (none) to 1. */
    double cameraSmoothing() {
        return cameraSmoothingPercent / 100.0;
    }

    @Override
    public LocalConfig define() {
        OptionGroup view = OptionGroup.createBuilder("View")
            .setExpanded(true)
            .addOption(toggle("Complementary view", () -> mirrorPerspective, false, v -> mirrorPerspective = v,
                "Displays a first-person perspective in the second window while the main window is in third person."))
            .addOption(toggle("Follow projectiles", () -> followProjectiles, true, v -> followProjectiles = v,
                "Follows arrows, tridents and ender pearls launched by the player. Snowballs, eggs and potions are not followed."))
            .addOption(toggle("Third-person follow", () -> followThirdPerson, true, v -> followThirdPerson = v,
                "Views a followed player from behind. When disabled, the view is taken from the player's eyes."))
            .addOption(toggle("HUD", () -> secondViewHud, false, v -> secondViewHud = v,
                "Displays the heads-up display, including the hotbar, health and chat, in the second window."))
            .addOption(BooleanOption.createBuilder("Menus", () -> secondViewScreens, false, v -> secondViewScreens = v)
                .availability(() -> secondViewHud, "Requires HUD to be enabled.")
                .description(text("Displays open menus, such as the inventory, in the second window, together with the mouse pointer."))
                .build())
            .addOption(toggle("Hitboxes", () -> showHitboxes, true, v -> showHitboxes = v,
                "Displays entity hitboxes in the second window whenever they are enabled with F3+B."))
            .addOption(toggle("Status text", () -> showStatusText, true, v -> showStatusText = v,
                "Displays a label in the second window indicating what it is currently showing."))
            .addOption(toggle("Keep last frame", () -> freezeLastFrame, true, v -> freezeLastFrame = v,
                "Retains the last rendered frame in the second window after leaving a world."))
            .build();

        OptionGroup camera = OptionGroup.createBuilder("Camera")
            .setExpanded(true)
            .addOption(NumericalOption.createBuilder("Distance", () -> followDistance, 4.0, v -> followDistance = v)
                .values(1.0, 12.0, 0.5)
                .description(text("Distance between the camera and its target, in blocks."))
                .build())
            .addOption(NumericalOption.createBuilder("Shoulder offset", () -> shoulderSide, 0.5, v -> shoulderSide = v)
                .values(-2.0, 2.0, 0.1)
                .description(text("Horizontal offset of the camera, in blocks. Negative values position it over the left shoulder."))
                .build())
            .addOption(NumericalOption.createBuilder("Height offset", () -> shoulderUp, 0.3, v -> shoulderUp = v)
                .values(-1.0, 1.5, 0.1)
                .description(text("Vertical offset of the camera relative to eye level, in blocks."))
                .build())
            .addOption(NumericalOption.createBuilder("Smoothing %", () -> cameraSmoothingPercent, 50, v -> cameraSmoothingPercent = v)
                .values(0, 100, 5)
                .description(text("Degree to which camera movement is smoothed. A value of 0 disables smoothing."))
                .build())
            .build();

        OptionGroup performance = OptionGroup.createBuilder("Performance")
            .setExpanded(true)
            .addOption(NumericalOption.createBuilder("Frame budget %", () -> secondViewBudgetPercent, 25, v -> secondViewBudgetPercent = v)
                .values(0, 75, 5)
                .description(text("Maximum proportion of frame time allocated to the second window. A value of 0 removes the limit."))
                .build())
            .addOption(NumericalOption.createBuilder("Render distance", () -> secondViewRenderDistance, 0, v -> secondViewRenderDistance = v)
                .values(0, 32, 1)
                .description(text("Measured in chunks. A value of 0 matches the main window, which cannot be exceeded."))
                .build())
            .addOption(NumericalOption.createBuilder("Resolution %", () -> resolutionPercent, 100, v -> resolutionPercent = v)
                .values(10, 100, 5)
                .description(text("Rendering resolution of the second window, as a percentage of that of the main window."))
                .build())
            .addOption(toggle("Vsync", () -> secondWindowVsync, false, v -> secondWindowVsync = v,
                "Synchronises the second window with the display to prevent tearing. Reduces the main frame rate."))
            .build();

        Category category = Category.createBuilder("Argus")
            .group(view)
            .group(camera)
            .group(performance)
            .build();

        return LocalConfig.createBuilder("Argus")
            .path(FabricLoader.getInstance().getConfigDir().resolve("argus.json"))
            .category(category)
            .build();
    }

    private static Option<Boolean> toggle(String name, Supplier<Boolean> get, boolean fallback, Consumer<Boolean> set, String description) {
        return BooleanOption.createBuilder(name, get, fallback, set).description(text(description)).build();
    }

    private static OptionDescription text(String text) {
        return OptionDescription.ofOrderedString(() -> text);
    }
}
