package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.event.GhostEvent;
import net.minecraft.util.Identifier;

/**
 * 元规则：凌驾于普通规则之上的一票否决。
 *
 * <p>为什么需要单独一层：有些规则不属于「满足条件就触发」，
 * 而是<b>无视其他一切规则</b>。原著里的必死诅咒就是典型 ——
 * 只要条件成立，不管你身上还挂着多少条规则，结果都是死。
 *
 * <p>用普通 {@link GhostRule} 表达这类逻辑会出问题：
 * 它要能中断同事件里其他所有规则的执行，而普通规则只能决定自己。
 *
 * <p>执行顺序：先评所有元规则，任一否决则本次事件直接结束，
 * 普通规则全部不执行。
 */
public interface GhostMetaRule {

    /**
     * 元规则唯一标识。
     */
    Identifier id();

    /**
     * 是否否决本次事件。
     *
     * <p>返回 {@code true} 时，本模块的普通规则不再执行。
     * 只应做判断，不应修改世界状态。
     */
    boolean vetoes(GhostModuleContext context, GhostEvent event);
}
