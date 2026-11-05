package com.example.smfsaddon.effect;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.manager.KakarotSlots;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.effect.SpiritAttributes;
import com.xie.smfs.manager.GhostDomainManager;
import com.xie.smfs.registry.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * 超级赛亚人形态 —— 随开随放。
 *
 * 两套加成必须同时做，缺一不可：
 *   1. 属性修正（AttributeModifier）：影响原版属性读取、UI 面板、移速/护甲等；
 *   2. NBT 灵异伤害（spiritDamage）：**打鬼时本体读的是 NBT 而不是属性实例**
 *      （GhostEntity#damage 里 `PlayerEvents.getSpiritAttributes(player)`），
 *      所以只加属性修正对鬼完全无效。
 *
 * NBT 加成是破坏性的，必须能回滚，因此：
 *   - 生效时把原值备份到 NBT `smfsaddon_ssj_backup`
 *   - 结束时还原并删掉备份
 *   - 玩家上线时若发现"有备份但没在变身"（服务器崩溃残留），自动还原
 */
public class SuperSaiyanEffect extends StatusEffect {

    /** 变身期间灵异伤害倍率 */
    public static final double SPIRIT_DAMAGE_MULTIPLIER = 50.0D;

    /** 每秒上涨的复苏度 */
    public static final int REVIVAL_COST_PER_SECOND = 2;

    /** NBT 备份键 */
    public static final String BACKUP_KEY = "smfsaddon_ssj_backup";

    /**
     * 变身期间一起放大的 NBT 字段。
     * 这几个都是本体 HUD（最大/当前灵异强度、灵异抗性）与伤害公式实际读取的值，
     * 只改属性实例不会反映到面板和打鬼伤害上。
     */
    private static final String[] SCALED_KEYS = {
            "spiritDamage", "maxSpirit", "currentSpirit", "spiritResistance"
    };

    /** 复用本体金色鬼域的等级（1 起算） */
    public static final int DOMAIN_LEVEL = 3;

    private static final DustParticleEffect KI_GOLD =
            new DustParticleEffect(new Vec3d(1.0, 0.84, 0.15).toVector3f(), 0.9f);
    private static final DustParticleEffect KI_WHITE =
            new DustParticleEffect(new Vec3d(1.0, 0.99, 0.85).toVector3f(), 0.7f);

