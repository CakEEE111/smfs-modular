package com.example.smfsaddon.crossing;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.ghost.module.GhostModuleContainer;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.LingyiHost;
import com.example.smfsaddon.ghost.module.impl.CrossingModule;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 灵异交错：让一只鬼除了自己的杀人规律，还会用别人的。
 *
 * <h2>为什么不用再挂一个「本源规律」</h2>
 *
 * <p>本源规律模块是按 moduleId 单例的，一只鬼身上只能有一份。而它的
 * {@code Custom.GhostTypes} 本来就是逗号列表 —— 也就是说"一个模块、多个鬼种"
 * 这件事本来就支持。所以交错**不走**本源规律，而是单独一个模块：
 *
 * <ul>
 *   <li>本源规律：给<b>物品</b>用，物品攻击时按绑定的鬼逐个判</li>
 *   <li>交错模块：给<b>鬼</b>用，鬼要打人时多问几个"别人家的判定"</li>
 * </ul>
 *
 * <p>分开的好处是互不干扰：不交错的老鬼一个模块都没挂，行为与原来完全一致。
 *
 * <h2>影子实例要缓存</h2>
 *
 * <p>判断一次要造 N 个影子实体。老实现是每次 {@code type.create()} 一个新的、
 * 用完 discard —— 一次攻击 4 个，攻击又频繁，纯属给 GC 添活。
 * 这里按 {@code (世界, 类型)} 复用同一个影子，只改坐标。
 */
public final class GhostRuleCrossing {

    /**
     * 难度 → 交错条数：随难度递增。
     *
     * <p>和平 0（不交错）/ 简单 2 / 普通 3 / 困难 4。
     *
     * <p>要加码就整体 +1，要改单档就改这一个数 —— 这是唯一需要动的地方。
     */
    private static final Map<net.minecraft.world.Difficulty, Integer> COUNT = Map.of(
            net.minecraft.world.Difficulty.PEACEFUL, 0,
            net.minecraft.world.Difficulty.EASY, 2,
            net.minecraft.world.Difficulty.NORMAL, 3,
            net.minecraft.world.Difficulty.HARD, 4
    );

    /**
     * 可被借走的「机制」模块白名单。
     *
     * <p>不是所有模块都能往鬼身上装：
     *
     * <ul>
     *   <li>{@code door_bane} 只听 {@code item_use} —— 那是"物品被使用"，鬼用不上</li>
     *   <li>{@code scapegoat} 听 {@code owner_damaged} —— 需要"主人"，鬼没有主人</li>
     *   <li>{@code native_rule} 和 {@code crossing} 是判定本身，不能再借一层</li>
     *   <li>{@code cornered} 是拼图协同，拆开就失效</li>
     * </ul>
     *
     * <p>剩下的这三个，事件都能在鬼宿主上触发。白名单写死比"猜"安全：
     * 装错模块不会报错，只会静默不生效，排查起来极难。
     */
    private static final List<Identifier> BORROWABLE_MECHANISMS = List.of(
            com.example.smfsaddon.ghost.module.impl.StepGhostModule.ID,
            com.example.smfsaddon.ghost.module.impl.SpatialPressureModule.ID,
            com.example.smfsaddon.ghost.module.impl.DomainPresenceModule.ID
    );

    /**
     * 难度 → 借用机制个数。
     *
     * <p>比规律少：一条机制顶得上好几条判定（踩头是一击必杀），
     * 借太多会让"交错"变成"缝合怪"，玩家连它是什么鬼都认不出来。
     */
    private static final Map<net.minecraft.world.Difficulty, Integer> MECHANISM_COUNT = Map.of(
            net.minecraft.world.Difficulty.PEACEFUL, 0,
            net.minecraft.world.Difficulty.EASY, 0,
            net.minecraft.world.Difficulty.NORMAL, 1,
            net.minecraft.world.Difficulty.HARD, 2
    );

    /** 能当影子的类型（懒加载一次，之后复用） */
    private static List<Identifier> pool;

    /** 影子实例缓存：类型 → 实例（不含世界，世界变了整体清空） */
    private static final Map<Identifier, GhostEntity> SHADOWS = new HashMap<>();

    /** 造不出来的类型，记住别再试 */
    private static final Set<Identifier> UNAVAILABLE = new HashSet<>();

