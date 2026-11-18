package com.example.smfsaddon.ghost.module.host;

/**
 * 宿主心跳。
 *
 * <p>这是最容易漏掉的一条能力。鬼实体和玩家天然每 tick 都在跑，
 * 但<b>物品和方块没有 tick</b>。插件一旦挂到道具上，
 * 周期性逻辑就没有人推动它。
 *
 * <p>所以「心跳」必须是宿主承诺提供的东西，而不是插件假设存在的东西。
 * 道具宿主可以用持有者的 tick、方块宿主可以用方块 tick 来实现。
 */
public interface TickHost extends HostCapability {

    /**
     * 宿主存活至今的 tick 数。
     *
     * <p>用于节流、心跳计数。不要拿它当世界时间 ——
     * 新生成的宿主会从 0 开始，跨宿主比较会出错。
     */
    long ticks();
}
