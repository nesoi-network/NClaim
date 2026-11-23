package nesoi.aysihuniks.nclaim.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nesoi.aysihuniks.nclaim.Config;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** Discord webhook notifications. Requests are sent asynchronously and never block a tick. */
public final class WebhookService {

    private final NClaim plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebhookService(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    public void claimRemoved(@NotNull Claim claim) {
        Config config = plugin.getNconfig();
        String url = config.getWebhookUrl();
        if (!config.isWebhookEnabled() || url == null || url.isBlank() || url.contains("WEBHOOK_ID")) return;

        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        String player = ownerName != null ? ownerName : "Unknown";
        int y = claim.getClaimBlockLocation().getBlockY();

        JsonObject payload = new JsonObject();
        String mention = config.getWebhookMention() == null ? "" : config.getWebhookMention().trim();
        if (config.isWebhookUseEmbed()) {
            if (!mention.isEmpty()) payload.addProperty("content", mention);
            JsonObject embed = new JsonObject();
            putIfNotEmpty(embed, "title", config.getWebhookEmbedTitle());
            embed.addProperty("description", fill(config.getWebhookEmbedDescription(), player, claim, y));
            embed.addProperty("color", parseColor(config.getWebhookEmbedColor()));
            if (notEmpty(config.getWebhookEmbedFooter())) {
                JsonObject footer = new JsonObject();
                footer.addProperty("text", config.getWebhookEmbedFooter());
                embed.add("footer", footer);
            }
            if (config.isWebhookEmbedTimestamp()) embed.addProperty("timestamp", Instant.now().toString());
            if (notEmpty(config.getWebhookEmbedImage())) embed.add("image", urlObject(config.getWebhookEmbedImage()));
            if (notEmpty(config.getWebhookEmbedThumbnail())) embed.add("thumbnail", urlObject(config.getWebhookEmbedThumbnail()));
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
        } else {
            String content = fill(config.getWebhookContent(), player, claim, y);
            payload.addProperty("content", mention.isEmpty() ? content : mention + " " + content);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            Msg.log("&cInvalid webhook URL: " + e.getMessage());
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + error.getMessage());
            } else if (response.statusCode() >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + response.statusCode());
            }
        });
    }

    private static String fill(String template, String player, Claim claim, int y) {
        if (template == null) return "";
        return template.replace("%player%", player)
                .replace("%world%", claim.getWorldName())
                .replace("%x%", String.valueOf(claim.getCenterX()))
                .replace("%y%", String.valueOf(y))
                .replace("%z%", String.valueOf(claim.getCenterZ()))
                .replace("%claim%", claim.getDisplayName());
    }

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex == null ? "FF0000" : hex.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return 0xFF0000;
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    private static void putIfNotEmpty(JsonObject object, String key, String value) {
        if (notEmpty(value)) object.addProperty(key, value);
    }

    private static JsonObject urlObject(String url) {
        JsonObject o = new JsonObject();
        o.addProperty("url", url);
        return o;
    }
}
