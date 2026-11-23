package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Loads player data before login (off the main thread) and saves it when the player leaves. */
public class UserManager implements Listener {

    private final NClaim plugin;

    public UserManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        if (User.getUser(uuid) == null) User.put(plugin.getStorage().loadUser(uuid));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (User.getUser(uuid) == null) {
            // e.g. /reload while players were online; load in the background.
            Scheduler.async(() -> User.put(plugin.getStorage().loadUser(uuid)));
        }
        Scheduler.async(() -> plugin.getHeadManager().preloadTexturesAsync(java.util.List.of(uuid)));
        if (player.hasPermission("nclaim.admin")) {
            Scheduler.entityLater(player, () -> UpdateChecker.notifyIfOutdated(plugin, player), 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        User user = User.unload(event.getPlayer().getUniqueId());
        if (user != null) plugin.getStorage().saveUserAsync(user);
    }
}
