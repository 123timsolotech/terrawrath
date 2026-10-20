package com.terrawrath.disaster;

import java.util.Locale;
import net.minecraft.world.BossEvent;

/**
 * The nine TerraWrath disasters. Timings are in ticks for intensity 1.0.
 */
public enum DisasterType {
    TORNADO("tornado", 0xC9B79C, BossEvent.BossBarColor.WHITE, 220, 1500, 160),
    VOLCANO("volcano", 0xFF5A1F, BossEvent.BossBarColor.RED, 240, 1800, 200),
    TSUNAMI("tsunami", 0x3FA9E0, BossEvent.BossBarColor.BLUE, 260, 1100, 160),
    EARTHQUAKE("earthquake", 0xE0763A, BossEvent.BossBarColor.YELLOW, 200, 900, 160),
    METEOR_SHOWER("meteor", 0x6CF0C2, BossEvent.BossBarColor.GREEN, 220, 1100, 160),
    SUPERSTORM("storm", 0x9B6BFF, BossEvent.BossBarColor.PURPLE, 220, 1300, 180),
    BLIZZARD("blizzard", 0xBFEFFF, BossEvent.BossBarColor.WHITE, 220, 1300, 200),
    SINKHOLE("sinkhole", 0xB08A5A, BossEvent.BossBarColor.YELLOW, 180, 640, 140),
    EVENT_HORIZON("horizon", 0xB15CFF, BossEvent.BossBarColor.PURPLE, 260, 1300, 120);

    public final String id;
    public final int color;
    public final BossEvent.BossBarColor barColor;
    public final int warnTicks;
    public final int activeTicks;
    public final int endTicks;

    DisasterType(String id, int color, BossEvent.BossBarColor barColor, int warnTicks, int activeTicks, int endTicks) {
        this.id = id;
        this.color = color;
        this.barColor = barColor;
        this.warnTicks = warnTicks;
        this.activeTicks = activeTicks;
        this.endTicks = endTicks;
    }

    public String nameKey() {
        return "disaster.terrawrath." + id;
    }

    public String descKey() {
        return "disaster.terrawrath." + id + ".desc";
    }

    /** The big EMERGENCY ALERT SYSTEM banner title. */
    public String alertKey() {
        return "disaster.terrawrath." + id + ".alert";
    }

    public String warnKey() {
        return "disaster.terrawrath." + id + ".warning";
    }

    public static DisasterType byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (DisasterType t : values()) {
            if (t.id.equals(s) || t.name().toLowerCase(Locale.ROOT).equals(s)) {
                return t;
            }
        }
        return null;
    }

    public static DisasterType byOrdinal(int ordinal) {
        DisasterType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TORNADO;
    }
}
