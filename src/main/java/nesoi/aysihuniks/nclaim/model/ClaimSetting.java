package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.enums.Setting;

import java.util.EnumMap;
import java.util.Map;

public class ClaimSetting {

    // Every key is present from the start, so concurrent reads never see a structural change.
    private final Map<Setting, Boolean> settings = new EnumMap<>(Setting.class);

    public ClaimSetting() {
        settings.put(Setting.CLAIM_PVP, false);
        settings.put(Setting.TNT_DAMAGE, true);
        settings.put(Setting.CREEPER_DAMAGE, true);
        settings.put(Setting.MOB_ATTACKING, false);
        settings.put(Setting.MONSTER_SPAWNING, true);
        settings.put(Setting.ANIMAL_SPAWNING, true);
        settings.put(Setting.VILLAGER_INTERACTION, false);
        settings.put(Setting.FIRE_SPREAD, false);
        settings.put(Setting.MOB_GRIEFING, false);
        settings.put(Setting.CROP_TRAMPLING, false);
        settings.put(Setting.OTHER_EXPLOSIONS, false);
    }

    public boolean isEnabled(Setting setting) {
        return Boolean.TRUE.equals(settings.get(setting));
    }

    public void set(Setting setting, boolean value) {
        settings.put(setting, value);
    }
}
