package net.managerhub.center.paper.menu;

import java.util.logging.Level;

import net.managerhub.center.Center;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;

/**
 * Builds the fixed player head of the creator {@value net.managerhub.center.Center#CREATOR}.
 *
 * <p>The head is always created from a profile that only carries the creator
 * name, which never blocks the server thread. In addition the profile is
 * completed once in the background through the profile API of the server, so
 * later menu openings can use the cached skin texture. MHCenter2 never talks to an
 * external service itself.</p>
 *
 * <p>If the texture is not available the menu still opens with a normal player
 * head.</p>
 */
public final class CreatorHead {

    private volatile PlayerProfile completedProfile;

    /**
     * Starts the asynchronous profile lookup of the server.
     *
     * @param plugin plugin used for logging
     */
    public void warmUp(final Plugin plugin) {
        final PlayerProfile profile;
        try {
            profile = Bukkit.createPlayerProfile(Center.CREATOR);
        } catch (final IllegalArgumentException failure) {
            plugin.getLogger().log(Level.FINE, "The creator profile could not be prepared.", failure);
            return;
        }
        profile.update().whenComplete((updated, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.FINE,
                        "The creator skin could not be loaded, a plain player head is used instead.", failure);
                return;
            }
            this.completedProfile = updated;
        });
    }

    /** @return the creator head, with the cached skin texture when it is available. */
    public ItemStack createHead() {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final PlayerProfile profile = profile();
        if (profile != null) {
            head.editMeta(SkullMeta.class, meta -> meta.setOwnerProfile(profile));
        }
        return head;
    }

    private PlayerProfile profile() {
        final PlayerProfile cached = this.completedProfile;
        if (cached != null) {
            return cached;
        }
        try {
            return Bukkit.createPlayerProfile(Center.CREATOR);
        } catch (final IllegalArgumentException failure) {
            return null;
        }
    }
}
