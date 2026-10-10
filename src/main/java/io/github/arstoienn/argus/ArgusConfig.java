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
    public boolean mirrorPerspective = true;
    public boolean followProjectiles = true;
    public boolean followFirstPerson = false;
    public boolean secondViewHud = true;
    public boolean secondViewScreens = true;
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
            .addOption(toggle("Flip to first person", () -> mirrorPerspective, true, v -> mirrorPerspective = v,
                "When the main window is in third person, show first person here."))
            .addOption(toggle("Follow projectiles", () -> followProjectiles, true, v -> followProjectiles = v,
                "Follow your arrows, tridents and ender pearls while they fly."))
            .addOption(toggle("Follow from their eyes", () -> followFirstPerson, false, v -> followFirstPerson = v,
                "Watch a followed player in first person instead of from behind."))
            .addOption(toggle("HUD", () -> secondViewHud, true, v -> secondViewHud = v,
                "Show the hotbar, health and chat here."))
            .addOption(BooleanOption.createBuilder("Menus", () -> secondViewScreens, true, v -> secondViewScreens = v)
                .availability(() -> secondViewHud, "Turn HUD on first.")
                .description(text("Also show menus you open, like the inventory, with the pointer."))
                .build())
            .addOption(toggle("Hitboxes", () -> showHitboxes, true, v -> showHitboxes = v,
                "Show F3+B hitboxes here too."))
            .addOption(toggle("Status text", () -> showStatusText, true, v -> showStatusText = v,
                "A small label saying what this window is showing."))
            .addOption(toggle("Keep last frame", () -> freezeLastFrame, true, v -> freezeLastFrame = v,
                "Keep the last picture after you leave a world."))
            .build();

        OptionGroup camera = OptionGroup.createBuilder("Camera")
            .setExpanded(true)
            .addOption(NumericalOption.createBuilder("Distance", () -> followDistance, 4.0, v -> followDistance = v)
                .values(1.0, 12.0, 0.5)
                .description(text("How far behind the camera sits, in blocks."))
                .build())
            .addOption(NumericalOption.createBuilder("Shoulder offset", () -> shoulderSide, 0.5, v -> shoulderSide = v)
                .values(-2.0, 2.0, 0.1)
                .description(text("Sideways, in blocks. Negative is the left shoulder."))
                .build())
            .addOption(NumericalOption.createBuilder("Height offset", () -> shoulderUp, 0.3, v -> shoulderUp = v)
                .values(-1.0, 1.5, 0.1)
                .description(text("Up or down from the eyes, in blocks."))
                .build())
            .addOption(NumericalOption.createBuilder("Smoothing %", () -> cameraSmoothingPercent, 50, v -> cameraSmoothingPercent = v)
                .values(0, 100, 5)
                .description(text("How gently the camera moves into place. 0 is off."))
                .build())
            .build();

        OptionGroup performance = OptionGroup.createBuilder("Performance")
            .setExpanded(true)
            .addOption(NumericalOption.createBuilder("Frame budget %", () -> secondViewBudgetPercent, 25, v -> secondViewBudgetPercent = v)
                .values(0, 75, 5)
                .description(text("Most of each frame this window may take. 0 is no limit."))
                .build())
            .addOption(NumericalOption.createBuilder("Render distance", () -> secondViewRenderDistance, 0, v -> secondViewRenderDistance = v)
                .values(0, 32, 1)
                .description(text("In chunks. 0 matches the main window; it cannot go further."))
                .build())
            .addOption(NumericalOption.createBuilder("Resolution %", () -> resolutionPercent, 100, v -> resolutionPercent = v)
                .values(10, 100, 5)
                .description(text("Of the main window's resolution."))
                .build())
            .addOption(toggle("Vsync", () -> secondWindowVsync, false, v -> secondWindowVsync = v,
                "Stops tearing here, but costs main FPS."))
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
