package nesoi.aysihuniks.nclaim.utils;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Feedback sound effects. Each effect is a short sequence of notes; the first note of most
 * effects can be changed in config.yml under {@code sounds.<key>} ({@code NONE} disables it).
 */
public final class MessageType {

    private record Note(@Nullable String configKey, @NotNull String sound, float volume, float pitch, long delay) {
    }

    private static Note cfg(String key, String sound, float volume, float pitch) {
        return new Note(key, sound, volume, pitch, 0);
    }

    private static Note fixed(String sound, float volume, float pitch, long delay) {
        return new Note(null, sound, volume, pitch, delay);
    }

    public static final MessageType CONFIRM = new MessageType(
            cfg("confirm", "BLOCK_NOTE_BLOCK_CHIME", 1f, 1f),
            new Note("confirm", "BLOCK_NOTE_BLOCK_CHIME", 1f, 2f, 2));
    public static final MessageType FAIL = new MessageType(
            cfg("fail", "ENTITY_ENDERMAN_TELEPORT", 0.5f, 0.5f),
            fixed("BLOCK_ANVIL_LAND", 0.3f, 0.5f, 2));
    public static final MessageType WARN = new MessageType(
            cfg("warn", "BLOCK_NOTE_BLOCK_BASS", 1f, 0.5f),
            new Note("warn", "BLOCK_NOTE_BLOCK_BASS", 1f, 0.6f, 2));
    public static final MessageType VALUE_INCREASE = new MessageType(
            cfg("value_increase", "UI_BUTTON_CLICK", 0.4f, 1.8f),
            fixed("BLOCK_NOTE_BLOCK_PLING", 0.3f, 1.6f, 0));
    public static final MessageType VALUE_DECREASE = new MessageType(
            cfg("value_decrease", "UI_BUTTON_CLICK", 0.4f, 1.4f),
            fixed("BLOCK_NOTE_BLOCK_PLING", 0.3f, 0.8f, 0));
    public static final MessageType MENU_REFRESH = new MessageType(
            cfg("menu_refresh", "BLOCK_PISTON_CONTRACT", 0.3f, 1.5f),
            fixed("BLOCK_METAL_PRESSURE_PLATE_CLICK_ON", 0.4f, 1.6f, 1),
            fixed("BLOCK_IRON_TRAPDOOR_CLOSE", 0.3f, 2.0f, 3));
    public static final MessageType SEARCH_OPEN = new MessageType(
            cfg("search_open", "UI_CARTOGRAPHY_TABLE_TAKE_RESULT", 0.6f, 1.2f),
            fixed("ITEM_BOOK_PAGE_TURN", 0.4f, 1.5f, 1),
            fixed("BLOCK_NOTE_BLOCK_HAT", 0.3f, 1.8f, 2));
    public static final MessageType MENU_BACK = new MessageType(
            cfg("menu_back", "ITEM_BUNDLE_DROP_CONTENTS", 0.5f, 1.2f),
            fixed("BLOCK_NOTE_BLOCK_HAT", 0.3f, 0.8f, 2));
    public static final MessageType MENU_FORWARD = new MessageType(
            cfg("menu_forward", "BLOCK_ENCHANTMENT_TABLE_USE", 0.5f, 1.5f),
            fixed("ENTITY_EXPERIENCE_ORB_PICKUP", 0.3f, 1.8f, 2));
    public static final MessageType MENU_SELECT = new MessageType(
            cfg("menu_click", "UI_BUTTON_CLICK", 0.7f, 1.2f),
            fixed("ENTITY_PLAYER_LEVELUP", 0.4f, 2.0f, 2),
            fixed("BLOCK_NOTE_BLOCK_BELL", 0.3f, 1.8f, 3));
    public static final MessageType MENU_DESELECT = new MessageType(
            cfg("menu_click", "UI_BUTTON_CLICK", 0.7f, 0.8f),
            fixed("BLOCK_WOODEN_BUTTON_CLICK_OFF", 0.4f, 0.7f, 1),
            fixed("BLOCK_NOTE_BLOCK_HAT", 0.3f, 0.6f, 2));
    public static final MessageType TELEPORT = new MessageType(
            cfg("teleport", "BLOCK_ENCHANTMENT_TABLE_USE", 0.7f, 1.0f),
            fixed("BLOCK_BEACON_AMBIENT", 0.4f, 2.0f, 0),
            fixed("ENTITY_EXPERIENCE_ORB_PICKUP", 0.5f, 1.8f, 2),
            fixed("BLOCK_AMETHYST_BLOCK_CHIME", 0.3f, 1.5f, 4));

    private final List<Note> notes;

    private MessageType(Note... notes) {
        this.notes = List.of(notes);
    }

    public void playSound(@Nullable Player player) {
        if (player == null) return;
        for (Note note : notes) {
            String sound = note.configKey() == null ? note.sound() : Sounds.Config.sound(note.configKey(), note.sound());
            if (note.delay() <= 0) {
                Sounds.play(player, sound, note.volume(), note.pitch());
            } else {
                Scheduler.entityLater(player, () -> Sounds.play(player, sound, note.volume(), note.pitch()), note.delay());
            }
        }
    }
}