    /**
     * 被统计淘汰的类型。
     *
     * <h2>为什么要有这个</h2>
     *
     * <p>别的附属的鬼长什么样我们事先不知道，也不可能要求作者配合写标记。
     * 所以只能看结果：某个类型被问了很多次、一次都没命中，就说明它的判定
     * 依赖影子复现不了的东西（自己的 map、阶段状态、域内其他鬼……），
     * 下次交错不再抽它。
     *
     * <p>门槛取 200 次，是为了别把"玩家刚好没满足条件"误判成"这条是废的"。
     * 误判也不是不可逆：{@link #resetEliminated()} 或者重启服务器就清空。
     */
    private static final Set<Identifier> ELIMINATED = new HashSet<>();

    /** 判定多少次仍 0 命中就淘汰 */
    private static final int ELIMINATE_AFTER_EVALS = 200;

    /**
     * 命中统计：<b>宿主</b> → 类型 → {询问次数, 命中次数}。
     *
     * <p>早先是按类型全局累计，结果质检里三条交错永远显示同一个数
     * （161/161/161）—— 因为那是"所有借过这个类型的鬼加起来"，
     * 不是"这只鬼问了几次"。按宿主分开，才能回答"它到底用没用上"。
     *
     * <p>放内存不放 NBT：判定是每 tick 都会发生的高频事件，
     * 每次都写盘会把存档写烂。服务端停止时清空。
     */
    private static final Map<UUID, Map<Identifier, long[]>> HOST_STATS = new HashMap<>();

    /** 统计表上限，防止长跑服务器攒满 */
    private static final int HOST_STATS_LIMIT = 1024;

    /**
     * 判定结果缓存：{@code (宿主, 目标, 类型) → [过期世界时间, 结果]}。
     *
     * <h2>为什么必须缓存</h2>
     *
     * <p>本体在 tick 里对<b>每个域内玩家</b>问一次"该不该杀"，实测三秒能问上百次。
     * 每次询问都要问遍借来的类型，而其中有些类型的判定内部会扫实体
     * （门鬼、警官、敲门、垃圾这四只的 {@code shouldAttackPlayer} 里有
     * {@code getEntitiesByClass} / stream）—— 不缓存等于每 tick 做几次实体扫描。
     *
     * <p>TTL 取 10 tick（0.5 秒）：鬼的反应慢半秒玩家察觉不到，
     * 但实算次数直接降到 1/10。命中结果同样缓存，不会出现"忽打忽停"。
     */
    private static final Map<AskKey, long[]> ASK_CACHE = new HashMap<>();

    private static final int ASK_TTL_TICKS = 10;

    private static final int ASK_CACHE_LIMIT = 8192;

    /** 缓存键：谁问的、问的谁、用的谁的判定 */
    private record AskKey(UUID host, UUID target, Identifier type) {
    }

    private static World shadowWorld;

    private GhostRuleCrossing() {
    }

    public static int countFor(World world) {
        if (world == null) {
            return 0;
        }
        return COUNT.getOrDefault(world.getDifficulty(), 0);
    }

    /**
     * 提前把候选池建好。
     *
     * <p>建池要试着造几百个实体，放在"第一只鬼生成的那个 tick"里会卡一下。
     * 挪到服务端启动时做，代价是启动慢几百毫秒，换来游戏中不卡。
     */
    public static void warmUp(World world) {
        if (world != null && !world.isClient()) {
            pool(world);
        }
    }

    /**
     * 给这只鬼种上交错。
     *
     * <p>已经种过（存档里带着交错模块）就直接返回，不再重抽 ——
     * 每 tick 重抽会让规律闪烁，玩家无法推理，和装配变体是同一个道理。
     */
    public static void seed(LingyiHost host, GhostEntity ghost) {
        if (ghost == null || ghost.getWorld() == null || ghost.getWorld().isClient()) {
            return;
        }
        GhostModuleContainer modules = host.modules();
        if (modules.has(CrossingModule.ID)) {
            return;
        }
        int count = countFor(ghost.getWorld());
        if (count <= 0) {
            return;
        }

        Identifier own = Registries.ENTITY_TYPE.getId(ghost.getType());
        List<Identifier> candidates = new ArrayList<>(pool(ghost.getWorld()));
        candidates.remove(own);
        // 被淘汰的类型不再发出去 —— 池子是开服时建的，淘汰是运行时才知道的
        candidates.removeIf(GhostRuleCrossing::isDead);
        if (candidates.isEmpty()) {
            return;
        }

        List<Identifier> picked = new ArrayList<>();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int index = ghost.getRandom().nextInt(candidates.size());
            picked.add(candidates.remove(index));
        }

