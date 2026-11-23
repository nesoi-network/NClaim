package nesoi.aysihuniks.nclaim.enums;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum Setting {
    CLAIM_PVP,
    TNT_DAMAGE,
    CREEPER_DAMAGE,
    MOB_ATTACKING,
    MONSTER_SPAWNING,
    ANIMAL_SPAWNING,
    VILLAGER_INTERACTION,
    /** Fire spreading to and burning blocks of the claim. */
    FIRE_SPREAD,
    /** Hostile mobs changing blocks: endermen, ravagers, withers, zombies breaking doors ... */
    MOB_GRIEFING,
    /** Farmland turning back into dirt when jumped on. */
    CROP_TRAMPLING,
    /** Explosions other than TNT, creepers and end crystals: withers, ghast fireballs, beds, respawn anchors. */
    OTHER_EXPLOSIONS;

    /** Lenient lookup used when reading stored data ({@code claim-pvp}, {@code claim_pvp} ...). */
    @Nullable
    public static Setting byName(@Nullable String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
