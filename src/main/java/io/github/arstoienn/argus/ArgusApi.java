package io.github.arstoienn.argus;

import main.walksy.lib.api.WalksyLibApi;
import main.walksy.lib.core.config.impl.LocalConfig;

/** WalksyLib's entry point, which puts the settings screen in Mod Menu. */
public final class ArgusApi implements WalksyLibApi {
    @Override
    public LocalConfig getConfig() {
        return ArgusConfig.get().config();
    }
}