    public SuperSaiyanEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0xFFD700);

        // 属性侧：让面板/原版读取同步变强（对鬼无效，但对普通生物和 UI 有效）
        this.addAttributeModifier(SpiritAttributes.SPIRIT_DAMAGE,
                "c1a5e000-0001-4b01-9f01-5a1b2c3d4e01", SPIRIT_DAMAGE_MULTIPLIER - 1.0D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(SpiritAttributes.SPIRIT_RESISTANCE,
                "c1a5e000-0002-4b01-9f01-5a1b2c3d4e02", 50.0D,
                EntityAttributeModifier.Operation.ADDITION);
        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "c1a5e000-0003-4b01-9f01-5a1b2c3d4e03", 0.30D,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
                "c1a5e000-0004-4b01-9f01-5a1b2c3d4e04", 50.0D,
                EntityAttributeModifier.Operation.ADDITION);
        this.addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                "c1a5e000-0005-4b01-9f01-5a1b2c3d4e05", 0.5D,
                EntityAttributeModifier.Operation.ADDITION);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        spawnAura(player);

        if (player.age % 20 == 0) {
            burnRevival(player);
            GhostDomainManager.applyTargetGhostDomainToOtherPlayers(
                    player, ModEffects.GOLDEN_GHOST_DOMAIN_TARGET, DOMAIN_LEVEL);
        }
    }

    /** 体表金色气焰 */
    private void spawnAura(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        for (int i = 0; i < 3; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 0.5 + player.getRandom().nextDouble() * 0.4;
            world.spawnParticles(KI_GOLD,
                    x + Math.cos(angle) * radius,
                    y + player.getRandom().nextDouble() * 1.9,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.10, 0.0, 0.015);
        }

        if (player.age % 4 == 0) {
            world.spawnParticles(KI_WHITE, x, y + 1.4, z, 2, 0.35, 0.5, 0.35, 0.05);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.4, z, 1, 0.4, 0.5, 0.4, 0.1);
        }
    }

    /**
     * 每秒烧复苏度。
     *
     * <h2>烧谁</h2>
     *
     * <p>必须是<b>卡卡罗特自己</b>，不是"当前主鬼"。早先无条件取
     * {@code MainGhostManager.getMainGhostSlot}，玩家把卡卡罗特融进剑、
     * 主鬼槽里放着别的鬼时，扣的是别人的复苏度 —— 等于白嫖。
     *
     * <p>三种情况：卡卡罗特在鬼槽 → 烧那一格；只在物品上 → 烧物品上那条规律的
     * 复苏值，到顶就打断变身；哪都没有 → 什么都不烧（能变身说明判定放行了）。
     */
    private void burnRevival(ServerPlayerEntity player) {
        int slot = KakarotSlots.findSlot(player);
        if (slot >= 0) {
            int degree = PlayerEvents.getGhostSlotRevivalDegree(player, slot);
            int required = PlayerEvents.getGhostSlotRequiredRevivalDegree(player, slot);
            if (required <= 0) {
                required = 1000;
            }

            int next = Math.min(degree + REVIVAL_COST_PER_SECOND, required - 1);
            if (next > degree) {
                PlayerEvents.updateGhostSlotValue(player, slot, "revivalDegree", next);
            }

            if (next >= required - 1) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.revival_edge"), true);
            }
            return;
        }

        // 只有融合物品：烧物品上那条规律，涨满就压不住，变身被打断
        if (KakarotSlots.burnItemRevival(player, REVIVAL_COST_PER_SECOND)) {
            player.removeStatusEffect(SmfsAddon.SUPER_SAIYAN);
            player.sendMessage(Text.translatable("message.smfsaddon.kakarot.item_revival_edge"), true);
        }
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);

        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        // 本体 HUD 与伤害公式读的都是 NBT，所以这一整组都要备份 + 放大
        NbtCompound spirit = PlayerEvents.getSpiritAttributes(player);
        NbtCompound backup = new NbtCompound();
        for (String key : SCALED_KEYS) {
            double original = spirit.getDouble(key);
            backup.putDouble(key, original);
            spirit.putDouble(key, original * SPIRIT_DAMAGE_MULTIPLIER);
        }
        spirit.put(BACKUP_KEY, backup);
        PlayerEvents.setSpiritAttributes(player, spirit);

        // 鬼域交给本体
        player.addStatusEffect(new StatusEffectInstance(
                ModEffects.GOLDEN_GHOST_DOMAIN, Integer.MAX_VALUE, DOMAIN_LEVEL - 1,
                false, false, true));

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(KI_GOLD, player.getX(), player.getBodyY(0.5), player.getZ(),
                60, 0.6, 0.9, 0.6, 0.35);
        world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.8f);

        player.sendMessage(Text.translatable("message.smfsaddon.kakarot.transform_on"), false);
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);

        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }

        restoreSpiritDamage(player);
        player.removeStatusEffect(ModEffects.GOLDEN_GHOST_DOMAIN);

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.5), player.getZ(),
                20, 0.5, 0.6, 0.5, 0.06);

        player.sendMessage(Text.translatable("message.smfsaddon.kakarot.transform_off"), false);
    }

    /** 把变身放大的所有灵异数值还原 */
    public static void restoreSpiritDamage(ServerPlayerEntity player) {
        NbtCompound spirit = PlayerEvents.getSpiritAttributes(player);
        if (!spirit.contains(BACKUP_KEY)) {
            return;
        }

        net.minecraft.nbt.NbtElement stored = spirit.get(BACKUP_KEY);
        if (stored instanceof NbtCompound backup) {
            for (String key : SCALED_KEYS) {
                if (backup.contains(key)) {
                    spirit.putDouble(key, backup.getDouble(key));
                }
            }
        } else {
            // 兼容第一版单值备份
            spirit.putDouble("spiritDamage", spirit.getDouble(BACKUP_KEY));
        }

        spirit.remove(BACKUP_KEY);
        PlayerEvents.setSpiritAttributes(player, spirit);
    }

    /** 给外部复用的判级工具：与本体 increaseRevivalDegreeDaily 的算法保持一致 */
    public static int getRevivalLevel(PlayerEntity player, int slot) {
        int degree = PlayerEvents.getGhostSlotRevivalDegree(player, slot);
        int required = PlayerEvents.getGhostSlotRequiredRevivalDegree(player, slot);
        if (required <= 0) {
            required = 1000;
        }
        return Math.min((int) Math.floor((double) degree / (double) required * 10.0) + 1, 10);
    }
}
