package com.terrawrath.client;

import com.mojang.datafixers.util.Pair;
import com.terrawrath.TerraWrath;
import com.terrawrath.disaster.DisasterManager;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.disaster.Terrain;
import com.terrawrath.entity.DisasterEntity;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development-only automated director: creates a test world, triggers every disaster at a fresh spot and
 * captures screenshots at their biggest moments. Enabled only with -Dterrawrath.autotest=all|type,type...
 * Never touches the mouse or keyboard.
 */
public final class AutoTest {
    private static final String PROP = System.getProperty("terrawrath.autotest", "");
    private static final boolean NIGHT = Boolean.getBoolean("terrawrath.autotest.night");
    private static final float INTENSITY = Float.parseFloat(System.getProperty("terrawrath.autotest.intensity", "1.0"));
    private static final String WORLD = "TWTest";

    private record Shot(int phase, float progress, boolean cinematic, boolean hud) {}

    private record Scenario(DisasterType type, float pitch, int stopAt, List<Shot> shots) {}

    private static int nextShot;
    private static int pendingCinematic = -1;
    private static int doneAt = -1;

    private static final List<Scenario> QUEUE = new ArrayList<>();
    private static int state;          // 0 idle, 1 waiting for world, 2 running
    private static int clock;
    private static int index = -1;
    private static int scenarioTick;
    private static boolean worldRequested;
    private static int prepared;

    private AutoTest() {}

    public static boolean enabled() {
        return !PROP.isBlank();
    }

    private static final int W = 0, A = 1, E = 2;

    /** shots: phase, progress, cinematic, hud ... */
    private static Scenario sc(DisasterType t, float pitch, int stop, Object... shots) {
        List<Shot> list = new ArrayList<>();
        for (int i = 0; i < shots.length; i += 4) {
            list.add(new Shot((Integer) shots[i], ((Number) shots[i + 1]).floatValue(), (Boolean) shots[i + 2], (Boolean) shots[i + 3]));
        }
        return new Scenario(t, pitch, stop, list);
    }

    private static boolean creativeMode() {
        return PROP.equalsIgnoreCase("creative");
    }

    private static void tickCreative(Minecraft mc) {
        scenarioTick++;
        if (scenarioTick == 20) {
            var screen = new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,
                mc.player.connection.enabledFeatures(), true);
            mc.gui.setScreen(screen);
        }
        if (scenarioTick == 40 && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
            try {
                net.minecraft.world.item.CreativeModeTab tab = com.terrawrath.registry.TWRegistry.TAB.get();
                java.lang.reflect.Method m;
                try {
                    m = screen.getClass().getMethod("setSelectedTab", net.minecraft.world.item.CreativeModeTab.class); // Fabric API paging
                } catch (NoSuchMethodException e) {
                    m = screen.getClass().getDeclaredMethod("selectTab", net.minecraft.world.item.CreativeModeTab.class);
                }
                m.setAccessible(true);
                m.invoke(screen, tab);
                TerraWrath.LOG.info("[AutoTest] creative: selected TerraWrath tab, items {}", tab.getDisplayItems().stream()
                    .map(st -> st.getItem().toString()).toList());
            } catch (Throwable t) {
                TerraWrath.LOG.error("[AutoTest] creative: no TerraWrath tab: {}", t.toString());
            }
        }
        if (scenarioTick == 70) {
            Screenshot.grab(mc.gameDirectory, "tw_creative_tab.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
            TerraWrath.LOG.info("[AutoTest] captured tw_creative_tab.png");
        }
        if (scenarioTick == 90) {
            state = 3;
            mc.stop();
        }
    }

    private static boolean naturalMode() {
        return PROP.equalsIgnoreCase("natural");
    }

    private static int naturalShots;
    private static int randomAt = -1;

