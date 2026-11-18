package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.damage.ModDamageSources;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「皮」与「骨」的驾驭技能。
 *
 * <h2>皮 · 两个动作</h2>
 * <ul>
 *   <li><b>贴面</b>（V）：把一层皮糊到目标脸上 —— 失明 + 缓慢，皮替你挡下伤害</li>
 *   <li><b>裹</b>（G）：把自己裹起来 —— 抗性、清除灼烧与中毒，但走不快</li>
 * </ul>
 *
 * <h2>骨 · 两个动作</h2>
 * <ul>
 *   <li><b>立骨</b>（V）：脚下长出骨刺 —— 伤害 + 缓慢，残血的直接折断（虚弱）</li>
 *   <li><b>跪</b>（G）：范围内所有人被按住肩 —— 站不起来</li>
 * </ul>
 */
public final class PetSkillPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "pet_skill");

    public static final byte SKIN_V = 0;
    public static final byte SKIN_G = 1;
    public static final byte BONE_V = 2;
    public static final byte BONE_G = 3;

    /** 每个动作的冷却（tick） */
    private static final int[] COOLDOWN = {160, 400, 140, 500};

    private static final Map<UUID, long[]> LAST_USE = new HashMap<>();

    private PetSkillPacket() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, sender) -> {
            byte action = buf.readByte();
            server.execute(() -> handle(player, action));
        });
    }

    public static void sendFromClient(byte action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(ID, buf);
    }

    private static void handle(ServerPlayerEntity player, byte action) {
        String need = (action <= SKIN_G) ? "pijiagu_skin" : "pijiagu_bone";
        if (!PlayerEvents.hasGhostType(player, need)) {
            return;
        }
        long now = player.getWorld().getTime();
        long[] last = LAST_USE.computeIfAbsent(player.getUuid(), k -> new long[4]);
        if (now - last[action] < COOLDOWN[action]) {
            return;
        }
        last[action] = now;

        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        switch (action) {
            case SKIN_V -> faceWrap(world, player);
            case SKIN_G -> selfWrap(world, player);
            case BONE_V -> boneSpike(world, player);
            default -> forceKneel(world, player);
        }
    }

    // ---------------------------------------------------------------- 皮

    /** 贴面：失明 + 缓慢，皮替你挡一下 */
    private static void faceWrap(ServerWorld world, ServerPlayerEntity player) {
        LivingEntity target = nearest(world, player, 8.0D);
        if (target == null) {
            player.sendMessage(Text.literal("§7没有可以贴上去的脸。"), true);
            return;
        }
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false, false));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, false, false));
        if (target instanceof ServerPlayerEntity targetPlayer) {
            PlayerEvents.handleSpiritDamage(targetPlayer, 40.0F, 40.0F,
                    ModDamageSources.ghost(world));
        } else {
            target.damage(ModDamageSources.ghost(world), 6.0F);
        }
        // 皮替你挡
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1, false, false, true));
        player.sendMessage(Text.literal("§d你的皮贴在了它脸上。"), true);
    }

    /** 裹：自保 */
    private static void selfWrap(ServerWorld world, ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 2, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 0, false, false, false));
        player.removeStatusEffect(StatusEffects.POISON);
        player.removeStatusEffect(StatusEffects.WITHER);
        player.extinguish();
        player.sendMessage(Text.literal("§d你把自己裹了起来。"), true);
    }

    // ---------------------------------------------------------------- 骨

    /** 立骨：脚下骨刺 */
    private static void boneSpike(ServerWorld world, ServerPlayerEntity player) {
        Box area = player.getBoundingBox().expand(5.0D);
        int hit = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !(e instanceof HostileEntity && !isHostileTarget(e)))) {
            if (entity.squaredDistanceTo(player) > 25.0D) {
                continue;
            }
            if (entity instanceof ServerPlayerEntity targetPlayer) {
                PlayerEvents.handleSpiritDamage(targetPlayer, 60.0F, 60.0F,
                        ModDamageSources.ghost(world));
            } else {
                entity.damage(ModDamageSources.ghost(world), 8.0F);
            }
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, false, false, false));
            if (entity.getHealth() / entity.getMaxHealth() < 0.3F) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 2, false, false, false));
            }
            hit++;
        }
        player.sendMessage(Text.literal(hit > 0
                ? "§f骨从他们脚下长了出来。（" + hit + "）"
                : "§7没有踩到骨的地方。"), true);
    }

    /** 跪：站不起来 */
    private static void forceKneel(ServerWorld world, ServerPlayerEntity player) {
        Box area = player.getBoundingBox().expand(7.0D);
        int hit = 0;
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())) {
            if (entity instanceof ServerPlayerEntity targetPlayer) {
                PlayerEvents.handleSpiritDamage(targetPlayer, 30.0F, 30.0F,
                        ModDamageSources.ghost(world));
                targetPlayer.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 40, 255, false, false, false));
                UnionFxPacket.send(targetPlayer, UnionFxPacket.SEATED, 40);
            } else {
                entity.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 40, 6, false, false, false));
            }
            hit++;
        }
        player.sendMessage(Text.literal(hit > 0 ? "§f『都跪下。』" : "§7没有人需要跪。"), true);
    }

    private static boolean isHostileTarget(LivingEntity entity) {
        return entity instanceof net.minecraft.entity.mob.Monster;
    }

    /** 视线方向最近的活体 */
    private static LivingEntity nearest(ServerWorld world, ServerPlayerEntity player, double range) {
        Vec3d look = player.getRotationVec(1.0F);
        LivingEntity best = null;
        double bestScore = 0.55D;         // 必须大致在正前方
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(range), e -> e != player && e.isAlive())) {
            Vec3d dir = entity.getPos().add(0, entity.getHeight() * 0.5, 0)
                    .subtract(player.getEyePos());
            if (dir.length() > range) {
                continue;
            }
            double dot = dir.normalize().dotProduct(look);
            if (dot > bestScore) {
                bestScore = dot;
                best = entity;
            }
        }
        return best;
    }
}
