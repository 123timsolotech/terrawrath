package com.terrawrath.item;

import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterManager;
import com.terrawrath.entity.DisasterEntity;
import com.terrawrath.registry.TWRegistry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Survival gadget: beeps faster as a disaster approaches and reports the threat when used.
 * The HUD readout is drawn client-side for players carrying it.
 */
public class DisasterDetectorItem extends Item {
    public DisasterDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        Disaster nearest = nearest(player);
        int interval;
        if (nearest != null && nearest.phase() == DisasterEntity.WARNING) {
            int t = nearest.ticksUntilActive();
            interval = t < 60 ? 4 : t < 140 ? 8 : 16;
        } else if (nearest != null && nearest.phase() == DisasterEntity.ACTIVE) {
            interval = 30;
        } else {
            long natural = DisasterManager.naturalCountdown();
            interval = natural > 0 && natural < 20 * 60 ? 60 : -1;
        }
        if (interval > 0 && level.getGameTime() % interval == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), TWRegistry.BEEP.get(), SoundSource.PLAYERS, 0.6F,
                nearest != null && nearest.phase() == DisasterEntity.WARNING ? 1.3F : 1.0F);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            Disaster d = nearest(sp);
            if (d != null) {
                Vec3 off = d.center().subtract(sp.position());
                int dist = (int) Math.sqrt(off.x * off.x + off.z * off.z);
                String dir = compass(off.x, off.z);
                Component state = d.phase() == DisasterEntity.WARNING
                    ? Component.translatable("terrawrath.detector.incoming", d.ticksUntilActive() / 20).withStyle(ChatFormatting.RED)
                    : Component.translatable("terrawrath.detector.active").withStyle(ChatFormatting.GOLD);
                sp.sendSystemMessage(Component.translatable("terrawrath.detector.report",
                    Component.translatable(d.type.nameKey()).withStyle(ChatFormatting.BOLD), dist, dir).append(" ").append(state), true);
            } else {
                long natural = DisasterManager.naturalCountdown();
                if (natural > 0) {
                    sp.sendSystemMessage(Component.translatable("terrawrath.detector.calm_eta", natural / 20 / 60 + 1).withStyle(ChatFormatting.GREEN), true);
                } else {
                    sp.sendSystemMessage(Component.translatable("terrawrath.detector.calm").withStyle(ChatFormatting.GREEN), true);
                }
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), TWRegistry.BEEP.get(), SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    private static Disaster nearest(ServerPlayer player) {
        Disaster best = null;
        double bestD = Double.MAX_VALUE;
        for (Disaster d : DisasterManager.active()) {
            if (d.level != player.level()) {
                continue;
            }
            double dd = d.center().distanceToSqr(player.position());
            if (dd < bestD && dd < 600 * 600) {
                bestD = dd;
                best = d;
            }
        }
        return best;
    }

    public static String compass(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dz, dx));
        String[] dirs = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        int idx = (int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8;
        return dirs[idx];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.terrawrath.disaster_detector.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