    private static void tickNatural(Minecraft mc) {
        scenarioTick++;
        if (scenarioTick == 1) {
            run("gamemode survival");
            run("effect give @s minecraft:resistance 600 255 true");
            run("effect give @s minecraft:regeneration 600 5 true");
            com.terrawrath.config.TWConfig.VALUES.naturalDisasters = true;
            com.terrawrath.config.TWConfig.VALUES.minMinutesBetweenDisasters = 1;
            com.terrawrath.config.TWConfig.VALUES.maxMinutesBetweenDisasters = 1;
            com.terrawrath.config.TWConfig.VALUES.naturalWarningSeconds = 12;
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> DisasterManager.scheduleNaturalIn(200));
            }
            TerraWrath.LOG.info("[AutoTest] natural mode: waiting for a natural disaster");
        }
        DisasterEntity target = null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d) {
                target = d;
            }
        }
        if (target != null && (naturalShots == 0 && target.phase() == DisasterEntity.WARNING && target.progress() > 0.4F
            || naturalShots == 1 && target.phase() == DisasterEntity.ACTIVE && target.progress() > 0.1F)) {
            String name = "tw_natural_" + naturalShots + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
            TerraWrath.LOG.info("[AutoTest] captured {} ({})", name, target.disasterType().id);
            naturalShots++;
        }
        if ((naturalShots >= 2 || scenarioTick > 20 * 240) && randomAt < 0) {
            TerraWrath.LOG.info("[AutoTest] natural mode done (shots={}), testing /disaster random", naturalShots);
            run("disaster stop");
            randomAt = scenarioTick + 40;
        }
        if (randomAt > 0 && scenarioTick == randomAt) {
            run("disaster random 0.6 front");
        }
        if (randomAt > 0 && scenarioTick == randomAt + 200) {
            Screenshot.grab(mc.gameDirectory, "tw_random.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
            TerraWrath.LOG.info("[AutoTest] captured tw_random.png");
        }
        if (randomAt > 0 && scenarioTick == randomAt + 240) {
            run("disaster list");
            run("disaster stop");
        }
        if (randomAt > 0 && scenarioTick > randomAt + 280) {
            state = 3;
            mc.stop();
        }
    }

    private static void buildQueue() {
        Map<DisasterType, Scenario> all = new LinkedHashMap<>();
        boolean F = false, T = true;
        all.put(DisasterType.TORNADO, sc(DisasterType.TORNADO, -12, 4000, W, 0.8, F, T, A, 0.12, F, F, A, 0.3, F, F, A, 0.45, T, F));
        all.put(DisasterType.VOLCANO, sc(DisasterType.VOLCANO, -14, 4000, W, 0.85, F, T, A, 0.2, F, F, A, 0.36, F, F, A, 0.48, F, F, A, 0.62, T, F));
        all.put(DisasterType.TSUNAMI, sc(DisasterType.TSUNAMI, -6, 4000, W, 0.75, F, T, A, 0.1, F, F, A, 0.14, T, F, A, 0.19, F, F, A, 0.215, F, F, A, 0.25, F, F));
        all.put(DisasterType.EARTHQUAKE, sc(DisasterType.EARTHQUAKE, 12, 4000, W, 0.75, F, T, A, 0.1, F, F, A, 0.25, F, F, A, 0.45, F, F, A, 0.7, T, F));
        all.put(DisasterType.METEOR_SHOWER, sc(DisasterType.METEOR_SHOWER, -18, 4000, W, 0.85, F, T, A, 0.15, F, F, A, 0.3, F, F, A, 0.55, F, F, A, 0.62, F, F, A, 0.665, F, F, A, 0.675, F, F, A, 0.69, F, F, A, 0.78, T, F));
        all.put(DisasterType.SUPERSTORM, sc(DisasterType.SUPERSTORM, -22, 4000, W, 0.85, F, T, A, 0.15, F, F, A, 0.3, F, F, A, 0.45, F, F, A, 0.6, T, F));
        all.put(DisasterType.BLIZZARD, sc(DisasterType.BLIZZARD, -6, 4000, W, 0.85, F, T, A, 0.015, F, F, A, 0.05, F, F, A, 0.12, F, F, A, 0.3, F, F, A, 0.5, T, F));
        all.put(DisasterType.SINKHOLE, sc(DisasterType.SINKHOLE, 18, 4000, W, 0.6, F, T, A, 0.05, F, F, A, 0.15, F, F, A, 0.3, F, F, A, 0.45, T, F));
        all.put(DisasterType.EVENT_HORIZON, sc(DisasterType.EVENT_HORIZON, -24, 4000,
            W, 0.08, F, T, W, 0.3, F, T, W, 0.55, F, F, W, 0.9, F, T,
            A, 0.02, F, F, A, 0.08, F, F, A, 0.2, F, F, A, 0.3, T, F,
            A, 0.42, F, T, A, 0.62, F, T, A, 0.72, F, T,
            A, 0.83, F, T, A, 0.95, F, F, E, 0.04, F, F, E, 0.1, F, F, E, 0.5, F, F, E, 0.99, F, F));
        if (PROP.equalsIgnoreCase("all")) {
            QUEUE.addAll(all.values());
        } else {
            for (String id : PROP.split(",")) {
                DisasterType t = DisasterType.byId(id.trim());
                if (t != null) {
                    QUEUE.add(all.get(t));
                }
            }
        }
    }

    public static void tick() {
        if (!enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        clock++;
        // never hold the user's mouse
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        if (state == 0) {
            if (mc.gui.screen() instanceof TitleScreen && clock > 40 && !worldRequested) {
                worldRequested = true;
                buildQueue();
                TerraWrath.LOG.info("[AutoTest] scenarios: {}", QUEUE.size());
                try {
                    if (mc.getLevelSource().levelExists(WORLD)) {
                        mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
                    } else {
                        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
                        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261019L, true, false),
                            WorldPresets::createNormalWorldDimensions, new TitleScreen());
                    }
                } catch (Exception e) {
                    TerraWrath.LOG.error("[AutoTest] could not open world", e);
                }
                state = 1;
            }
            return;
        }
        if (state == 1) {
            if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
                state = 2;
                scenarioTick = -200;
                run("gamemode creative");
                run("gamerule advance_time false");
                run("gamerule advance_weather false");
                run("time set " + (NIGHT ? "18000" : "6000"));
                run("weather clear");
                run("give @s terrawrath:disaster_detector");
            }
            return;
        }
        if (state != 2 || mc.player == null) {
            return;
        }
        if (naturalMode()) {
            tickNatural(mc);
            return;
        }
        if (creativeMode()) {
            tickCreative(mc);
            return;
        }
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof com.terrawrath.client.gui.ControlPanelScreen) && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            mc.gui.setScreen(null);
        }
        scenarioTick++;
        if (index < 0 || scenarioTick > QUEUE.get(index).stopAt) {
            nextShot = 0;
            pendingCinematic = -1;
            doneAt = -1;
            nextScenario(mc);
            return;
        }
        Scenario s = QUEUE.get(index);
        if (scenarioTick == -30) {
            mc.gui.setScreen(new com.terrawrath.client.gui.ControlPanelScreen());
        }
        if (scenarioTick == -27 && mc.gui.screen() instanceof com.terrawrath.client.gui.ControlPanelScreen panel) {
            panel.hoverCard(s.type);
        }
        if (scenarioTick == -25 && index == 0) {
            Screenshot.grab(mc.gameDirectory, "tw_panel_bounce.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
        }
        if (scenarioTick == -10 && index == 0) {
            Screenshot.grab(mc.gameDirectory, "tw_panel.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
        }
        if (scenarioTick == 0) {
            // launch through the real Control Panel card (GUI -> command -> server)
            if (!(mc.gui.screen() instanceof com.terrawrath.client.gui.ControlPanelScreen panel) || !panel.pressCard(s.type, INTENSITY)) {
                run("disaster start " + s.type.id + " " + String.format(Locale.ROOT, "%.2f", INTENSITY) + " front");
            }
        }
        DisasterEntity target = null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DisasterEntity d) {
                target = d;
            }
        }
        if (target == null) {
            if (scenarioTick > 120 && doneAt < 0) {
                doneAt = scenarioTick;
            }
        } else {
            horizonActions(mc, s, target);
        }
        if (target != null && nextShot < s.shots.size()) {
            Shot shot = s.shots.get(nextShot);
            boolean reached = target.phase() > shot.phase || target.phase() == shot.phase && target.progress() >= shot.progress;
            if (pendingCinematic < 0 && reached) {
                if (shot.cinematic) {
                    if (!CinematicCamera.active()) {
                        CinematicCamera.start();
                    }
                    pendingCinematic = 60;
                } else {
                    pendingCinematic = 2;
                }
                boolean hidden = mc.gui.hud.isHidden();
                if (shot.hud == hidden) {
                    mc.gui.hud.toggle();
                }
            }
        }
        if (pendingCinematic > 0 && --pendingCinematic == 0) {
            pendingCinematic = -1;
            Shot shot = s.shots.get(nextShot);
            String name = String.format(Locale.ROOT, "tw_%s_%02d%s.png", s.type.id, nextShot, NIGHT ? "_night" : "");
            TerraWrath.LOG.info("[AutoTest] shot {} tickrate {} at phase {} progress {} dilation {} white {}", nextShot, mc.level.tickRateManager().tickrate(), target != null ? target.phase() : -1,
                target != null ? String.format(Locale.ROOT, "%.3f", target.progress()) : "-", String.format(Locale.ROOT, "%.2f", ClientFX.dilation(0)),
                String.format(Locale.ROOT, "%.2f", ClientFX.whiteout(0)));
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
            int particles = 0;
            TerraWrath.LOG.info("[AutoTest] captured {} fps={} entities={} debris={}", name, mc.getFps(), mc.level.getEntityCount(),
                com.terrawrath.entity.DebrisEntity.LIVE.get());
            if (shot.cinematic) {
                CinematicCamera.stop();
            }
            nextShot++;
            if (nextShot >= s.shots.size()) {
                doneAt = scenarioTick;
            }
        }
        if (doneAt >= 0 && scenarioTick - doneAt > 30) {
            scenarioTick = Integer.MAX_VALUE / 2;
        }
    }

    private static int hzStep;
    private static net.minecraft.world.phys.Vec3 hzHome;

    /** Event Horizon: fly next to the core to film time dilation, then let the singularity drag us in (survival). */
    private static void horizonActions(Minecraft mc, Scenario s, DisasterEntity target) {
        if (s.type != DisasterType.EVENT_HORIZON || target.phase() != DisasterEntity.ACTIVE) {
            return;
        }
        float pr = target.progress();
        double x = target.getX(), y = target.getY(), z = target.getZ();
        if (hzStep == 0 && pr >= 0.36F) {
            hzStep = 1;
            hzHome = mc.player.position();
            float r = Math.max(4, target.a());
            run(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", x + r * 1.9, y - r * 0.4, z + r * 0.6, x, y, z));
            TerraWrath.LOG.info("[AutoTest] horizon: moved next to the core (r={})", r);
        }
        if (hzStep == 1 && pr >= 0.46F) {
            hzStep = 2;
            run(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", hzHome.x, hzHome.y, hzHome.z, x, y, z));
            run("effect give @s minecraft:resistance 60 255 true");
            run("gamemode survival");
            TerraWrath.LOG.info("[AutoTest] horizon: survival pull test from {}", hzHome);
        }
        if (hzStep == 2 && pr >= 0.6F) {
            hzStep = 3;
            TerraWrath.LOG.info("[AutoTest] horizon: after pull player at {} dist to core {} tickrate {}", mc.player.position(),
                String.format(Locale.ROOT, "%.1f", mc.player.position().distanceTo(target.position())), mc.level.tickRateManager().tickrate());
        }
        if (hzStep == 3 && pr >= 0.74F) {
            hzStep = 4;
            TerraWrath.LOG.info("[AutoTest] horizon: before reset player dist to core {} tickrate {}",
                String.format(Locale.ROOT, "%.1f", mc.player.position().distanceTo(target.position())), mc.level.tickRateManager().tickrate());
            run("gamemode creative");
            run(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", hzHome.x, hzHome.y, hzHome.z, x, y, z));
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                });
            }
        }
    }

    private static void nextScenario(Minecraft mc) {
        hzStep = 0;
        if (CinematicCamera.active()) {
            CinematicCamera.stop();
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (index >= 0 && server != null) {
            run("disaster stop");
        }
        index++;
        if (index >= QUEUE.size()) {
            TerraWrath.LOG.info("[AutoTest] all scenarios done, quitting");
            state = 3;
            mc.stop();
            return;
        }
        Scenario s = QUEUE.get(index);
        scenarioTick = -160;
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        if (server == null) {
            return;
        }
        int slot = index;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            ServerLevel level = p.level();
            int x = 3000 + slot * 1600;
            int z = (int) (slot * 400 * (NIGHT ? -1 : 1));
            float yaw = 0;
            if (s.type == DisasterType.TSUNAMI) {
                Pair<BlockPos, Holder<Biome>> ocean = level.findClosestBiome3d(h -> h.is(BiomeTags.IS_OCEAN), new BlockPos(x, 64, z), 6400, 32, 64);
                if (ocean != null) {
                    BlockPos o = ocean.getFirst();
                    // walk from the ocean toward land in the direction with the nearest shore
                    double bestDist = 1e9;
                    double bestAng = 0;
                    for (int a = 0; a < 16; a++) {
                        double ang = a * Math.PI / 8;
                        for (int r = 16; r < 400; r += 16) {
                            int sx = o.getX() + (int) (Math.cos(ang) * r), sz = o.getZ() + (int) (Math.sin(ang) * r);
                            level.getChunk(sx >> 4, sz >> 4);
                            if (Terrain.solidGround(level, sx, sz) > level.getSeaLevel() + 1) {
                                if (r < bestDist) {
                                    bestDist = r;
                                    bestAng = ang;
                                }
                                break;
                            }
                        }
                    }
                    x = o.getX() + (int) (Math.cos(bestAng) * (bestDist + 40));
                    z = o.getZ() + (int) (Math.sin(bestAng) * (bestDist + 40));
                    // face back toward the sea
                    yaw = (float) Math.toDegrees(Math.atan2(-Math.sin(bestAng), -Math.cos(bestAng))) - 90;
                    TerraWrath.LOG.info("[AutoTest] ocean at {}, standing at {} {} shore {}", o, x, z, bestDist);
                }
            }
            if (s.type != DisasterType.TSUNAMI) {
                // look for dry land so the disaster has terrain to wreck
                outer:
                for (int r = 0; r < 1600; r += 64) {
                    for (int a = 0; a < 8; a++) {
                        int lx = x + (int) (Math.cos(a * Math.PI / 4) * r), lz = z + (int) (Math.sin(a * Math.PI / 4) * r);
                        level.getChunk(lx >> 4, lz >> 4);
                        int sy = Terrain.solidGround(level, lx, lz);
                        if (sy > level.getSeaLevel() + 2 && !Terrain.isWater(level.getBlockState(new BlockPos(lx, Terrain.top(level, lx, lz), lz)))) {
                            x = lx;
                            z = lz;
                            break outer;
                        }
                    }
                }
            }
            level.getChunk(x >> 4, z >> 4);
            int y = Terrain.top(level, x, z) + (s.type == DisasterType.SINKHOLE || s.type == DisasterType.EARTHQUAKE ? 14 : 9);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(level, x + 0.5, Math.max(y, level.getSeaLevel() + 6), z + 0.5, java.util.Set.of(), yaw, s.pitch, false);
            prepared = slot;
        });
        TerraWrath.LOG.info("[AutoTest] scenario {} -> {}", index, s.type.id);
    }

    private static void run(String command) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), command);
        });
    }

    static int clamp(int v, int lo, int hi) {
        return Mth.clamp(v, lo, hi);
    }

    static File dir() {
        return Minecraft.getInstance().gameDirectory;
    }
}
