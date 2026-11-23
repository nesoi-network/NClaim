package nesoi.aysihuniks.nclaim.utils;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** Sending messages to players and the console. Empty messages are silently skipped. */
public final class Msg {

    private Msg() {
    }

    public static void send(@Nullable CommandSender target, @Nullable String message) {
        if (target == null || message == null || message.isEmpty()) return;
        target.sendMessage(Text.component(message));
    }

    public static void sendWithPrefix(@Nullable CommandSender target, @Nullable String message) {
        if (message == null || message.isEmpty()) return;
        send(target, Text.prefix() + " " + message);
    }

    public static void actionBar(@Nullable Player player, @Nullable String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(Text.component(message));
    }

    /** {@code title} may contain a {@code \n} to separate title and subtitle. */
    public static void title(@Nullable Player player, @Nullable String message) {
        if (player == null || message == null || message.isEmpty()) return;
        String[] parts = message.split("\\\\n|\n", 2);
        player.showTitle(Title.title(
                Text.component(parts[0]),
                Text.component(parts.length > 1 ? parts[1] : ""),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }

    /**
     * Sends a message whose channel is chosen by an optional {@code CHAT=}, {@code ACTION_BAR=} or
     * {@code TITLE=} prefix (used by the enter/leave messages in the language files).
     */
    public static void sendSorted(@NotNull Player player, @Nullable String message) {
        if (message == null || message.isEmpty()) return;
        int idx = message.indexOf('=');
        if (idx > 0) {
            String channel = message.substring(0, idx);
            String body = message.substring(idx + 1);
            switch (channel) {
                case "CHAT" -> {
                    send(player, body);
                    return;
                }
                case "ACTION_BAR" -> {
                    actionBar(player, body);
                    return;
                }
                case "TITLE" -> {
                    title(player, body);
                    return;
                }
                default -> {
                }
            }
        }
        send(player, message);
    }

    public static void log(@NotNull String message) {
        Bukkit.getConsoleSender().sendMessage(Text.component(Text.prefix() + " " + message));
    }
}
