package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.effect.SpiritAttributes;
import com.xie.smfs.event.ModEvents;
import com.xie.smfs.entity.GhostEntity;
import com.xie.smfs.manager.GhostDomainManager;
import com.xie.smfs.manager.MainGhostManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * 「龟派气功」C2S 请求包。
 *
 * 实现方式是射线检测（hitscan），不是真实弹道实体：
 * 沿玩家视线每 0.5 格采样，命中第一个活体就结算灵异伤害。
 * 好处是不用注册自定义 EntityType，也不会有实体同步和性能问题。
 */
public final class KakarotKamehamehaPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "kakarot_kamehameha");

    private static final String COOLDOWN_KEY = "kakarot_kamehameha";
    /** 与本体通用技能一致：20 tick（1 秒）。真正的成本是每次释放的复苏度 */
    private static final int COOLDOWN_TICKS = 20;
    private static final double RANGE = 32.0;           // 射程
    private static final float BASE_DAMAGE = 120.0f;    // 基础灵异伤害
    private static final float DAMAGE_SPIRIT_SCALE = 0.5f;
    /** 每级鬼等级带来的技能伤害加成（10 级时 ×3.25） */
    private static final float LEVEL_DAMAGE_STEP = 0.25f; // 每点灵异伤害额外加成

    private static final DustParticleEffect BEAM_CORE =
            new DustParticleEffect(new Vec3d(0.75, 0.95, 1.0).toVector3f(), 1.5f);
    private static final DustParticleEffect BEAM_EDGE =
            new DustParticleEffect(new Vec3d(0.35, 0.65, 1.0).toVector3f(), 2.0f);

    private KakarotKamehamehaPacket() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, KakarotKamehamehaPacket::receive);
    }

    private static void receive(MinecraftServer server, ServerPlayerEntity player,
                                ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        // 与变身同一套口径：面板带 true（允许融合物品），按键带 false（只认鬼槽）
        boolean allowFused = buf.isReadable() && buf.readBoolean();
        server.execute(() -> tryCast(player, allowFused));
    }

    private static void tryCast(ServerPlayerEntity player, boolean allowFused) {
        // 1) 资格
        if (!allowFused) {
            // 按键路径：必须在鬼槽里养着（canUseGhostSkill 对融合物品是放行的，不能拿它判按键）
            if (com.example.smfsaddon.manager.KakarotSlots.findSlot(player) < 0) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.slot_only"), true);
                return;
            }
        } else {
            GhostDomainManager.SkillCheckResult result = GhostDomainManager.canUseGhostSkill(
                    player, "kakarot_ghost", SmfsAddon.KAKAROT_GHOST_ITEM, -1);
            if (result == GhostDomainManager.SkillCheckResult.NO_GHOST) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.no_ghost"), true);
                return;
            }
            if (result == GhostDomainManager.SkillCheckResult.LEVEL_TOO_LOW) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.level_too_low"), true);
                return;
            }
        }

        // 2) 冷却（本体冷却系统，自带"冷却中，剩余 x 秒"提示）
        if (!GhostDomainManager.checkAndSetJSkillCooldown(player, COOLDOWN_KEY, COOLDOWN_TICKS, "龟派气功")) {
            return;
        }

        // 3) 执行
        fire(player);

        // 4) 复苏度结算 —— 只记在卡卡罗特自己身上（槽里或物品上）。
        //    不能用 balanceRevivalDegree：那会给主鬼槽里的别的鬼加复苏度
        com.example.smfsaddon.manager.KakarotSlots.chargeRelease(
                player, com.example.smfsaddon.manager.KakarotSlots.RELEASE_COST);
    }

    private static void fire(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d far = start.add(dir.multiply(RANGE));

        // 方块遮挡
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start, far, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        double limit = blockHit.getType() == HitResult.Type.MISS ? RANGE : start.distanceTo(blockHit.getPos());

        // 沿射线找第一个活体
        LivingEntity target = null;
        Vec3d hitPos = start.add(dir.multiply(limit));

        outer:
        for (double d = 1.0; d <= limit; d += 0.5) {
            Vec3d point = start.add(dir.multiply(d));
            Box probe = new Box(point.x - 0.7, point.y - 0.7, point.z - 0.7,
                                point.x + 0.7, point.y + 0.7, point.z + 0.7);

            List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, probe,
                    e -> e != player && e.isAlive() && !e.isSpectator());

            if (!found.isEmpty()) {
                target = found.get(0);
                hitPos = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
                break;
            }

            // 采样点撞墙就停
            if (point.distanceTo(start) >= limit) {
                break outer;
            }
        }

        drawBeam(world, start, hitPos);

        if (target != null) {
            // 伤害随鬼等级成长：1 级 1.00 → 5 级 2.00 → 10 级 3.25
            // 等级取卡卡罗特自己的：槽里有就用槽里的，只有融合物品时按 1 级
            int level = com.example.smfsaddon.manager.KakarotSlots.level(player);
            float levelMultiplier = 1.0f + (level - 1) * LEVEL_DAMAGE_STEP;

            float spiritDamage = PlayerEvents.getSpiritAttribute(player, SpiritAttributes.SPIRIT_DAMAGE);
            float damage = (BASE_DAMAGE + Math.max(0.0f, spiritDamage) * DAMAGE_SPIRIT_SCALE) * levelMultiplier;

            dealDamage(player, target, damage, levelMultiplier);

            target.setVelocity(target.getVelocity().add(
                    dir.x * 1.2, 0.45, dir.z * 1.2));

            world.spawnParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 3, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticles(BEAM_CORE, hitPos.x, hitPos.y, hitPos.z, 30, 0.5, 0.5, 0.5, 0.3);
            world.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.3f);
    }

    /**
     * 结算伤害。
     *
     * 两个坑必须同时处理：
     *  1. 本体只认"玩家作为攻击者 + processingSpiritDamage 标记"，否则对鬼的伤害被直接吞掉；
     *  2. 打鬼时本体**忽略传入的 amount**，改用玩家 NBT 里的 spiritDamage 重算，
     *     所以等级加成必须临时写进 NBT，打完立刻还原。
     */
    private static void dealDamage(ServerPlayerEntity player, LivingEntity target,
                                   float amount, float levelMultiplier) {
        boolean isGhost = target instanceof GhostEntity;
        boolean scaled = isGhost && levelMultiplier > 1.001f;
        double original = 0.0;

        if (scaled) {
            NbtCompound spirit = PlayerEvents.getSpiritAttributes(player);
            original = spirit.getDouble("spiritDamage");
            spirit.putDouble("spiritDamage", original * levelMultiplier);
            PlayerEvents.setSpiritAttributes(player, spirit);
        }

        ModEvents.processingSpiritDamage.set(true);
        try {
            target.damage(player.getDamageSources().playerAttack(player), amount);
        } finally {
            ModEvents.processingSpiritDamage.set(false);
            if (scaled) {
                NbtCompound spirit = PlayerEvents.getSpiritAttributes(player);
                spirit.putDouble("spiritDamage", original);
                PlayerEvents.setSpiritAttributes(player, spirit);
            }
        }
    }

    /** 沿射线画光束 */
    private static void drawBeam(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vec3d step = delta.normalize().multiply(0.4);

        for (double d = 0.0; d < length; d += 0.4) {
            Vec3d p = from.add(step.multiply(d / 0.4));
            world.spawnParticles(BEAM_CORE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (((int) (d / 0.4)) % 3 == 0) {
                world.spawnParticles(BEAM_EDGE, p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0.01);
            }
            if (((int) (d / 0.4)) % 5 == 0) {
                world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }
}
