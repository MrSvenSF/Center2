package net.managerhub.center.paper.config;

import net.managerhub.center.common.remote.RemoteSettings;

/**
 * Validated content of {@code MainConfig.yml}.
 *
 * @param language              selected language code, for example {@code DE}
 * @param centerInfoMenuEnabled whether the Center-Info menu may be opened
 * @param remote                the optional remote database of the network
 */
public record MainSettings(String language, boolean centerInfoMenuEnabled, RemoteSettings remote) {
}
