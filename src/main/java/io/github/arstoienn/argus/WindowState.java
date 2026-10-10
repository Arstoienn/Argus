package io.github.arstoienn.argus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Where the second window was and how big, persisted to {@code config/argus-window.json}.
 *
 * Kept apart from {@link ArgusConfig} because it is runtime state, not a setting: it is rewritten
 * whenever the window moves, which would otherwise keep rewriting the settings file in the
 * background. Deleting the file puts the window back at its default place and size.
 */
public final class WindowState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_WIDTH = 854;
    private static final int DEFAULT_HEIGHT = 480;

    static WindowState current = new WindowState();

    // Field names are the JSON keys.
    int windowWidth = DEFAULT_WIDTH;
    int windowHeight = DEFAULT_HEIGHT;
    /** -1 leaves the position to the operating system. */
    int windowX = -1;
    int windowY = -1;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("argus-window.json");
    }

    static void load() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                WindowState loaded = GSON.fromJson(Files.readString(path), WindowState.class);
                if (loaded != null) {
                    current = loaded;
                }
            }
        } catch (Exception e) {
            ArgusClient.LOGGER.error("[argus] could not read the window state, using defaults", e);
        }
        if (current.windowWidth < 1 || current.windowHeight < 1) {
            current.windowWidth = DEFAULT_WIDTH;
            current.windowHeight = DEFAULT_HEIGHT;
        }
    }

    static void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(current));
        } catch (IOException e) {
            ArgusClient.LOGGER.error("[argus] could not write the window state", e);
        }
    }
}
