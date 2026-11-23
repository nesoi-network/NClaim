package nesoi.aysihuniks.nclaim.service;

import lombok.RequiredArgsConstructor;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.ClaimSettingChangeEvent;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.SettingCfg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import nesoi.aysihuniks.nclaim.utils.Msg;

@RequiredArgsConstructor
public class ClaimSettingsManager {
    private final NClaim plugin;

    public void toggleSetting(Claim claim, Player player, Setting setting) {

        SettingCfg settingCfg = plugin.getGuiLangManager().getSettingConfig(setting);
        if (settingCfg != null && !settingCfg.isChangeable()) {
            Msg.send(player, plugin.getLangManager().getString("command.change.setting_cannot_be_changed"));
            return;
        }
        if (settingCfg != null && settingCfg.getPermission() != null && !player.hasPermission(settingCfg.getPermission())) {
            Msg.send(player, plugin.getLangManager().getString("command.permission_denied"));
            return;
        }

        if (!isAuthorized(claim, player)) {
            Msg.send(player, plugin.getLangManager().getString("command.permission_denied"));
            return;
        }

        boolean newState = !claim.getSettings().isEnabled(setting);

        ClaimSettingChangeEvent changeEvent = new ClaimSettingChangeEvent(claim, claim.getSettings(), player, newState);
        Bukkit.getPluginManager().callEvent(changeEvent);

        if (changeEvent.isCancelled()) {
            Msg.send(player, plugin.getLangManager().getString("claim.setting_change_cancelled"));
            return;
        }

        claim.getSettings().set(setting, newState);

        claim.markDirty();
    }

    public boolean isSettingEnabled(Claim claim, Setting setting) {
        return claim.getSettings().isEnabled(setting);
    }

    private boolean isAuthorized(Claim claim, Player player) {
        return claim.getOwner().equals(player.getUniqueId()) || 
               player.hasPermission("nclaim.admin");
    }

    public static void resetToDefaults(Claim claim) {
        for (Setting setting : Setting.values()) {
            SettingCfg cfg = NClaim.inst().getGuiLangManager().getSettingConfig(setting);
            if (cfg != null) {
                claim.getSettings().set(setting, cfg.isDefaultValue());
            }
        }
        claim.markDirty();
    }
}
