package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.HostCapability;

import java.util.Optional;

/**
 * 灵异宿主。
 *
 * <p>「万物插件化」的接口面：宿主不声明自己是什么（鬼 / 道具 / 方块 / 玩家），
 * 只声明自己<b>能做什么</b>。规则按能力装载，而不是按类名判断。
 *
 * <p>这样同一份规则代码：
 * <ul>
 *   <li>挂到鬼实体上，就是会走路的厉鬼；</li>
 *   <li>挂到物品上，就是灵异道具；</li>
 *   <li>挂到玩家身上，就是技能。</li>
 * </ul>
 *
 * <p>宿主必须承诺的只有两件事：模块容器，以及按能力查询。
 * 其余（tick、持久化、同步）都在 {@link HostCapability} 子接口里按需声明。
 */
public interface LingyiHost {

    /**
     * 按类型查询宿主能力。
     *
     * <p>实现应当零分配 —— 通常就是 {@code type.isInstance(this)} 之后转型。
     */
    <T extends HostCapability> Optional<T> capability(Class<T> type);

    /**
     * 宿主携带的模块集合。
     */
    GhostModuleContainer modules();

    /**
     * 宿主是否允许规则修改模块的复苏值。
     */
    default boolean allowsRevivalStimulation() {
        return true;
    }

    /**
     * 宿主是否满足该模块的能力需求。
     *
     * <p>这是「插件装载」的唯一判据。不满足时模块不会被调度，
     * 也不应该被挂载。
     */
    default boolean supportsCapabilities(GhostModule module) {
        for (Class<? extends HostCapability> required : module.requires()) {
            if (capability(required).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
