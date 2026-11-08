package com.example.smfsaddon.fusion;

import com.example.smfsaddon.assembly.GhostAssembly;
import com.example.smfsaddon.assembly.GhostVariant;
import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.ghost.module.impl.NativeGhostRuleModule;
import com.example.smfsaddon.item.ItemGhostHost;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把一只鬼的规律移植进物品。
 *
 * <h2>两个来源并存</h2>
 *
 * <p>移植时同时挂两样东西：
 * <ol>
 *   <li><b>本源规律</b>（{@link NativeGhostRuleModule}）—— 通用。它不复制逻辑，
 *       而是造一个影子实例、直接调用鬼自己的 {@code shouldAttackPlayer}。
 *       任何继承 {@code GhostEntity} 的鬼都能用，包括别的附属的；</li>
 *   <li><b>装配表里的模块</b> —— 补本源规律覆盖不到的部分
 *       （鬼域、开门这类写在 tick 里的行为）。</li>
 * </ol>
 *
 * <p>第一条是关键：有它在，别的附属的鬼<b>不需要任何单独适配</b>。
 *
 * <h2>压制不足是赌，不是失败</h2>
 *
 * <pre>
 * 成功率 = (压制 / 需求)² × 0.9，限制在 5% ~ 75%
 * 赌赢 → 仍然移植，反噬 ×0.6
 * 赌输 → 不移植，反噬 ×1.2
 * </pre>
 */
public final class GhostGrafting {

    private static final double MIN_CHANCE = 0.05D;
    private static final double MAX_CHANCE = 0.75D;

    public record Result(boolean success, double suppression, double required,
                         int grafted, double chance, boolean lucky, boolean escaped,
                         float escapeChance) {
    }

    private GhostGrafting() {
    }

    /**
     * 由鬼种字符串解析出实体类型 ID。
     */
    public static Identifier entityTypeOf(String ghostType) {
        if (ghostType == null || ghostType.isBlank()) {
            return null;
        }
        if (ghostType.indexOf(':') > 0) {
            return Identifier.tryParse(ghostType);
        }
        Identifier smfs = Identifier.tryParse("smfs:" + ghostType);

        // 先看装配表里有没有同名的（可能是别的命名空间）
        for (Identifier candidate : GhostAssembly.all().keySet()) {
            if (candidate.getPath().equals(ghostType)) {
                return candidate;
            }
        }

        // 再按已注册实体校准：别的附属的鬼在它自己的命名空间下，
        // 写死 smfs: 会导致影子实例造不出来，本源规律形同虚设
        if (smfs != null) {
            var direct = net.minecraft.registry.Registries.ENTITY_TYPE.getOrEmpty(smfs);
            if (direct.isPresent()) {
                return smfs;
            }
        }
        for (Identifier id : net.minecraft.registry.Registries.ENTITY_TYPE.getIds()) {
            if (id.getPath().equals(ghostType)) {
                return id;
            }
        }
        return smfs;
    }

    public static List<Identifier> modulesOfGhost(Identifier entityType) {
        List<GhostVariant> variants = GhostAssembly.variantsFor(entityType);
        if (variants.isEmpty()) {
            return List.of();
        }
        return variants.get(0).modules();
    }

    /**
     * 一次移植涉及的全部模块：装配表模块 + 本源规律。
     */
    public static List<Identifier> graftPlanFor(Identifier entityType) {
        Set<Identifier> plan = new LinkedHashSet<>(modulesOfGhost(entityType));
        // 无论有没有装配表，都挂上本源规律 —— 这才是「零适配」的来源
        plan.add(NativeGhostRuleModule.ID);
        return new ArrayList<>(plan);
    }

    /**
     * 按鬼类型移植。
     */
    public static Result tryGraftForGhost(ItemStack target, Identifier entityType, PlayerEntity player) {
        if (entityType == null) {
            return new Result(false, FusionFormula.suppression(player), 0.0D, 0, 0.0D, false, false, 0.0F);
        }
        return tryGraft(target, graftPlanFor(entityType), player, entityType);
    }

    public static Result tryGraft(ItemStack target, List<Identifier> moduleIds, PlayerEntity player) {
        return tryGraft(target, moduleIds, player, null);
    }

    /**
     * @param bindType 绑定给「本源规律」的鬼类型，可为 {@code null}
     */
    public static Result tryGraft(ItemStack target, List<Identifier> moduleIds,
                                  PlayerEntity player, Identifier bindType) {
        if (target.isEmpty() || moduleIds.isEmpty()) {
            return new Result(false, FusionFormula.suppression(player), 0.0D, 0, 0.0D, false, false, 0.0F);
        }

        double suppression = FusionFormula.suppression(player);
        // 已经绑了几只鬼 → 边际成本
        int existing = ItemGhostHost.boundGhostTypes(target).size();
        double required = FusionFormula.requiredFor(moduleIds, existing);
        if (required <= 0.0D) {
            return new Result(false, suppression, 0.0D, 0, 0.0D, false, false, 0.0F);
        }

        if (suppression + 1.0E-6D >= required) {
            int applied = graft(target, moduleIds, player, bindType);
            return new Result(true, suppression, required, applied, 1.0D, false, false, 0.0F);
        }

        double shortfall = required - suppression;
        double ratio = suppression / required;
        double chance = Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, ratio * ratio * 0.9D));
        boolean lucky = player.getRandom().nextDouble() < chance;

        if (!lucky) {
            FusionBacklash.apply(player, shortfall, required, FusionBacklash.FACTOR_FAILED);
            // 只在"真的赌输"时补这一刀。原先放在 tryGraftForGhost 的 !success 分支，
            // 会把"左槽为空 / 需求为 0"这类早退也算成失败，白扣灵异强度。
            boolean escaped = false;
            float escapeChance = 0.0F;
            if (bindType != null) {
                FusionBacklash.spiritBacklash(player, bindType.toString());
                // 先取这次实际要掷的概率，再去掷 —— 掷完计数可能清零，
                // 事后再查会得到一个和刚才无关的数字
                escapeChance = FusionEscape.chanceFor(player, bindType.toString());
                escaped = FusionEscape.maybeEscape(player, bindType.toString());
            }
            return new Result(false, suppression, required, 0, chance, false, escaped,
                    escapeChance);
        }

        FusionBacklash.apply(player, shortfall, required, FusionBacklash.FACTOR_LUCKY);
        if (bindType != null) {
            FusionEscape.reset(player, bindType.toString());
        }
        int applied = graft(target, moduleIds, player, bindType);
        return new Result(true, suppression, required, applied, chance, true, false, 0.0F);
    }

    private static int graft(ItemStack target, List<Identifier> moduleIds,
                             PlayerEntity player, Identifier bindType) {
        ItemGhostHost host = ItemGhostHost.open(target, player.getWorld(), player);
        for (Identifier moduleId : moduleIds) {
            if (host.modules().has(moduleId)) {
                continue;
            }
            try {
                GhostModuleDispatcher.attach(host, moduleId);
            } catch (RuntimeException ignored) {
                // 能力不匹配的模块直接跳过，不影响其它模块
            }
        }

        // 本源规律要记住「我是哪只鬼」，否则不知道去问谁
        if (bindType != null) {
            GhostModuleData nativeData = host.modules().get(NativeGhostRuleModule.ID);
            if (nativeData != null) {
                NativeGhostRuleModule.bind(nativeData, bindType);
            }
        }

        host.flush();
        // 回读：以真的写进物品 NBT 的条数为准
        return ItemGhostHost.moduleCount(target);
    }
}
