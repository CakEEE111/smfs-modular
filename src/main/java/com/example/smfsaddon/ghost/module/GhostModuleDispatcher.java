package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.event.GhostAttackRuleEvent;
import com.example.smfsaddon.ghost.module.event.GhostDamagedEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.event.GhostItemUseEvent;
import com.example.smfsaddon.ghost.module.event.GhostNearbyPlayerEvent;
import com.example.smfsaddon.ghost.module.event.GhostTickEvent;
import com.example.smfsaddon.ghost.module.host.GhostEntityHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.SpatialHost;
import com.example.smfsaddon.ghost.module.host.TickHost;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 厉鬼模块调度器。
 *
 * <p>这是整个模块系统对外的唯一入口，鬼实体只需要在
 * {@code tick()}、{@code damage()}、攻击判定三处各调用一次，
 * 其余全部由本类完成。
 *
 * <p>设计上刻意把所有遍历都建立在 {@code new ArrayList<>(...)} 快照上：
 * 规则在触发过程中挂载或卸载模块是合法用法，不能因此抛并发修改异常。
 */
public final class GhostModuleDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("smfs/GhostModule");

    private GhostModuleDispatcher() {
    }

    /**
     * 给宿主挂载模块并触发 {@code onAttach}。
     */
    public static GhostModuleData attach(LingyiHost host, Identifier moduleId) {
        GhostModule module = GhostModuleRegistry.get(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("Unregistered ghost module: " + moduleId);
        }
        if (!host.supportsCapabilities(module)) {
            throw new IllegalArgumentException(
                    "Host lacks capabilities required by module " + moduleId
                            + ": " + missingCapabilities(host, module)
            );
        }
        GhostModuleData data = module.createData();
        host.modules().put(data);
        module.onAttach(new GhostModuleContext(host, module, data));
        return data;
    }

    /**
     * 列出宿主缺少的能力，用于装载失败时的报错。
     *
     * <p>装载期就炸掉，比运行时静默不生效强得多 ——
     * 后者会让人以为是规则写错了。
     */
    public static List<Class<? extends HostCapability>> missingCapabilities(LingyiHost host, GhostModule module) {
        List<Class<? extends HostCapability>> missing = new ArrayList<>();
        for (Class<? extends HostCapability> required : module.requires()) {
            if (host.capability(required).isEmpty()) {
                missing.add(required);
            }
        }
        return missing;
    }

    /**
     * 从宿主卸载模块并触发 {@code onDetach}。
     */
    public static boolean detach(LingyiHost host, Identifier moduleId) {
        GhostModuleData data = host.modules().get(moduleId);
        if (data == null) {
            return false;
        }
        GhostModule module = GhostModuleRegistry.get(moduleId);
        if (module != null) {
            module.onDetach(new GhostModuleContext(host, module, data));
        }
        return host.modules().remove(moduleId) != null;
    }

    /**
     * 每 tick 调用一次。
     *
     * <p>职责：推进自然复苏、派发 tick 事件、按需扫描邻近玩家。
     */
    public static void tick(LingyiHost host) {
        GhostModuleContainer container = host.modules();
        if (container.isEmpty()) {
            return;
        }

        // 协同先于模块调度：让「刚拼上的组合」这一 tick 就能生效
        syncCombos(host);

        // 邻近扫描只依赖空间能力 —— 之前写成「必须有 GhostEntity」，
        // 导致方块/道具宿主上的扫描永远不触发。这正是加非实体宿主才暴露出来的。
        Optional<SpatialHost> spatial = host.capability(SpatialHost.class);

        // 宿主有心跳就用心跳；没有（比如某些道具宿主）就退回世界时间，
        // 否则 tick 恒为 0，取模判定永远成立，会变成每 tick 全量扫描。
        long tick = host.capability(TickHost.class)
                .map(TickHost::ticks)
                .orElseGet(() -> spatial
                        .map(host1 -> host1.world().getTime())
                        .orElse(0L));

        GhostTickEvent tickEvent = new GhostTickEvent(tick);

        for (GhostModuleData data : new ArrayList<>(container.all())) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            if (!host.supportsCapabilities(module)) {
                continue;
            }

            if (host.allowsRevivalStimulation() && data.revival() < module.maxRevival()) {
                double next = data.revival() + module.naturalRevivalRate() / 20.0D;
                data.setRevival(Math.min(next, module.maxRevival()));
            }

            GhostModuleContext context = new GhostModuleContext(host, module, data);

            dispatch(context, tickEvent);

            int interval = module.nearbyScanInterval();
            if (interval > 0 && spatial.isPresent() && tick % interval == 0) {
                scanNearby(context, spatial.get(), module.nearbyScanRadius());
            }
        }
    }

    /**
     * 协同同步：宿主同时具备所需模块时装上，缺了就卸下。
     *
     * <p>协同不需要玩家手动挂、也没有独立的调度分支 —— 它就是一个模块，
     * 只是「何时挂」由这里决定。这样优先级、互斥、代价、图鉴全部原样复用。
     *
     * <p>成本很低：协同数量是个位到十位级，每次只做几次 map 查找。
     */
    private static void syncCombos(LingyiHost host) {
        Collection<GhostCombo> combos = GhostComboRegistry.all();
        if (combos.isEmpty()) {
            return;
        }
        for (GhostCombo combo : combos) {
            boolean satisfied = host.modules().hasAll(combo.requiredModules());
            boolean present = host.modules().has(combo.id());
            if (satisfied == present) {
                continue;
            }
            try {
                if (satisfied) {
                    attach(host, combo.id());
                } else {
                    detach(host, combo.id());
                }
            } catch (RuntimeException exception) {
                // 协同挂载失败不应连累整只鬼
                LOGGER.error("Failed to sync combo {} on host", combo.id(), exception);
            }
        }
    }

    /**
     * 对宿主上所有模块派发事件。
     */
    public static void dispatchAll(LingyiHost host, GhostEvent event) {
        GhostModuleContainer container = host.modules();
        if (container.isEmpty()) {
            return;
        }
        for (GhostModuleData data : new ArrayList<>(container.all())) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            if (!host.supportsCapabilities(module)) {
                continue;
            }
            dispatch(new GhostModuleContext(host, module, data), event);
        }
    }

    /**
     * 鬼受到伤害。
     */
    public static void damaged(LingyiHost host, DamageSource source, float amount) {
        dispatchAll(host, new GhostDamagedEvent(source, amount));
    }

    /**
     * 物品被使用（规律被移植到物品上之后的主要触发方式）。
     *
     * <p>同一个规则实现，挂在鬼身上走 tick / attack 事件，
     * 移植到物品上就走这里 —— 规则本身不需要知道自己现在在谁身上。
     */
    public static void itemUsed(LingyiHost host, GhostItemUseEvent event) {
        dispatchAll(host, event);
    }

    /**
     * 物品攻击命中（规律移植到武器上之后的另一条触发路径）。
     */
    public static void attackHit(LingyiHost host,
                                 com.example.smfsaddon.ghost.module.event.GhostAttackHitEvent event) {
        dispatchAll(host, event);
    }

    /**
     * 持有者受伤（物品护主）。
     */
    public static void ownerDamaged(LingyiHost host,
                                    com.example.smfsaddon.ghost.module.event.GhostOwnerDamagedEvent event) {
        dispatchAll(host, event);
    }

    /**
     * 杀人规律裁决。
     *
     * <p>无模块时直接返回 {@link AttackVerdict#ABSTAIN}，
     * 让调用方回落到原有逻辑。
     */
    public static AttackVerdict attackVerdict(LingyiHost host, PlayerEntity player) {
        GhostModuleContainer container = host.modules();
        if (container.isEmpty()) {
            return AttackVerdict.ABSTAIN;
        }

        GhostAttackRuleEvent event = new GhostAttackRuleEvent(player);
        for (GhostModuleData data : new ArrayList<>(container.all())) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            if (!host.supportsCapabilities(module)) {
                continue;
            }
            dispatch(new GhostModuleContext(host, module, data), event);
        }
        return event.verdict();
    }

    /**
     * 释放指定模块的指定能力。
     *
     * @return 是否成功释放
     */
    public static boolean useAbility(LingyiHost host, Identifier moduleId, Identifier abilityId) {
        GhostModule module = GhostModuleRegistry.get(moduleId);
        if (module == null) {
            return false;
        }
        GhostModuleData data = host.modules().get(moduleId);
        if (data == null) {
            return false;
        }

        GhostModuleContext moduleContext = new GhostModuleContext(host, module, data);

        for (GhostAbility ability : module.abilities()) {
            if (!ability.id().equals(abilityId)) {
                continue;
            }
            GhostAbilityContext abilityContext = new GhostAbilityContext(moduleContext, ability);
            if (!ability.canUse(abilityContext)) {
                return false;
            }
            moduleContext.addRevival(-ability.revivalCost());
            ability.use(abilityContext);
            return true;
        }
        return false;
    }

    /**
     * 对单个模块派发事件。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>元规则一票否决 —— 任一成立则本次事件结束；</li>
     *   <li>按 {@link GhostRuleTuning} 过滤停用规则、按事件类型筛选；</li>
     *   <li>收集所有满足条件的规则，按 {@code priority} 降序；</li>
     *   <li>依次执行，同 {@code exclusionGroup} 只允许第一条生效；</li>
     *   <li>每条触发后按 {@code revivalGain} × 全局倍率累加复苏值。</li>
     * </ol>
     */
    public static void dispatch(GhostModuleContext context, GhostEvent event) {
        GhostModule module = context.module();

        for (GhostMetaRule metaRule : module.metaRules()) {
            try {
                if (metaRule.vetoes(context, event)) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Ghost meta rule {} of module {} failed on event {}",
                        metaRule.id(),
                        module.id(),
                        event.id(),
                        exception
                );
            }
        }

        List<GhostRule> candidates = new ArrayList<>();
        for (GhostRule rule : module.rules()) {
            if (!GhostRuleTuning.isEnabled(rule.id())) {
                continue;
            }
            if (!rule.supports(event.id())) {
                continue;
            }
            try {
                if (rule.canTrigger(context, event)) {
                    candidates.add(rule);
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Ghost rule {} of module {} failed on canTrigger for {}",
                        rule.id(),
                        module.id(),
                        event.id(),
                        exception
                );
            }
        }

        if (candidates.isEmpty()) {
            return;
        }
        if (candidates.size() > 1) {
            candidates.sort(Comparator.comparingInt(GhostRule::priority).reversed());
        }

        Set<Identifier> usedGroups = null;
        for (GhostRule rule : candidates) {
            Identifier group = rule.exclusionGroup();
            if (group != null) {
                if (usedGroups == null) {
                    usedGroups = new HashSet<>();
                }
                if (usedGroups.contains(group)) {
                    continue;
                }
            }

            try {
                rule.onTrigger(context, event);
            } catch (RuntimeException exception) {
                // 单条规则出错不应连累整只鬼的 tick，也不占用互斥组
                LOGGER.error(
                        "Ghost rule {} of module {} failed on trigger for {}",
                        rule.id(),
                        module.id(),
                        event.id(),
                        exception
                );
                continue;
            }

            if (group != null) {
                usedGroups.add(group);
            }

            recordObservation(context, rule, event);

            double gain = rule.revivalGain() * GhostRuleTuning.revivalScale(rule.id());
            if (gain != 0.0D) {
                context.addRevival(gain);
            }
        }
    }

    /**
     * 图鉴观察记录。
     *
     * <p>只有同时满足三个条件才记：
     * 规则声明了图鉴条目、事件带玩家、宿主是实体。
     * 三者缺一都不算「被观察到」—— 没有旁观者的规律不进图鉴。
     */
    private static void recordObservation(GhostModuleContext context, GhostRule rule, GhostEvent event) {
        if (rule.codex() == null) {
            return;
        }
        PlayerEntity observer = event.player();
        if (observer == null) {
            return;
        }
        GhostEntity ghost = context.ghost();
        if (ghost == null) {
            return;
        }
        try {
            Identifier ghostType = Registries.ENTITY_TYPE.getId(ghost.getType());
            if (ghostType != null) {
                // 用世界时间而非实体 age 做节流基准：age 是新生成实体从 0 开始的，
                // 拿它和上一条记录比较会出现负差值，导致刚刷出来的鬼长时间记不上。
                RuleCodex.observe(observer, ghostType, rule.id(), ghost.getWorld().getTime());
            }
        } catch (RuntimeException exception) {
            // 图鉴记录失败绝不能影响战斗逻辑
            LOGGER.error(
                    "Failed to record codex observation for rule {} on {}",
                    rule.id(),
                    observer.getName().getString(),
                    exception
            );
        }
    }

    private static void scanNearby(GhostModuleContext context, SpatialHost spatial, double radius) {
        World world = spatial.world();
        if (world.isClient()) {
            return;
        }
        Vec3d origin = spatial.position();
        double radiusSq = radius * radius;
        for (PlayerEntity player : world.getPlayers()) {
            double distanceSq = player.getPos().squaredDistanceTo(origin);
            if (distanceSq <= radiusSq) {
                dispatch(context, new GhostNearbyPlayerEvent(player, distanceSq));
            }
        }
    }
}