        try {
            GhostModuleData data = GhostModuleDispatcher.attach(host, CrossingModule.ID);
            CrossingModule.setTypes(data, picked);

            // 机制借用：规律决定"该不该杀"，机制决定"用什么手段"
            List<Identifier> mechanisms = borrowMechanisms(host, ghost, countForMechanisms(ghost.getWorld()));
            CrossingModule.setMechanisms(data, mechanisms);
            SmfsAddon.LOGGER.info("[smfsaddon] 灵异交错：{} 获得 {} 条外来规律 {}，{} 个机制 {}",
                    own, picked.size(), picked, mechanisms.size(), mechanisms);
        } catch (RuntimeException exception) {
            SmfsAddon.LOGGER.warn("[smfsaddon] 交错挂载失败：{}", exception.getMessage());
        }
    }

    /**
     * 挑几个机制装上去。
     *
     * @param want 想要几个；没装过的才候选
     */
    private static List<Identifier> borrowMechanisms(LingyiHost host, GhostEntity ghost, int want) {
        List<Identifier> result = new ArrayList<>();
        if (want <= 0) {
            return result;
        }
        List<Identifier> candidates = new ArrayList<>();
        for (Identifier moduleId : BORROWABLE_MECHANISMS) {
            // 装配表已经给过的就不算"借"
            if (!host.modules().has(moduleId)) {
                candidates.add(moduleId);
            }
        }
        for (int i = 0; i < want && !candidates.isEmpty(); i++) {
            Identifier picked = candidates.remove(ghost.getRandom().nextInt(candidates.size()));
            try {
                GhostModuleDispatcher.attach(host, picked);
                result.add(picked);
            } catch (RuntimeException exception) {
                // 能力不匹配就跳过，别拖垮整只鬼的交错
                SmfsAddon.LOGGER.warn("[smfsaddon] 借用机制 {} 失败：{}", picked, exception.getMessage());
            }
        }
        return result;
    }

    public static int countForMechanisms(World world) {
        if (world == null) {
            return 0;
        }
        return MECHANISM_COUNT.getOrDefault(world.getDifficulty(), 0);
    }

    /**
     * 问一只「别人家的鬼」：这个人该不该死。
     */
    public static boolean ask(Identifier typeId, GhostEntity host, PlayerEntity target) {
        World world = host.getWorld();
        if (world == null || target == null
                || UNAVAILABLE.contains(typeId) || ELIMINATED.contains(typeId)) {
            return false;
        }
        if (shadowWorld != world) {
            SHADOWS.clear();
            ASK_CACHE.clear();
            shadowWorld = world;
        }

        // ① 先看缓存：0.5 秒内的重复询问直接回答
        long now = world.getTime();
        AskKey key = new AskKey(host.getUuid(), target.getUuid(), typeId);
        long[] cached = ASK_CACHE.get(key);
        if (cached != null && now < cached[0]) {
            boolean verdict = cached[1] != 0L;
            record(host.getUuid(), typeId, verdict, false);
            return verdict;
        }

        GhostEntity shadow = SHADOWS.get(typeId);
        if (shadow == null) {
            EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
            if (type == null) {
                UNAVAILABLE.add(typeId);
                return false;
            }
            Entity created;
            try {
                created = type.create(world);
            } catch (RuntimeException exception) {
                UNAVAILABLE.add(typeId);
                return false;
            }
            if (!(created instanceof GhostEntity ghostShadow)) {
                UNAVAILABLE.add(typeId);
                return false;
            }
            SHADOWS.put(typeId, ghostShadow);
            shadow = ghostShadow;
        }

        try {
            // 影子摆到宿主所在位置，让鬼自己的坐标判断成立
            shadow.setPosition(host.getX(), host.getY(), host.getZ());
            boolean verdict = shadow.shouldAttackPlayer(target);
            if (ASK_CACHE.size() > ASK_CACHE_LIMIT) {
                ASK_CACHE.clear();
            }
            ASK_CACHE.put(key, new long[]{now + ASK_TTL_TICKS, verdict ? 1L : 0L});
            record(host.getUuid(), typeId, verdict, true);
            return verdict;
        } catch (RuntimeException exception) {
            // 单个类型的判定炸了不该拖垮整只鬼
            UNAVAILABLE.add(typeId);
            return false;
        }
    }

    /**
     * @param evaluated 是否真的算了一次（false = 命中缓存）
     */
    private static void record(UUID hostId, Identifier typeId, boolean hit, boolean evaluated) {
        if (hostId == null) {
            return;
        }
        if (HOST_STATS.size() > HOST_STATS_LIMIT) {
            HOST_STATS.clear();
        }
        long[] stat = HOST_STATS
                .computeIfAbsent(hostId, ignored -> new HashMap<>())
                .computeIfAbsent(typeId, ignored -> new long[3]);
        stat[0]++;
        if (evaluated) {
            stat[1]++;
        }
        if (hit) {
            stat[2]++;
        }
        // 问了够多次还一次没命中 → 这条判定影子复现不了，以后别再抽它
        if (stat[1] >= ELIMINATE_AFTER_EVALS && stat[2] == 0 && ELIMINATED.add(typeId)) {
            SmfsAddon.LOGGER.info("[smfsaddon] 交错淘汰 {}：判定 {} 次 0 命中，判定依赖自身状态",
                    typeId, stat[1]);
        }
    }

    /** 是否已被判为"借了也没用"（造不出影子，或长期 0 命中） */
    public static boolean isDead(Identifier typeId) {
        return UNAVAILABLE.contains(typeId) || ELIMINATED.contains(typeId);
    }

    /** 清空淘汰名单，给调参留的后悔药 */
    public static int resetEliminated() {
        int count = ELIMINATED.size();
        ELIMINATED.clear();
        return count;
    }

    /** 世界卸载时清空，别让 ServerWorld 被静态表攥住 */
    public static void clearCache() {
        SHADOWS.clear();
        UNAVAILABLE.clear();
        ELIMINATED.clear();
        HOST_STATS.clear();
        ASK_CACHE.clear();
        shadowWorld = null;
    }

    /**
     * 这只鬼对某个类型的统计 {@code [询问次数, 实算次数, 命中次数]}；没统计过返回 {@code null}。
     */
    public static long[] statsOf(UUID hostId, Identifier typeId) {
        if (hostId == null) {
            return null;
        }
        Map<Identifier, long[]> perHost = HOST_STATS.get(hostId);
        return perHost == null ? null : perHost.get(typeId);
    }

    /**
     * 造不出影子的类型（判定必然为 false，等于白借）。
     *
     * <p>质检命令用它来指出"这条交错是废的"，否则玩家只会觉得"交错没工作"。
     */
    public static Set<Identifier> unavailableTypes() {
        return Set.copyOf(UNAVAILABLE);
    }

    /**
     * 不参战的类型：名字里带这些词的，`shouldAttackPlayer` 多半恒为 false，
     * 借来只是白占名额（实测抽到过融合流程鬼、商人和梦魇）。
     */
    private static final List<String> POOL_BLACKLIST = List.of(
            "fusion", "nightmare", "seller", "slave"
    );

    /**
     * 候选池：能造出 {@code GhostEntity} 的类型。
     *
     * <p>先按名字粗筛再实际试造 —— 全量试造几百个实体太浪费。
     * 粗筛规则：路径以 {@code _ghost} 结尾、以 {@code ghost_} 开头，或命名空间是 smfs。
     */
    private static List<Identifier> pool(World world) {
        if (pool != null) {
            return pool;
        }
        List<Identifier> list = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            String path = id.getPath();
            boolean likely = path.endsWith("_ghost")
                    || path.startsWith("ghost_")
                    || "smfs".equals(id.getNamespace());
            if (!likely) {
                continue;
            }
            boolean blocked = false;
            for (String bad : POOL_BLACKLIST) {
                if (path.contains(bad)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                continue;
            }
            EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            if (type == null) {
                continue;
            }
            // 不能召唤的多半是流程/演出实体，拉进池子只会白借
            if (!type.isSummonable()) {
                continue;
            }
            try {
                if (type.create(world) instanceof GhostEntity ghost) {
                    ghost.discard();
                    list.add(id);
                }
            } catch (RuntimeException ignored) {
                // 造不出来的类型不进池子
            }
        }
        pool = List.copyOf(list);
        SmfsAddon.LOGGER.info("[smfsaddon] 交错候选池：{} 种鬼", pool.size());
        return pool;
    }
}
