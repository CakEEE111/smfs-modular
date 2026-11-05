package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.HostCapability;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Set;

/**
 * 厉鬼模块定义。
 *
 * <p>一个模块 = 身份 + 初始属性 + 一组规则 + 一组能力。
 *
 * <p>模块负责「声明」，不负责「持有状态」：
 * 模块实现应当是无状态的单例（在 {@link GhostModuleRegistry} 中注册一次），
 * 真正的运行时状态（灵异强度、复苏值、自定义 NBT）放在
 * {@link GhostModuleData} 里，由宿主持有。
 *
 * <p>这样同一个模块可以被任意数量的鬼共享，
 * 不会出现「类静态字段互相污染」的问题。
 */
public interface GhostModule {

    /**
     * 模块唯一标识，建议使用 {@code smfs:xxx} 形式。
     */
    Identifier id();

    /**
     * 给人看的名字。
     *
     * <p>模块 ID 是给存档和注册表用的（一旦发布不能改），
     * 但界面上必须显示人话 —— 否则玩家看到 {@code door_bane} 等于什么都没看到。
     */
    default String displayName() {
        return id().getPath();
    }

    /**
     * 本模块需要宿主具备哪些能力。
     *
     * <p>这是「插件装载」的唯一判据，也是拼图咬合的落地方式：
     * 需要 {@code GhostEntityHost} 的模块装不到道具上，
     * 需要 {@code TickHost} 的模块装不到没有心跳的宿主上。
     *
     * <p>默认不要求任何能力 —— 意味着它能在任何宿主上跑。
     * 但真正这么写的模块很少，多数模块至少需要空间信息。
     */
    default Set<Class<? extends HostCapability>> requires() {
        return Set.of();
    }

    /**
     * 为新挂载的模块实例生成初始数据。
     */
    default GhostModuleData createData() {
        return GhostModuleData.initial(id(), 1.0D, 0.0D);
    }

    /**
     * 压制这条规律所需的「灵异值」。
     *
     * <p>用于灵异装置：玩家把带模块的物品放进去，需要用自己的灵异值压住这些规律。
     * 压得住就无视反噬，压不住就会被反噬。
     *
     * <p>默认 30，按规律的危险程度自行上调。越强的规律应该越贵。
     */
    default double suppressionCost() {
        return 30.0D;
    }

    /**
     * 自然复苏速度，单位：复苏值 / 秒。
     *
     * <p>返回 0 表示该模块不会自然复苏。
     */
    default double naturalRevivalRate() {
        return 1.0D;
    }

    /**
     * 复苏值上限。
     */
    default double maxRevival() {
        return 100.0D;
    }

    /**
     * 邻近玩家扫描间隔，单位为 tick。
     *
     * <p>返回 0 表示本模块不关心邻近玩家，引擎不会为其做扫描，
     * 这是默认值 —— 避免每只鬼每 tick 遍历玩家列表。
     */
    default int nearbyScanInterval() {
        return 0;
    }

    /**
     * 邻近玩家扫描半径，仅在 {@link #nearbyScanInterval()} 大于 0 时生效。
     */
    default double nearbyScanRadius() {
        return 16.0D;
    }

    /**
     * 本模块提供的元规则。
     *
     * <p>元规则先于普通规则评估，任一否决则本次事件的普通规则全部跳过。
     * 用于表达「无视其他规则」的绝对规则，例如必死诅咒。
     */
    default List<GhostMetaRule> metaRules() {
        return List.of();
    }

    /**
     * 本模块提供的规则。
     */
    default List<GhostRule> rules() {
        return List.of();
    }

    /**
     * 本模块提供的主动能力。
     */
    default List<GhostAbility> abilities() {
        return List.of();
    }

    /**
     * 模块被挂载到宿主时调用一次。
     */
    default void onAttach(GhostModuleContext context) {
    }

    /**
     * 模块从宿主上移除时调用一次。
     */
    default void onDetach(GhostModuleContext context) {
    }
}
