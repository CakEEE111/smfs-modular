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
     * 难度 → 交错条数。
     *
     * <p>按设计给的数：和平 0 / 简单 3 / 普通 2 / 困难 4。
     * （简单比普通多，若这是笔误，对调 EASY 与 NORMAL 两个数即可。）
     */
    private static final Map<net.minecraft.world.Difficulty, Integer> COUNT = Map.of(
            net.minecraft.world.Difficulty.PEACEFUL, 0,
            net.minecraft.world.Difficulty.EASY, 3,
            net.minecraft.world.Difficulty.NORMAL, 2,
            net.minecraft.world.Difficulty.HARD, 4
    );

    /** 能当影子的类型（懒加载一次，之后复用） */
    private static List<Identifier> pool;

    /** 影子实例缓存：类型 → 实例（不含世界，世界变了整体清空） */
    private static final Map<Identifier, GhostEntity> SHADOWS = new HashMap<>();

    /** 造不出来的类型，记住别再试 */
    private static final Set<Identifier> UNAVAILABLE = new HashSet<>();

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
        } catch (RuntimeException exception) {
            SmfsAddon.LOGGER.warn("[smfsaddon] 交错挂载失败：{}", exception.getMessage());
            return;
        }
        SmfsAddon.LOGGER.info("[smfsaddon] 灵异交错：{} 获得 {} 条外来规律 {}",
                own, picked.size(), picked);
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
            shadowWorld = world;
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
            return shadow.shouldAttackPlayer(target);
        } catch (RuntimeException exception) {
            // 单个类型的判定炸了不该拖垮整只鬼
            UNAVAILABLE.add(typeId);
            return false;
        }
    }

    /** 世界卸载时清空，别让 ServerWorld 被静态表攥住 */
    public static void clearCache() {
        SHADOWS.clear();
        UNAVAILABLE.clear();
        shadowWorld = null;
    }

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
            EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(id).orElse(null);
            if (type == null) {
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
