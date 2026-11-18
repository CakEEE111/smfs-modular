package com.example.smfsaddon.fusion;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.item.ItemGhostHost;
import com.example.smfsaddon.item.GhostSpiritPower;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.damage.ModDamageSources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 残响：融合物品上的鬼会反过来咬持有者。
 *
 * <h2>为什么要有这个</h2>
 *
 * <p>到 0.3 为止，融合只有好处 —— 融得越多、灵异伤害越高，代价只有一次性的素材消耗。
 * 玩家没有任何理由在战斗中途收起武器。</p>
 *
 * <p>而《神秘复苏》的底色是「越强的鬼越难驾驭」。所以这里补上那一半：
 * 物品里的鬼不只是工具，它一直在<b>躁动</b>。复苏值越高，越可能在攻击时
 * 顺着武器反咬你一口。</p>
 *
 * <h2>机制</h2>
 *
 * <p>不用新做判定 —— 直接看物品上各模块累积的复苏值：</p>
 *
 * <pre>
 * 躁动度 = Σ(模块复苏值) / Σ(模块复苏上限)
 * 躁动度 &lt; 阈值(50%)      → 无事
 * 否则每次命中 → 概率 (躁动度-阈值)/(1-阈值) × 上限(25%) 反噬
 * </pre>
 *
 * <p>反噬发生时<b>释放压力</b>：复苏值回退一截。所以它是收敛的循环，
 * 不是越用越死的雪球：用得凶 → 躁动 → 咬你一口 → 安静一阵 → 再次累积。</p>
 */
public final class ItemResonance {

    /** 躁动度低于这个值就完全安静 */
    private static final double THRESHOLD = 0.50D;

    /** 满躁动度时的反噬概率 */
    private static final double MAX_CHANCE = 0.25D;

    /** 反噬扣掉的灵异强度比例（占当前值） */
    private static final float SPIRIT_BITE_RATIO = 0.08F;

    /** 反噬后释放掉多少复苏（占各自上限的比例） */
    private static final double VENT_RATIO = 0.30D;

    /**
     * 每次命中给模块涨多少复苏（占各自上限的比例）。
     *
     * <h2>为什么必须手动涨</h2>
     *
     * <p>复苏值原本只靠 {@code GhostModuleDispatcher.tick} 里那行「自然复苏」推进，
     * 而物品宿主根本不被 tick —— 只有鬼灯笼例外。所以融合物品上的复苏值
     * 永远是 0，躁动度也永远是 0，残响等于没做。
     *
     * <p>改成"用一次涨一点"：1% 每次，50 刀进入躁动区，100 刀满。
     * 这个节奏和"打一场架就要掂量收不收手"是对齐的。
     */
    private static final double STIR_PER_HIT = 0.01D;

    /** 诊断用：前几次 stir 写日志，避免刷屏 */
    private static int stirLogged;

    private ItemResonance() {
    }

    /** 只读地算躁动度，供 tooltip 用 —— 不构造宿主 */
    public static double agitation(ItemStack stack) {
        NbtCompound root = stack.getSubNbt("SmfsAddonHost");
        if (root == null || !root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            return 0.0D;
        }
        NbtCompound modulesNbt = root.getCompound("Modules");
        if (!modulesNbt.contains("Modules", NbtElement.LIST_TYPE)) {
            return 0.0D;
        }
        NbtList list = modulesNbt.getList("Modules", NbtElement.COMPOUND_TYPE);

        double total = 0.0D;
        double cap = 0.0D;
        for (int index = 0; index < list.size(); index++) {
            NbtCompound one = list.getCompound(index);
            Identifier id = Identifier.tryParse(one.getString("Id"));
            if (id == null) {
                continue;
            }
            GhostModule module = GhostModuleRegistry.get(id);
            if (module == null) {
                continue;
            }
            double max = Math.max(1.0D, module.maxRevival());
            total += Math.min(max, one.getDouble("Revival"));
            cap += max;
        }
        return cap <= 0.0D ? 0.0D : total / cap;
    }

    /**
     * 按来源鬼分别算躁动度：鬼种 → 0~1。
     *
     * <p>依赖融合时写进模块的 {@code Source} 标记。老物品没有这个字段，
     * 返回空表 —— 调用方回落到整体躁动。
     */
    public static java.util.Map<String, Double> agitationByGhost(ItemStack stack) {
        NbtCompound root = stack.getSubNbt("SmfsAddonHost");
        if (root == null || !root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            return java.util.Map.of();
        }
        NbtCompound modulesNbt = root.getCompound("Modules");
        if (!modulesNbt.contains("Modules", NbtElement.LIST_TYPE)) {
            return java.util.Map.of();
        }
        NbtList list = modulesNbt.getList("Modules", NbtElement.COMPOUND_TYPE);

        java.util.Map<String, double[]> acc = new java.util.HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            NbtCompound one = list.getCompound(index);
            String source = one.getCompound("Custom").getString("Source");
            if (source == null || source.isBlank()) {
                continue;
            }
            Identifier id = Identifier.tryParse(one.getString("Id"));
            if (id == null) {
                continue;
            }
            GhostModule module = GhostModuleRegistry.get(id);
            if (module == null) {
                continue;
            }
            double max = Math.max(1.0D, module.maxRevival());
            double[] slot = acc.computeIfAbsent(source, ignored -> new double[2]);
            slot[0] += Math.min(max, one.getDouble("Revival"));
            slot[1] += max;
        }

