package nesoi.aysihuniks.nclaim.utils;

import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Player-facing number formatting (config.yml number_format), at most two decimals.
 * compact: {@code 950, 1.5k, 1.25M}; full: {@code 950, 1,500, 1,250,000}.
 */
public final class Numbers {

    private static final ThreadLocal<DecimalFormat> FULL = ThreadLocal.withInitial(() -> decimal("#,##0.##"));
    private static final ThreadLocal<DecimalFormat> SHORT = ThreadLocal.withInitial(() -> decimal("0.##"));

    private static volatile boolean compact = true;
    private static volatile List<String> suffixes = List.of("k", "M", "B", "T");

    private Numbers() {
    }

    public static void configure(boolean compactStyle, @NotNull List<String> suffixList) {
        compact = compactStyle;
        if (!suffixList.isEmpty()) suffixes = List.copyOf(suffixList);
    }

    /** Formats with the configured style. */
    public static @NotNull String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return compact ? compact(value) : full(value);
    }

    /** Always the full form, e.g. for chat confirmations where the exact amount matters. */
    public static @NotNull String full(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return FULL.get().format(value);
    }

    private static String compact(double value) {
        double abs = Math.abs(value);
        if (Math.round(abs * 100) < 100_000) return FULL.get().format(value); // below 999.995
        int tier = -1;
        double scaled = abs;
        while (scaled >= 1000 && tier < suffixes.size() - 1) {
            scaled /= 1000;
            tier++;
        }
        // 999.995k rounds to "1000k": move up one tier instead when possible.
        if (Math.round(scaled * 100) >= 100_000 && tier < suffixes.size() - 1) {
            scaled /= 1000;
            tier++;
        }
        String number = tier == suffixes.size() - 1 && scaled >= 1000
                ? FULL.get().format(scaled)
                : SHORT.get().format(scaled);
        return (value < 0 ? "-" : "") + number + suffixes.get(tier);
    }

    private static DecimalFormat decimal(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }
}
