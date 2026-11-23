package nesoi.aysihuniks.nclaim.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Checks SpigotMC once at startup; admins are told on join when a newer version exists. */
public class UpdateChecker {

    private static volatile String latestVersion;

    private final Plugin plugin;
    private final int resourceId;

    public UpdateChecker(Plugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void check() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null || response.statusCode() != 200) {
                plugin.getLogger().info("Could not check for updates.");
                return;
            }
            String latest = response.body().trim();
            if (latest.isEmpty() || latest.length() > 32) return;
            latestVersion = latest;
            if (isNewer()) {
                Msg.log("&eA new version is available: &fv" + latest + " &7(current: v" + current() + ")");
            }
        });
    }

    private String current() {
        return plugin.getPluginMeta().getVersion();
    }

    private boolean isNewer() {
        return isNewer(latestVersion, current());
    }

    /**
     * Numeric, part-by-part comparison: "3.10.0" > "3.9.1", and an older remote is never "new".
     * A release is newer than its own pre-releases: "4.1.0" > "4.1.0-beta.2".
     */
    static boolean isNewer(@Nullable String remote, String local) {
        if (remote == null) return false;
        String remoteCore = remote.replaceFirst("^[vV]", "");
        String localCore = local.replaceFirst("^[vV]", "");
        boolean remotePre = remoteCore.contains("-"), localPre = localCore.contains("-");
        String[] r = remoteCore.split("-", 2)[0].split("\\.");
        String[] l = localCore.split("-", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int a = i < r.length ? leadingInt(r[i]) : 0;
            int b = i < l.length ? leadingInt(l[i]) : 0;
            if (a != b) return a > b;
        }
        return localPre && !remotePre;
    }

    private static int leadingInt(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c) || n > 100_000) break;
            n = n * 10 + (c - '0');
        }
        return n;
    }

    @Nullable
    public static String getLatestVersion() {
        return latestVersion;
    }

    public static void notifyIfOutdated(Plugin plugin, Player player) {
        String latest = latestVersion;
        String current = plugin.getPluginMeta().getVersion();
        if (!isNewer(latest, current)) return;
        Msg.sendWithPrefix(player, "&aA new version is available: &ev" + latest + " &8(&7You are using: &ev" + current + "&8)");
        MessageType.CONFIRM.playSound(player);
    }
}
