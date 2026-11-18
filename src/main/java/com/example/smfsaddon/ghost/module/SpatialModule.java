package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.SpatialHost;

import java.util.Set;

/**
 * 只要求「有位置」的模块。
 *
 * <p>最宽的一档：能挂在任何有空间位置的宿主上 —— 鬼、道具、方块、玩家都行。
 * 想不出该用哪档时，先试着用这个，写不出来再往上加能力。
 */
public interface SpatialModule extends GhostModule {

    @Override
    default Set<Class<? extends HostCapability>> requires() {
        return Set.of(SpatialHost.class);
    }
}
