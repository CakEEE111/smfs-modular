package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.pijiagu.UnionCapture;
import com.xie.smfs.common.events.PlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 礼成体的技能包 —— 走本体那套「技能 = 一个 *_skill 通道」的约定。
 *
 * <h2>按键怎么接</h2>
 *
 * <p>本体给鬼域技能留了 V / G / J / N 四个键，按键处理在
 * {@code KeyEventHandler} 里分发到 {@code GhostSkillSystem.handleXKeySkill}。
 * 那个方法对不认识的鬼种是空转，所以我们在客户端监听<b>同样四个 KeyBinding</b>，
 * 当主鬼是礼成体时发我们自己的包 —— 按键、界面分组、冷却都还是本体的。</p>
 *
 * <h2>为什么服务端要校验</h2>
 *
 * <p>客户端可以伪造包。所以服务端先确认玩家槽位里真的有礼成体，
 * 才执行技能 —— 不然任何人都能白嫖。</p>
 */
public final class UnionSkillPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "pijiagu_union_skill");

    /** 0 = 骨手（V），1 = 拜堂（G） */
    public static final byte ACTION_BONE_HAND = 0;
    public static final byte ACTION_BOW = 1;

    private static final Map<UUID, Long> LAST_USE = new HashMap<>();
    private static final int COOLDOWN_TICKS = 60;
    /** 拜堂的灵力代价 */
    private static final float COST_SPIRIT = 120.0F;

    private UnionSkillPacket() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, sender) -> {
                    byte action = buf.readByte();
                    server.execute(() -> handle(player, action));
                });
    }

    private static void handle(ServerPlayerEntity player, byte action) {
        if (!ownsUnion(player)) {
            player.sendMessage(Text.literal("§7你身上没有礼成体。"), true);
            return;
        }
        long now = player.getWorld().getTime();
        Long last = LAST_USE.get(player.getUuid());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_USE.put(player.getUuid(), now);

        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (action == ACTION_BONE_HAND) {
            boneHand(world, player);
        } else {
            bow(world, player);
        }
    }

    /**
     * V · 骨手 —— 抓住一个，拖到面前；快死的直接折断。
     *
     * <p>不是范围 AOE，是<b>点杀</b>：抓最近的那一个、拖过来、按血量决定收尾。
     * 这是礼成体"把东西收进仪式"的动作。</p>
     */
    private static void boneHand(ServerWorld world, ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);

        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(6.0D), e -> e != player && e.isAlive())) {
            Vec3d to = candidate.getPos().subtract(eye).normalize();
            if (to.dotProduct(look) < 0.5D) {
                continue;   // 只抓前方 60°
            }
            double dist = candidate.squaredDistanceTo(player);
            if (dist < best) {
                best = dist;
                target = candidate;
            }
        }

        if (target == null) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.PLAYERS, 0.8F, 0.6F);
            player.sendMessage(Text.literal("§7骨手抓了个空。"), true);
            return;
        }

        float ratio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
        float dealt = com.example.smfsaddon.combat.SpiritStrike.deal(player, target, 60.0F);

        // 处决线：血量低于 35% 的对象直接被折断
        if (ratio < 0.35F && target.isAlive()) {
            com.example.smfsaddon.combat.SpiritStrike.deal(player, target, 60.0F);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    target.getX(), target.getBodyY(0.5), target.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
            player.sendMessage(Text.literal("§d骨手收下了它。"), true);
        }

        // 拖到面前：直接把位置拉近，比给速度可靠
        if (target.isAlive()) {
            Vec3d dir = player.getPos().subtract(target.getPos()).normalize();
            Vec3d dest = player.getPos().add(dir.multiply(1.6D));
            target.requestTeleport(dest.x, target.getY(), dest.z);
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 2));
        }

        world.spawnParticles(ParticleTypes.ASH, target.getX(), target.getBodyY(0.6), target.getZ(),
                24, 0.3, 0.6, 0.3, 0.03);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.PLAYERS, 1.0F, 0.6F);
        player.sendMessage(Text.literal("§7骨手：" + Math.round(dealt) + " 点灵异伤害"), true);
    }

    /**
     * G · 拜堂 —— 把礼堂铺在地上，持续 6 秒。
     *
     * <p>领域跟着你走：范围内每秒被礼压一次（灵异伤害 + 缓慢），
     * 同时你自己获得护盾与抗性 —— 因为你正在行礼，礼不会伤你。</p>
     */
    private static void bow(ServerWorld world, ServerPlayerEntity player) {
        // 代价：拜堂要用鬼的力量，先抽灵力
        float spirit = com.xie.smfs.common.events.PlayerEvents
                .getSpiritAttribute(player, "currentSpirit", 0.0F);
        if (spirit < COST_SPIRIT) {
            player.sendMessage(Text.literal("§7灵力不足，礼办不起来。"), true);
            return;
        }
        com.xie.smfs.common.events.PlayerEvents.setSpiritAttribute(player,
                com.xie.smfs.effect.SpiritAttributes.CURRENT_SPIRIT, spirit - COST_SPIRIT);

        // 服务端只做伤害脉冲；视觉交给客户端幻影（不生成实体 → 不可能推动玩家）
        com.example.smfsaddon.pijiagu.UnionDomain.start(player);
        // 守护灵礼堂：普通实体（无 AI、无碰撞、不推人）
        // 服务端只做伤害脉冲；视觉由客户端图层负责（没有实体 → 无物理问题）
        com.example.smfsaddon.pijiagu.UnionDomain.start(player);
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.squaredDistanceTo(player) < 64.0D * 64.0D) {
                UnionBowFxPacket.sendStart(viewer, player.getId(), 160);
            }
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.2F, 0.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 0.6F, 0.7F);
        player.sendMessage(Text.literal("§d礼堂降临：6 秒内，范围内的存在都要行礼。"), true);
    }

    /**
     * 玩家是否驾驭着礼成体。
     *
     * <p>不要自己翻槽位 NBT —— 本体的 {@code hasGhostType} 才是官方入口，
     * 它内部会走 {@code getGhostType(itemStack)}，兼容不同版本的存储方式。</p>
     */
    private static boolean ownsUnion(ServerPlayerEntity player) {
        try {
            return PlayerEvents.hasGhostType(player, UnionCapture.GHOST_TYPE)
                    || PlayerEvents.hasGhostType(player, "smfsaddon:" + UnionCapture.GHOST_TYPE);
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static void sendFromClient(byte action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ID, buf);
    }
}
