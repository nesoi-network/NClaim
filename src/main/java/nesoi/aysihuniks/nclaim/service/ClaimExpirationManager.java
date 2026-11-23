package nesoi.aysihuniks.nclaim.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import nesoi.aysihuniks.nclaim.Config;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.RemoveCause;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.TimeLeftThreshold;
import nesoi.aysihuniks.nclaim.utils.LangManager;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Removes expired claims and formats the remaining time. */
public class ClaimExpirationManager {

    private static final long CHECK_INTERVAL_SECONDS = 60;

    private final NClaim plugin;
    private ScheduledTask task;

    public ClaimExpirationManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    public void startExpirationChecker() {
        stopExpirationChecker();
        // The scan itself touches no world state; each removal is dispatched to its own region.
        task = Scheduler.asyncTimer(this::checkExpiredClaims, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopExpirationChecker() {
        Scheduler.cancel(task);
        task = null;
    }

    public void checkExpiredClaims() {
        List<Claim> expired = new ArrayList<>();
        for (Claim claim : Claim.claims) {
            if (claim.isExpired()) expired.add(claim);
        }
        expired.forEach(claim -> plugin.getClaimService().removeClaim(claim, RemoveCause.REMOVED));
    }

    public void extendClaimExpiration(@NotNull Claim claim, int days, int hours, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(claim.getExpiredAt());
        calendar.add(Calendar.DAY_OF_MONTH, days);
        calendar.add(Calendar.HOUR_OF_DAY, hours);
        calendar.add(Calendar.MINUTE, minutes);
        claim.setExpiredAt(calendar.getTime());
    }

    /** Remaining time as e.g. "3d, 4h", coloured according to the configured thresholds. */
    public String getFormattedTimeLeft(@NotNull Claim claim) {
        long millis = claim.getExpiredAt().getTime() - System.currentTimeMillis();
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        LangManager lang = plugin.getLangManager();
        String d = lang.getString("hologram.time_left.d");
        String h = lang.getString("hologram.time_left.h");
        String m = lang.getString("hologram.time_left.m");
        String s = lang.getString("hologram.time_left.s");

        String timeLeft;
        if (days > 0) timeLeft = days + d + ", " + hours + h;
        else if (hours > 0) timeLeft = hours + h + ", " + minutes + m;
        else if (minutes > 0) timeLeft = minutes + m + ", " + secs + s;
        else timeLeft = secs + s;

        return getTimeLeftColor(seconds) + timeLeft;
    }

    public String getTimeLeftColor(long secondsLeft) {
        for (TimeLeftThreshold t : plugin.getNconfig().getTimeLeftThresholds()) {
            long value = Config.getTimeUnitValue(secondsLeft, t.getUnit());
            boolean match = switch (t.getOperator()) {
                case ">=" -> value >= t.getValue();
                case "<=" -> value <= t.getValue();
                case "<" -> value < t.getValue();
                case ">" -> value > t.getValue();
                case "==" -> value == t.getValue();
                default -> false;
            };
            if (match) return t.getColor();
        }
        return "&7";
    }

    public boolean isExpired(@NotNull Claim claim) {
        return claim.isExpired();
    }

    public boolean isExpiringSoon(@NotNull Claim claim, int warningHours) {
        return claim.getExpiredAt().getTime() - System.currentTimeMillis() <= TimeUnit.HOURS.toMillis(warningHours);
    }
}
