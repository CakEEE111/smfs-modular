package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.GhostEntityHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;

import java.util.Set;

/**
 * 只对鬼有意义的模块。
 *
 * <p>用到鬼等级、鬼域、攻击冷却这类概念时用这一档 ——
 * 这些属性在道具和玩家身上不存在。
 *
 * <p>名字刻意叫「GhostOnly」而不是「Ghost」，是为了提醒：
 * 选了这一档，这个插件就再也装不到道具上了。
 * 如果只是需要位置或生命体，用 {@link LivingModule} 更值。
 */
public interface GhostOnlyModule extends GhostModule {

    @Override
    default Set<Class<? extends HostCapability>> requires() {
        return Set.of(GhostEntityHost.class);
    }
}
