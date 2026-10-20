package com.terrawrath.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terrawrath.TerraWrath;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple JSON config at config/terrawrath.json (works on both loaders, no config library needed).
 */
public final class TWConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Paths.get("config", "terrawrath.json");

    public static Values VALUES = new Values();

    public static final class Values {
        public String _comment = "TerraWrath config. naturalDisasters: random disasters in survival over time.";
        /** Random disasters strike near players over time (with warnings). */
        public boolean naturalDisasters = true;
        public int minMinutesBetweenDisasters = 20;
        public int maxMinutesBetweenDisasters = 40;
        /** Seconds of warning (sirens, boss bar countdown) before a natural disaster hits. */
        public int naturalWarningSeconds = 30;
        /** Intensity for natural disasters (0.25 - 2.0). */
        public double naturalIntensity = 0.75;
        /** Disasters change terrain (blocks broken / placed). */
        public boolean terrainDestruction = true;
        /** Max block changes per server tick across all disasters (keeps the game smooth). */
        public int maxBlockChangesPerTick = 2600;
        /** Max flying debris entities alive at once. */
        public int maxDebrisEntities = 180;
        /** Give new players a Disaster Control Panel the first time they join. */
        public boolean giveControlPanelOnFirstJoin = false;
        /** Client: multiplier for disaster particle counts (0 - 2). */
        public double particleMultiplier = 1.0;
        /** Client: multiplier for camera shake (0 - 2). */
        public double screenShakeMultiplier = 1.0;
    }

    private TWConfig() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Values loaded = GSON.fromJson(reader, Values.class);
                    if (loaded != null) {
                        VALUES = loaded;
                    }
                }
            }
            sanitize();
            save();
        } catch (Exception e) {
            TerraWrath.LOG.warn("Could not read {}, using defaults: {}", FILE, e.toString());
            VALUES = new Values();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(VALUES, writer);
            }
        } catch (IOException e) {
            TerraWrath.LOG.warn("Could not write {}: {}", FILE, e.toString());
        }
    }

    private static void sanitize() {
        Values v = VALUES;
        v.minMinutesBetweenDisasters = Math.max(1, v.minMinutesBetweenDisasters);
        v.maxMinutesBetweenDisasters = Math.max(v.minMinutesBetweenDisasters, v.maxMinutesBetweenDisasters);
        v.naturalWarningSeconds = Math.max(5, Math.min(120, v.naturalWarningSeconds));
        v.naturalIntensity = Math.max(0.25, Math.min(2.0, v.naturalIntensity));
        v.maxBlockChangesPerTick = Math.max(100, Math.min(20000, v.maxBlockChangesPerTick));
        v.maxDebrisEntities = Math.max(0, Math.min(600, v.maxDebrisEntities));
        v.particleMultiplier = Math.max(0, Math.min(2, v.particleMultiplier));
        v.screenShakeMultiplier = Math.max(0, Math.min(2, v.screenShakeMultiplier));
    }
}