        java.util.Map<String, Double> result = new java.util.HashMap<>();
        for (var entry : acc.entrySet()) {
            double cap = entry.getValue()[1];
            if (cap > 0.0D) {
                result.put(entry.getKey(), entry.getValue()[0] / cap);
            }
        }
        return result;
    }

    /**
     * 用一下就涨一点复苏 —— 这就是物品宿主缺的那段推进。
     *
     * <p>在 {@link #roll} 之前调用，先涨再判定，否则第一次永远看不到效果。
     */
    public static void stir(ItemStack stack, PlayerEntity holder) {
        if (holder == null || stack.isEmpty()
                || holder.getWorld() == null || holder.getWorld().isClient()) {
            return;
        }
        if (ItemGhostHost.moduleCount(stack) <= 0) {
            return;
        }
        ItemGhostHost host = ItemGhostHost.open(stack, holder.getWorld(), holder);
        if (host == null) {
            return;
        }

        boolean changed = false;
        double before = 0.0D;
        double after = 0.0D;
        double cap = 0.0D;

        for (GhostModuleData data : host.modules().all()) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            double max = Math.max(1.0D, module.maxRevival());
            cap += max;
            before += Math.min(max, data.revival());

            if (data.revival() < max) {
                data.setRevival(Math.min(max, data.revival() + max * STIR_PER_HIT));
                changed = true;
            }
            after += Math.min(max, data.revival());
        }
        if (!changed) {
            if (stirLogged++ < 5) {
                com.example.smfsaddon.SmfsAddon.LOGGER.info(
                        "[smfsaddon] 残响 stir：{}/{} 个模块已满，未变化", 0, 0);
            }
            return;
        }
        host.flush();

        if (stirLogged++ < 5) {
            com.example.smfsaddon.SmfsAddon.LOGGER.info(
                    "[smfsaddon] 残响 stir：复苏 {} → {}（上限 {}）", before, after, cap);
        }

        // 物品 NBT 变了，客户端那份还是旧的 —— 不同步就永远看不到躁动数字。
        // 按 10% 一档同步：逐刀同步等于每次命中多发一整个背包包，没必要。
        if (cap > 0.0D && (int) (before / cap * 10.0D) != (int) (after / cap * 10.0D)
                && holder instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && serverPlayer.currentScreenHandler != null) {
            serverPlayer.currentScreenHandler.syncState();
        }
    }

    /**
     * 攻击命中后掷一次残响。
     *
     * <p>在 {@code attackFired} 里调用，服务端、每次命中一次。
     */
    public static void roll(ItemStack stack, PlayerEntity holder) {
        if (holder == null || stack.isEmpty()
                || holder.getWorld() == null || holder.getWorld().isClient()) {
            return;
        }
        double agitation = agitation(stack);
        if (agitation < THRESHOLD) {
            return;
        }

        double span = Math.max(0.0001D, 1.0D - THRESHOLD);
        double chance = Math.min(MAX_CHANCE, (agitation - THRESHOLD) / span * MAX_CHANCE);
        if (holder.getRandom().nextDouble() >= chance) {
            return;
        }

        // 咬的是灵异强度，不是血。
        //
        // 残响的意义是「鬼在拖累你」，不是「鬼在打你」。扣血会变成纯粹的受伤惩罚，
        // 和本体的灵异反噬同一套语言：力量是有代价的，代价记在灵异强度上。
        var data = PlayerEvents.getSpiritAttributes(holder);
        float current = data.contains("currentSpirit")
                ? (float) data.getDouble("currentSpirit") : 0.0F;
        if (current <= 0.0F) {
            return;
        }

        float loss = Math.max(1.0F, current * SPIRIT_BITE_RATIO);
        float actual = Math.min(loss, current);
        PlayerEvents.addSpiritAttribute(holder,
                com.xie.smfs.effect.SpiritAttributes.CURRENT_SPIRIT, -actual);

        holder.getWorld().playSound(null, holder.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.PLAYERS, 0.5F, 1.4F);
        holder.sendMessage(Text.literal("§4残响 §8鬼咬了你一口 §c灵异强度 −"
                + Math.round(actual)), true);

        vent(stack, holder);
    }

    /** 释放压力：把各模块的复苏值按比例压回去 */
    private static void vent(ItemStack stack, PlayerEntity holder) {
        if (ItemGhostHost.moduleCount(stack) <= 0) {
            return;
        }
        ItemGhostHost host = ItemGhostHost.open(stack, holder.getWorld(), holder);
        if (host == null) {
            return;
        }
        boolean changed = false;
        for (GhostModuleData data : host.modules().all()) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            double reduce = module.maxRevival() * VENT_RATIO;
            if (data.revival() > 0.0D) {
                data.setRevival(Math.max(0.0D, data.revival() - reduce));
                changed = true;
            }
        }
        if (changed) {
            host.flush();
        }
    }
}
