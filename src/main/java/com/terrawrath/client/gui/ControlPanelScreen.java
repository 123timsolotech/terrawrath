package com.terrawrath.client.gui;

import com.terrawrath.TerraWrath;
import com.terrawrath.client.CinematicCamera;
import com.terrawrath.client.gui.HudOverlay.Mesh2D;
import com.terrawrath.disaster.DisasterType;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The Disaster Control Panel: an emergency-console layout with a red header, a crawling hazard stripe,
 * a 3x3 grid of disaster cards (icons spring up on hover), the intensity bar, placement toggle and the
 * Random / Stop All / Cinematic Cam row.
 */
public class ControlPanelScreen extends Screen {
    static final Identifier HAZARD = TerraWrath.id("textures/misc/hazard_red.png");

    static float intensity = 1.0F;
    static boolean inFront = true;

    private int panelW, panelH, x0, y0;
    private int headH;
    private int activeCount;

    public ControlPanelScreen() {
        super(Component.translatable("screen.terrawrath.control_panel"));
    }

    static Identifier icon(String name) {
        return TerraWrath.id("textures/gui/icon/" + name + ".png");
    }

    /** Wall-clock animation time in ticks, so the panel animates even while the game is paused or slowed. */
    static float animTime() {
        return (System.currentTimeMillis() % 3_600_000L) / 50F;
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width - 12, 540);
        panelH = Math.min(this.height - 8, 310);
        x0 = (this.width - panelW) / 2;
        y0 = (this.height - panelH) / 2;
        boolean roomy = panelH >= 250;
        headH = roomy ? 24 : 19;
        int stripe = roomy ? 6 : 5;
        int pad = 6;
        int footer = 10;
        int rowH = roomy ? 22 : 17;
        int gap = roomy ? 5 : 3;
        int footerY = y0 + panelH - footer;
        int row2Y = footerY - 2 - rowH;
        int row1Y = row2Y - gap - rowH;
        int cardsTop = y0 + headH + stripe + gap;
        int cardsBottom = row1Y - gap;
        int cw = (panelW - pad * 2 - gap * 2) / 3;
        int ch = (cardsBottom - cardsTop - gap * 2) / 3;
        DisasterType[] types = DisasterType.values();
        for (int i = 0; i < types.length; i++) {
            int col = i % 3, row = i / 3;
            addRenderableWidget(new DisasterCard(x0 + pad + col * (cw + gap), cardsTop + row * (ch + gap), cw, ch, types[i]));
        }
        int innerW = panelW - pad * 2;
        int half = (innerW - gap) / 2;
        addRenderableWidget(new IntensitySlider(x0 + pad, row1Y, half, rowH));
        addRenderableWidget(new PanelButton(x0 + pad + half + gap, row1Y, innerW - half - gap, rowH,
            () -> Component.translatable(inFront ? "screen.terrawrath.front_on" : "screen.terrawrath.front_off"),
            null, 0x4FC3D8, 0x7FE8FF, b -> inFront = !inFront, false));
        int third = (innerW - gap * 2) / 3;
        addRenderableWidget(new PanelButton(x0 + pad, row2Y, third, rowH, () -> Component.translatable("screen.terrawrath.random"),
            icon("random"), 0xE0782A, 0xFF9A3A, b -> {
                send("disaster random " + fmt(intensity) + " " + (inFront ? "front" : "nearby"));
                onClose();
            }, true));
        addRenderableWidget(new PanelButton(x0 + pad + third + gap, row2Y, third, rowH, () -> Component.translatable("screen.terrawrath.stop"),
            icon("stop"), 0xD8262C, 0xFF3A30, b -> {
                send("disaster stop");
                if (CinematicCamera.active()) {
                    CinematicCamera.stop();
                }
                onClose();
            }, true));
        addRenderableWidget(new PanelButton(x0 + pad + (third + gap) * 2, row2Y, innerW - (third + gap) * 2, rowH,
            () -> Component.translatable(CinematicCamera.active() ? "screen.terrawrath.cinematic_off" : "screen.terrawrath.cinematic"),
            icon("camera"), 0x4FC3D8, 0x7FE8FF, b -> {
                onClose();
                CinematicCamera.toggle();
            }, true));
        // small letterbox toggle in the footer, right aligned
        Component lb = Component.translatable("screen.terrawrath.letterbox_off");
        int lbW = (int) (font.width(lb) * 0.75F) + 10;
        addRenderableWidget(new FooterToggle(x0 + panelW - pad - lbW, footerY, lbW, 9));
        countActive();
    }

    /** Used by the dev auto-test to press a disaster card exactly like a click would. */
    public boolean pressCard(DisasterType type, float atIntensity) {
        intensity = atIntensity;
        inFront = true;
        for (var child : children()) {
            if (child instanceof DisasterCard card && card.type == type) {
                card.onPress(null);
                return true;
            }
        }
        return false;
    }

    /** Dev auto-test: pretend the mouse rests on a card so its hover animation can be captured. */
    public void hoverCard(DisasterType type) {
        for (var child : children()) {
            if (child instanceof DisasterCard card) {
                card.forcedHover = card.type == type;
            }
        }
    }

    private void countActive() {
        activeCount = 0;
        if (minecraft.level != null) {
            for (Entity e : minecraft.level.entitiesForRendering()) {
                if (e instanceof DisasterEntity) {
                    activeCount++;
                }
            }
        }
    }

    static String fmt(float f) {
        return String.format(Locale.ROOT, "%.2f", f);
    }

    private void send(String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft.level != null && minecraft.level.getGameTime() % 10 == 0) {
            countActive();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        gg.fillGradient(0, 0, this.width, this.height, 0x78000000, 0xB0100406);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        float time = animTime();
        // body
        gg.fill(x0 - 1, y0 - 1, x0 + panelW + 1, y0 + panelH + 1, 0xFF2A0A0C);
        gg.fillGradient(x0, y0, x0 + panelW, y0 + panelH, 0xF6151011, 0xF60C0909);
        // header band
        gg.fillGradient(x0, y0, x0 + panelW, y0 + headH, 0xFF7A1418, 0xFF4A0A0E);
        gg.fill(x0, y0, x0 + panelW, y0 + 1, 0x60FF8A80);
        float brandScale = headH >= 24 ? 1.75F : 1.35F;
        int textY = y0 + (headH - (int) (8 * brandScale)) / 2 + 1;
        gg.fill(x0 + 7, y0 + headH / 2 - 2, x0 + 11, y0 + headH / 2 + 2, 0xFFF8EEDC);
        Component brand = Component.literal("TERRAWRATH").withStyle(ChatFormatting.BOLD);
        gg.pose().pushMatrix();
        gg.pose().translate(x0 + 16, textY);
        gg.pose().scale(brandScale, brandScale);
        gg.text(font, brand, 0, 0, 0xFFF8EEDC, true);
        gg.pose().popMatrix();
        int subX = x0 + 16 + (int) (font.width(brand) * brandScale) + 10;
        float subScale = headH >= 24 ? 1.3F : 1.0F;
        gg.pose().pushMatrix();
        gg.pose().translate(subX, y0 + (headH - (int) (8 * subScale)) / 2 + 1);
        gg.pose().scale(subScale, subScale);
        gg.text(font, Component.translatable("screen.terrawrath.subtitle"), 0, 0, 0xFFF2A49A, false);
        gg.pose().popMatrix();
        Component status = Component.translatable("screen.terrawrath.active", activeCount);
        int sc = activeCount > 0 ? ((int) (time / 8) % 2 == 0 ? 0xFFFF5A3A : 0xFFFFC23D) : 0xFF7CF0A0;
        gg.pose().pushMatrix();
        gg.pose().translate(x0 + panelW - 8 - font.width(status) * subScale, y0 + (headH - (int) (8 * subScale)) / 2 + 1);
        gg.pose().scale(subScale, subScale);
        gg.text(font, status, 0, 0, sc, true);
        gg.pose().popMatrix();
        // crawling hazard stripe (moves left -> right)
        int stripe = headH >= 24 ? 6 : 5;
        stripes(gg, x0, y0 + headH, panelW, stripe, time * 0.9F, 0xFFFFFFFF);
        // footer hint
        fitText(gg, font, Component.translatable("screen.terrawrath.footer"), x0 + 7, y0 + panelH - 8, panelW - 90, 0xFF6A6268, false, 0.7F);
        super.extractRenderState(gg, mouseX, mouseY, a);
    }

    /** Draws a red/black hazard stripe band whose stripes crawl to the right. */
    static void stripes(GuiGraphicsExtractor gg, int x, int y, int w, int h, float time, int color) {
        int texH = 16;
        int uWidth = Math.max(1, w * texH / Math.max(1, h));
        int scroll = (int) (time) % 64;
        gg.blit(RenderPipelines.GUI_TEXTURED, HAZARD, x, y, 64 - scroll, 0, w, h, uWidth, texH, 64, 16, color);
    }

    /** Draws text shrunk (never enlarged beyond maxScale) so it fits in maxWidth. */
    static void fitText(GuiGraphicsExtractor gg, Font font, Component text, int x, int y, int maxWidth, int color, boolean shadow, float maxScale) {
        int w = font.width(text);
        float scale = Math.min(maxScale, w > 0 ? maxWidth / (float) w : maxScale);
        gg.pose().pushMatrix();
        gg.pose().translate(x, y);
        gg.pose().scale(scale, scale);
        gg.text(font, text, 0, 0, color, shadow);
        gg.pose().popMatrix();
    }

    static void click(SoundManager sm) {
        sm.play(SimpleSoundInstance.forUI(TWRegistry.UI_CLICK.get(), 1.0F, 0.6F));
    }

    /** A horizontal colour gradient made of thin vertical slices (the GUI only has vertical gradients). */
    static void hGradient(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (w <= 0) {
            return;
        }
        int step = Math.max(1, w / 48);
        for (int x = x0; x < x1; x += step) {
            float t = (x - x0) / (float) Math.max(1, w - 1);
            gg.fill(x, y0, Math.min(x1, x + step), y1, 0xFF000000 | Mesh2D.mix(left, right, t));
        }
    }

    // ------------------------------------------------------------------ widgets

    private class DisasterCard extends AbstractButton {
        private final DisasterType type;
        boolean forcedHover;
        // icon spring (bouncy hover)
        private float scale = 1, vel;
        private long lastMs;
        private boolean wasHover;

        DisasterCard(int x, int y, int w, int h, DisasterType type) {
            super(x, y, w, h, Component.translatable(type.nameKey()));
            this.type = type;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            send("disaster start " + type.id + " " + fmt(intensity) + " " + (inFront ? "front" : "nearby"));
            onClose();
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            click(soundManager);
        }

        private void spring(boolean hover) {
            long now = System.currentTimeMillis();
            float dt = lastMs == 0 ? 0 : Math.min(0.05F, (now - lastMs) / 1000F);
            lastMs = now;
            if (hover && !wasHover) {
                vel += 5.5F; // the pop
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(TWRegistry.UI_CLICK.get(), 1.9F, 0.12F));
            }
            wasHover = hover;
            float target = hover ? 1.16F : 1.0F;
            // under-damped spring: overshoots and settles with a little wobble
            int steps = Math.max(1, (int) Math.ceil(dt / 0.004F));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                float acc = 520 * (target - scale) - 15 * vel;
                vel += acc * h;
                scale += vel * h;
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused() || forcedHover;
            spring(hover);
            int accent = type.color;
            gg.fill(x, y, x + w, y + h, hover ? 0xFF2C201A : 0xFF1B1614);
            if (hover) {
                gg.fillGradient(x, y, x + w, y + h, Mesh2D.argb(0.16F, accent), Mesh2D.argb(0.04F, accent));
                gg.outline(x, y, w, h, 0xFF000000 | accent);
                gg.outline(x + 1, y + 1, w - 2, h - 2, Mesh2D.argb(0.55F, accent));
            }
            gg.fill(x, y, x + 3, y + h, 0xFF000000 | accent);
            // icon, springing up on hover
            int iconSize = (int) Math.min(h - 12, w * 0.3F);
            float cx = x + 8 + iconSize / 2F, cy = y + h / 2F;
            float lift = (scale - 1) * iconSize * 0.35F;
            gg.pose().pushMatrix();
            gg.pose().translate(cx, cy - lift);
            float k = iconSize / 32F * scale;
            gg.pose().scale(k, k);
            gg.blit(RenderPipelines.GUI_TEXTURED, icon(type.id), -16, -16, 0, 0, 32, 32, 64, 64, 64, 64);
            gg.pose().popMatrix();
            // title in the disaster colour, description underneath
            float ts = Mth.clamp(h / 46F, 0.95F, 1.6F);
            int tx = x + 8 + iconSize + 8;
            int textW = x + w - 4 - tx;
            Component name = type == DisasterType.EVENT_HORIZON
                ? Component.translatable(type.nameKey()).append(Component.literal(" ★").withStyle(s -> s.withColor(0xC08AFF)))
                : Component.translatable(type.nameKey());
            int titleY = y + Math.max(3, (int) (h * 0.1F));
            fitText(gg, font, name, tx, titleY, textW, 0xFF000000 | Mesh2D.mix(accent, 0xFFFFFF, 0.12F), true, ts);
            int nextY = titleY + (int) (9 * ts) + 2;
            float ds = Mth.clamp(ts * 0.72F, 0.6F, 1.05F);
            gg.enableScissor(x, nextY, x + w, y + h - 2);
            gg.pose().pushMatrix();
            gg.pose().translate(tx, nextY);
            gg.pose().scale(ds, ds);
            gg.textWithWordWrap(font, Component.translatable(type.descKey()), 0, 0, (int) (textW / ds), 0xFFD6CEC6, false);
            gg.pose().popMatrix();
            gg.disableScissor();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private class PanelButton extends AbstractButton {
        private final Supplier<Component> label;
        private final Identifier iconTex;
        private final int border;
        private final int textColor;
        private final Consumer<PanelButton> action;
        private final boolean big;

        PanelButton(int x, int y, int w, int h, Supplier<Component> label, Identifier icon, int border, int textColor,
                    Consumer<PanelButton> action, boolean big) {
            super(x, y, w, h, label.get());
            this.label = label;
            this.iconTex = icon;
            this.border = border;
            this.textColor = textColor;
            this.action = action;
            this.big = big;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.accept(this);
            setMessage(label.get());
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            click(soundManager);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            gg.fill(x, y, x + w, y + h, hover ? 0xFF000000 | Mesh2D.mix(0x1B1614, border, 0.22F) : 0xFF1B1614);
            gg.outline(x, y, w, h, 0xFF000000 | (hover ? Mesh2D.mix(border, 0xFFFFFF, 0.25F) : border));
            if (hover) {
                gg.outline(x + 1, y + 1, w - 2, h - 2, Mesh2D.argb(0.45F, border));
            }
            Component msg = label.get();
            if (big) {
                msg = msg.copy().withStyle(ChatFormatting.BOLD);
            }
            float ts = Mth.clamp((h - 8) / 9F, 1.0F, 1.45F);
            int iconS = iconTex != null ? h - 8 : 0;
            int textW = (int) (font.width(msg) * ts);
            int contentW = textW + (iconS > 0 ? iconS + 5 : 0);
            int maxContent = w - 8;
            if (contentW > maxContent) {
                ts *= (maxContent - (iconS > 0 ? iconS + 5 : 0)) / (float) textW;
                textW = (int) (font.width(msg) * ts);
                contentW = textW + (iconS > 0 ? iconS + 5 : 0);
            }
            int cx = x + (w - contentW) / 2;
            if (iconS > 0) {
                gg.blit(RenderPipelines.GUI_TEXTURED, iconTex, cx, y + 4, 0, 0, iconS, iconS, 64, 64, 64, 64);
                cx += iconS + 5;
            }
            gg.pose().pushMatrix();
            gg.pose().translate(cx, y + (h - 8 * ts) / 2F + 0.5F);
            gg.pose().scale(ts, ts);
            gg.text(font, msg, 0, 0, 0xFF000000 | (hover ? Mesh2D.mix(textColor, 0xFFFFFF, 0.3F) : textColor), true);
            gg.pose().popMatrix();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private class FooterToggle extends AbstractButton {
        FooterToggle(int x, int y, int w, int h) {
            super(x, y, w, h, Component.translatable("screen.terrawrath.letterbox_off"));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            CinematicCamera.letterbox = !CinematicCamera.letterbox;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            click(soundManager);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
            Component msg = Component.translatable(CinematicCamera.letterbox ? "screen.terrawrath.letterbox_on" : "screen.terrawrath.letterbox_off");
            boolean hover = isHoveredOrFocused();
            int color = hover ? 0xFFF4EEE6 : CinematicCamera.letterbox ? 0xFF7FE8FF : 0xFF8A8288;
            int tw = (int) (font.width(msg) * 0.75F);
            fitText(gg, font, msg, getX() + getWidth() - tw, getY() + 1, getWidth(), color, false, 0.75F);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private class IntensitySlider extends AbstractSliderButton {
        IntensitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (intensity - 0.25F) / 1.75F);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float v = 0.25F + (float) this.value * 1.75F;
            setMessage(Component.translatable("screen.terrawrath.intensity_value", String.format(Locale.ROOT, "%.1f", v)));
        }

        @Override
        protected void applyValue() {
            intensity = 0.25F + (float) this.value * 1.75F;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            gg.fill(x, y, x + w, y + h, 0xFF1B1614);
            int fillW = (int) ((w - 2) * this.value);
            hGradient(gg, x + 1, y + 1, x + 1 + fillW, y + h - 1, 0xE8261A, 0xFFA43A);
            gg.outline(x, y, w, h, hover ? 0xFFFFC23D : 0xFFB0602A);
            int knob = x + 1 + Mth.clamp(fillW, 1, w - 3);
            gg.fill(knob - 2, y + 1, knob + 2, y + h - 1, 0xFFF8EEDC);
            gg.fill(knob + 2, y + 1, knob + 3, y + h - 1, 0x80000000);
            Component msg = getMessage().copy().withStyle(ChatFormatting.BOLD);
            float ts = Mth.clamp((h - 8) / 9F, 1.0F, 1.45F);
            int tw = (int) (font.width(msg) * ts);
            if (tw > w - 8) {
                ts *= (w - 8) / (float) tw;
                tw = w - 8;
            }
            gg.pose().pushMatrix();
            gg.pose().translate(x + (w - tw) / 2F, y + (h - 8 * ts) / 2F + 0.5F);
            gg.pose().scale(ts, ts);
            gg.text(font, msg, 0, 0, 0xFFF8EEDC, true);
            gg.pose().popMatrix();
        }
    }
}
