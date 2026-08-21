package net.managerhub.center.velocity.config;

import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.remote.RemoteSettings;

/**
 * One complete configuration snapshot of MHCenter2 on the proxy.
 *
 * <p>The proxy has no menu and no {@code Commands.yml}, so a snapshot is only the
 * texts and the remote section of {@code MainConfig.yml}. It exists for the same
 * reason as the Paper snapshot: a reload either produces a complete, valid
 * configuration or it changes nothing at all.</p>
 *
 * @param language validated texts of the selected language
 * @param remote   the optional remote database of the network
 */
public record ProxyConfiguration(Language language, RemoteSettings remote) {
}
