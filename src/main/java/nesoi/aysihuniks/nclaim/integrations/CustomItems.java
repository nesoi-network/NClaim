package nesoi.aysihuniks.nclaim.integrations;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Items from custom item plugins, looked up by id. Called through reflection so none of these
 * plugins is needed to build or run NClaim.
 * <ul>
 *   <li>{@code itemsadder:namespace:id}</li>
 *   <li>{@code nexo:id}</li>
 *   <li>{@code oraxen:id}</li>
 * </ul>
 */
public final class CustomItems {

    private CustomItems() {
    }

    /** True if the spec starts with a supported plugin prefix. */
    public static boolean handles(String spec) {
        String lower = spec.toLowerCase(Locale.ROOT);
        return lower.startsWith("itemsadder:") || lower.startsWith("nexo:") || lower.startsWith("oraxen:");
    }

    /** The item, or null if the plugin is missing or the id is unknown. */
    @Nullable
    public static ItemStack get(String spec) {
        int colon = spec.indexOf(':');
        String source = spec.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = spec.substring(colon + 1);
        try {
            return switch (source) {
                case "itemsadder" -> {
                    if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) yield null;
                    Class<?> stacks = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Object stack = stacks.getMethod("getInstance", String.class).invoke(null, id);
                    yield stack == null ? null : (ItemStack) stacks.getMethod("getItemStack").invoke(stack);
                }
                case "nexo" -> {
                    if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) yield null;
                    Object builder = Class.forName("com.nexomc.nexo.api.NexoItems")
                            .getMethod("itemFromId", String.class).invoke(null, id);
                    yield build(builder);
                }
                case "oraxen" -> {
                    if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen")) yield null;
                    Object builder = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
                            .getMethod("getItemById", String.class).invoke(null, id);
                    yield build(builder);
                }
                default -> null;
            };
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return null;
        }
    }

    @Nullable
    private static ItemStack build(@Nullable Object builder) throws ReflectiveOperationException {
        if (builder == null) return null;
        Method build = builder.getClass().getMethod("build");
        return ((ItemStack) build.invoke(builder)).clone();
    }
}
