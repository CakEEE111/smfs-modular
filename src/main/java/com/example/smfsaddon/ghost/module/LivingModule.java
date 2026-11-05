package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.EntityHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;

import java.util.Set;

/**
 * 要求「活物」宿主的模块。
 *
 * <p>比 {@link SpatialModule} 多一个生命体身份：能受伤、能被攻击。
 * 鬼和玩家满足，道具和方块不满足。
 *
 * <p>典型用途：追人、逃离、受伤后变化 —— 这些都需要一个会动的身体。
 */
public interface LivingModule extends GhostModule {

    @Override
    default Set<Class<? extends HostCapability>> requires() {
        return Set.of(EntityHost.class);
    }
}
