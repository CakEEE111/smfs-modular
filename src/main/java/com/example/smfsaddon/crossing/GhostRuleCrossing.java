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
    private static final Map<net.minecraft.world.Difficulty, Integer> COUNT = new java.util.EnumMap<>(
            net.minecraft.world.Difficulty.class);

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
            com.example.smfsaddon.ghost.module.impl.DomainPresenceModule.ID,
            // 环境类：判定只读玩家侧状态，换成任何宿主都能生效
            com.example.smfsaddon.ghost.module.impl.FacingAwayModule.ID,
            com.example.smfsaddon.ghost.module.impl.LookUpModule.ID,
            com.example.smfsaddon.ghost.module.impl.LookDownModule.ID,
            com.example.smfsaddon.ghost.module.impl.WaterGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.LowLightModule.ID,
            com.example.smfsaddon.ghost.module.impl.CropGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.MineralGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.SneakGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.StationaryGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.DirectSightModule.ID,
            com.example.smfsaddon.ghost.module.impl.ContainerGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.BareArmorModule.ID,
            com.example.smfsaddon.ghost.module.impl.EatingGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.ContinuousMoveModule.ID,
            com.example.smfsaddon.ghost.module.impl.BurningGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.JumpGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.PlagueGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.TrashGateModule.ID,
            com.example.smfsaddon.ghost.module.impl.MoneyGateModule.ID
    );

    /**
     * 难度 → 借用机制个数。
     *
     * <p>比规律少：一条机制顶得上好几条判定（踩头是一击必杀），
     * 借太多会让"交错"变成"缝合怪"，玩家连它是什么鬼都认不出来。
     */
    private static final Map<net.minecraft.world.Difficulty, Integer> MECHANISM_COUNT = new java.util.EnumMap<>(
            net.minecraft.world.Difficulty.class);

    /** 能当影子的类型（懒加载一次，之后复用） */
    private static List<Identifier> pool;

    /** 影子实例缓存：类型 → 实例（不含世界，世界变了整体清空） */
    private static final Map<Identifier, GhostEntity> SHADOWS = new HashMap<>();

    /** 造不出来的类型，记住别再试 */
    private static final Set<Identifier> UNAVAILABLE = new HashSet<>();

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
     * 交错出手冷却：同一只鬼对同一个玩家，最多每 40 tick 生效一次。
     *
     * <h2>为什么交错必须自带冷却</h2>
     *
     * <p>本体的出手节奏藏在<b>每只鬼自己的</b> {@code shouldAttackPlayer} 里
     * （开头几乎都有一句 {@code if (attackCooldown > 0) return false}），
     * 而 {@code resolveKillingRule} 是三态短路：
     *
     * <pre>
     * ALLOW  → 直接返回 true，<b>不再问鬼自己的规律</b>
     * DENY   → 直接返回 false
     * ABSTAIN→ 才轮到 entity.shouldAttackPlayer(player)
     * </pre>
     *
     * <p>交错一旦 allow，那道冷却闸就被整个跳过 —— 只要借来的规律此刻成立，
     * 每个 tick 都会执行一次袭击（一秒 20 下）。所以这里自己补一道闸：
     * 冷却期内<b>不表态</b>，让本体照常去问鬼自己的规律，原版节奏就回来了。
     */
    private static int ALLOW_COOLDOWN_TICKS = 40;

    /** 宿主 → 目标 → 上次允许的 tick */
    private static final Map<UUID, Map<UUID, Long>> ALLOW_AT = new HashMap<>();

    private static final int ALLOW_CACHE_LIMIT = 1024;

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
        if (world == null || target == null || UNAVAILABLE.contains(typeId)) {
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
    }

    /**
     * 交错的总闸：这次允许能不能放行。
     *
     * @return {@code false} 表示冷却未到，调用方应当<b>不表态</b>（回到 ABSTAIN）
     */
    public static boolean tryConsumeAllow(UUID hostId, UUID targetId, long now) {
        if (hostId == null || targetId == null) {
            return true;
        }
        if (ALLOW_AT.size() > ALLOW_CACHE_LIMIT) {
            ALLOW_AT.clear();
        }
        Map<UUID, Long> perTarget = ALLOW_AT.computeIfAbsent(hostId, ignored -> new HashMap<>());
        Long last = perTarget.get(targetId);
        if (last != null && now - last < ALLOW_COOLDOWN_TICKS) {
            return false;
        }
        perTarget.put(targetId, now);
        return true;
    }

    /** 世界卸载时清空，别让 ServerWorld 被静态表攥住 */
    /**
     * 由配置写入难度档位与出手冷却。
     */
    public static void applyConfig(int peacefulRules, int easyRules, int normalRules, int hardRules,
                                   int peacefulMechanisms, int easyMechanisms,
                                   int normalMechanisms, int hardMechanisms,
                                   int allowCooldownTicks) {
        COUNT.clear();
        COUNT.put(net.minecraft.world.Difficulty.PEACEFUL, Math.max(0, peacefulRules));
        COUNT.put(net.minecraft.world.Difficulty.EASY, Math.max(0, easyRules));
        COUNT.put(net.minecraft.world.Difficulty.NORMAL, Math.max(0, normalRules));
        COUNT.put(net.minecraft.world.Difficulty.HARD, Math.max(0, hardRules));

        MECHANISM_COUNT.clear();
        MECHANISM_COUNT.put(net.minecraft.world.Difficulty.PEACEFUL, Math.max(0, peacefulMechanisms));
        MECHANISM_COUNT.put(net.minecraft.world.Difficulty.EASY, Math.max(0, easyMechanisms));
        MECHANISM_COUNT.put(net.minecraft.world.Difficulty.NORMAL, Math.max(0, normalMechanisms));
        MECHANISM_COUNT.put(net.minecraft.world.Difficulty.HARD, Math.max(0, hardMechanisms));

        ALLOW_COOLDOWN_TICKS = Math.max(0, allowCooldownTicks);
    }

    public static void clearCache() {
        SHADOWS.clear();
        UNAVAILABLE.clear();
        HOST_STATS.clear();
        ASK_CACHE.clear();
        ALLOW_AT.clear();
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
    /**
     * 可被当作"鬼"处理的实体类型。
     *
     * <p>给指令补全用：<b>只列鬼</b>，不要把僵尸、牛、村民也塞进列表 ——
     * 那些东西既不能被污染进方块，也不会被交错。
     */
    public static List<Identifier> ghostTypeIds(World world) {
        if (world == null) {
            return List.of();
        }
        return pool(world);
    }

    /**
     * 该命名空间对应的模组是否声明了 smfs 依赖。
     *
     * <p>附属的命名空间不一定等于模组 id，但绝大多数是一致的；
     * 对不上时只是少收几个候选，不会出错。
     */
    private static boolean namespaceDependsOnSmfs(String namespace) {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(namespace)
                    .map(container -> container.getMetadata().getDependencies().stream()
                            .anyMatch(dependency -> "smfs".equals(dependency.getModId())))
                    .orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<Identifier> pool(World world) {
        if (pool != null) {
            return pool;
        }
        List<Identifier> list = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            String path = id.getPath();
            // 判据放了三层，从便宜到贵：
            //   1) 名字里带 ghost —— 覆盖 *_ghost / ghost_* / 各种驼峰写法
            //   2) 本体自己的命名空间
            //   3) 该命名空间对应的模组声明了 smfs 依赖 —— 覆盖"给鬼起了个
            //      跟 ghost 无关的名字"的附属（比如 otheraddon:weeping_woman）
            //
            // 早先只认「_ghost 结尾 / ghost_ 开头」，第三种鬼会被整只漏掉：
            // 它自己该拿的交错不会少，但别的鬼永远借不到它 —— 玩家会觉得
            // "这明明也是鬼，怎么从没被借过"。
            boolean likely = path.contains("ghost")
                    || "smfs".equals(id.getNamespace())
                    || namespaceDependsOnSmfs(id.getNamespace());
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
        SmfsAddon.LOGGER.info("[smfsaddon] 交错候选池就绪：{} 只鬼可被借用", pool.size());
        SmfsAddon.LOGGER.info("[smfsaddon] 交错候选池：{} 种鬼", pool.size());
        return pool;
    }
}
