package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.effect.SeatedEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 「请入席」的演出状态机。
 *
 * <pre>
 * 0.0s  点名（标题显示玩家名字）+ 礼堂开始长
 * 4.5s  礼成，拉力开始：范围内的玩家被地面带向中心
 * 9.5s  入席：挂上「入席」效果（走不快、看不清、持续掉血）
 * 6.5s~ 礼成后追加：吊灯熄灭 → 落帘封门 → 身后纸人
 * 60s   席散：从高到低拆掉
 * </pre>
 *
 * <p>所有时间点是相对 {@code startTick} 的，用世界时间推进，不依赖实体存活。</p>
 */
public final class FeastCeremony {

    /** 席面半径（拉力与入席判定） */
    private static final double RADIUS = 27.0D;   // 席面放大三倍，范围同步
    private static final int RISE_TICKS = 90;        // 4.5 秒
    private static final int SEAT_TICKS = 190;       // 9.5 秒
    private static final int TOTAL_TICKS = 1200;     // 60 秒后席散

    private record Active(ServerWorld world, BlockPos center, long startTick, UUID caster) {
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    private FeastCeremony() {
    }

    /**
     * 开始一场席面。
     *
     * <p><b>全场只允许一场。</b>礼堂的方块、待放置队列、已放置清单都是全局静态的，
     * 同时开两场会互相覆盖（A 的席散把 B 的墙也拆了），所以这里直接按"已有就拒绝"处理，
     * 不做距离判断。</p>
     */
    public static boolean start(ServerWorld world, BlockPos center, ServerPlayerEntity caster) {
        if (!ACTIVE.isEmpty() || FeastSite.isBusy()) {
            SmfsAddon.LOGGER.info("[smfsaddon] 席面被拒：ACTIVE={} 正在生成={}",
                    ACTIVE.size(), FeastSite.isBusy());
            return false;   // 场上已经有席面（正在长或正在演）
        }
        long now = world.getTime();
        FeastSite.rise(world, center);
        ACTIVE.add(new Active(world, center, now, caster == null ? null : caster.getUuid()));

        // 点名：席上所有人都看得到
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(Vec3d.ofCenter(center)) > 48.0D * 48.0D) {
                continue;
            }
            callName(player, center, caster);
        }
        return true;
    }

    private static void callName(ServerPlayerEntity player, BlockPos center, ServerPlayerEntity caster) {
        String who = player.getName().getString();
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c『 入 席 』")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("§7" + who + "，请入席")));
    }

    /** 清空所有席面状态（停服 / 紧急重置） */
    public static void clear() {
        ACTIVE.clear();
        FeastSite.reset();
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active active = it.next();
            // 用服务器全局 tick：世界时间在离开该维度后可能停住，
            // 那样 elapsed 永远到不了 TOTAL_TICKS，ACTIVE 清不掉，
            // 之后所有席面都会被"全场唯一"挡死（换维度就会踩到）。
            long elapsed = server.getTicks() - active.startTick();

            if (elapsed > TOTAL_TICKS * 2) {
                SmfsAddon.LOGGER.warn("[smfsaddon] 席面超时（{} tick），强制清理", elapsed);
                try {
                    FeastSite.dissolve(active.world(), active.center());
                } catch (Throwable ignored) {
                }
                it.remove();
                continue;
            }

            if (elapsed >= TOTAL_TICKS) {
                // 席散：还在席上的人，全部留下
                Vec3d centerAtEnd = Vec3d.ofCenter(active.center());
                for (ServerPlayerEntity player : active.world().getPlayers()) {
                    if (player.isSpectator()
                            || player.getPos().distanceTo(centerAtEnd) > RADIUS + 2.0D) {
                        continue;
                    }
                    // 走本体的灵异伤害结算：内部会算灵异抗性、鬼寿衣、蹲下抵挡、被动等，并自行扣血
                    com.xie.smfs.common.events.PlayerEvents.handleSpiritDamage(player,
                            10000.0F, 10000.0F,
                            com.xie.smfs.damage.ModDamageSources.ghost(active.world()));
                    player.sendMessage(net.minecraft.text.Text.literal("§4礼成。"), true);
                }
                for (ServerPlayerEntity p : active.world().getPlayers()) {
                    com.example.smfsaddon.network.UnionFxPacket.send(p,
                            com.example.smfsaddon.network.UnionFxPacket.RELEASE, 0);
                }
                try {
                    FeastSite.dissolve(active.world(), active.center());
                } catch (Throwable throwable) {
                    SmfsAddon.LOGGER.warn("[smfsaddon] 席面拆除失败（可能是世界已卸载）: {}",
                            throwable.toString());
                }
                it.remove();
                continue;
            }

            Vec3d centerVec = Vec3d.ofCenter(active.center());

            // ── 声音：只在这些精确 tick 触发一次
            if (elapsed == 1) {
                playAt(active, SmfsAddon.PIJIAGU_PRELUDE, 0.45F, 0.75F);   // 低音铺底
            }
            if (elapsed == RISE_TICKS) {
                playAt(active, SmfsAddon.PIJIAGU_SUONA, 2.0F, 1.0F);        // 礼成：唢呐
                for (ServerPlayerEntity p : active.world().getPlayers()) {
                    if (p.getPos().distanceTo(centerVec) <= RADIUS) {
                        com.example.smfsaddon.network.UnionFxPacket.send(p,
                                com.example.smfsaddon.network.UnionFxPacket.SHAKE, 6);
                    }
                }
            }
            if (elapsed == TOTAL_TICKS - 100) {
                playAt(active, SmfsAddon.PIJIAGU_WEDDING, 1.2F, 0.62F);    // 席散前，压低的婚礼曲
            }

            // 一开始就拉 —— 礼堂还在长，脚下已经在带你了
            for (ServerPlayerEntity player : active.world().getPlayers()) {
                double dist = player.getPos().distanceTo(centerVec);
                if (dist > RADIUS || player.isSpectator()) {
                    continue;
                }
                // 越靠边拉力越明显，越靠中心越弱 —— 会自己往里滑，而不是被钉住
                // 席面放大到 27 格半径后，原参数拉不动边缘的人：
                // 边缘 0.28（明显被拽）、中心 0.06（站着还能转身）
                double ramp = Math.min(1.0D, 0.35D + elapsed / (double) RISE_TICKS);
                double strength = (0.22D * (dist / RADIUS) + 0.06D) * ramp;
                Vec3d pull = centerVec.subtract(player.getPos()).normalize().multiply(strength);
                player.addVelocity(pull.x, 0.0D, pull.z);
                player.velocityModified = true;

                // 入席
                if (elapsed >= SEAT_TICKS) {
                    // 状态效果只负责图标与视觉，伤害在这里直接结算 ——
                    // 之前依赖 StatusEffect#applyUpdateEffect，实测根本不被调用
                    if (player.getStatusEffect(SmfsAddon.SEATED) == null) {
                        player.addStatusEffect(new StatusEffectInstance(
                                SmfsAddon.SEATED, 400, 0, false, false, true));
                        SmfsAddon.LOGGER.info("[smfsaddon] 入席：{} 已在席间", player.getName().getString());
                    }
                    if (player.getStatusEffect(SmfsAddon.SEATED) != null && elapsed % 20 == 0) {
                        com.example.smfsaddon.network.UnionFxPacket.send(player,
                                com.example.smfsaddon.network.UnionFxPacket.SEATED, 40);
                    }
                    if (elapsed % 20 == 0) {
                        // 灵异伤害走本体结算：灵异攻击力²/(攻击力+抗性)，并处理鬼寿衣/蹲防/鬼影被动
                        float actual = com.xie.smfs.common.events.PlayerEvents.handleSpiritDamage(
                                player, 100.0F, 100.0F,
                                com.xie.smfs.damage.ModDamageSources.ghost(active.world()));
                        SmfsAddon.LOGGER.info("[smfsaddon] 入席结算：{} 实际受到 {} 点灵异伤害，剩余 {} 血",
                                player.getName().getString(), actual, player.getHealth());
                    }
                }
            }
        }
    }

    /** 在席面上放一段声音 */
    private static void playAt(Active active, net.minecraft.sound.SoundEvent sound,
                               float volume, float pitch) {
        active.world().playSound(null, active.center(), sound,
                net.minecraft.sound.SoundCategory.HOSTILE, volume, pitch);
    }

    public static boolean isBusy() {
        return !ACTIVE.isEmpty();
    }
}
