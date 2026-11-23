package nesoi.aysihuniks.nclaim.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour handling: {@code &a}, {@code <#RRGGBB>} / {@code &#RRGGBB} and palette placeholders
 * ({@code {ORANGE}}, {@code {prefix}} ...). Strings stay raw until they reach an output.
 */
public final class Text {

    private static final Map<String, String> PLACEHOLDERS = new LinkedHashMap<>();
    private static final Pattern ANGLE_HEX = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    static {
        PLACEHOLDERS.put("{WHITE}", "<#FFF8E8>");
        PLACEHOLDERS.put("{DARKGREEN}", "<#0A6847>");
        PLACEHOLDERS.put("{GREEN}", "<#7ABA78>");
        PLACEHOLDERS.put("{DARKRED}", "<#6D2323>");
        PLACEHOLDERS.put("{RED}", "<#cf2525>");
        PLACEHOLDERS.put("{YELLOW}", "<#FFEC9E>");
        PLACEHOLDERS.put("{ORANGE}", "<#fa8443>");
        PLACEHOLDERS.put("{GRAY}", "<#ababab>");
        PLACEHOLDERS.put("{BROWN}", "<#825B32>");
        PLACEHOLDERS.put("{PURPLE}", "<#8D77AB>");
        PLACEHOLDERS.put("{prefix}", "&8[<#fa8443>NClaim&8]&r");
    }

    private Text() {
    }

    public static void setPrefix(@NotNull String prefix) {
        PLACEHOLDERS.put("{prefix}", prefix);
    }

    @NotNull
    public static String prefix() {
        return PLACEHOLDERS.get("{prefix}");
    }

    /** Expands palette placeholders and normalises hex codes to {@code &#RRGGBB}. */
    @NotNull
    private static String normalise(@NotNull String input) {
        String out = input;
        if (out.indexOf('{') >= 0) {
            for (Map.Entry<String, String> e : PLACEHOLDERS.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
        }
        if (out.indexOf('<') >= 0) {
            Matcher m = ANGLE_HEX.matcher(out);
            out = m.replaceAll("&#$1");
        }
        // Strings that were already converted to section codes are accepted too.
        return out.replace(LegacyComponentSerializer.SECTION_CHAR, '&');
    }

    @NotNull
    public static Component component(@Nullable String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return AMPERSAND.deserialize(normalise(input));
    }

    /** Component for item names / lore: italic is disabled unless the text sets it explicitly. */
    @NotNull
    public static Component item(@Nullable String input) {
        return component(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    @NotNull
    public static List<Component> itemLore(@Nullable Collection<String> lines) {
        List<Component> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            // A single entry may contain several lines (e.g. after placeholder replacement).
            for (String part : (line == null ? "" : line).split("\n", -1)) {
                out.add(item(part));
            }
        }
        return out;
    }

    /** Legacy section-coded string, for APIs that still want a String (e.g. PlaceholderAPI results). */
    @NotNull
    public static String legacy(@Nullable String input) {
        return SECTION.serialize(component(input));
    }

    @NotNull
    public static String plain(@Nullable String input) {
        return PlainTextComponentSerializer.plainText().serialize(component(input));
    }
}
