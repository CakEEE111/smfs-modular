package com.example.smfsaddon.ghost.module.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 语义事件。
 *
 * <p>这里的「事件」不是 Fabric 的加载器事件，而是面向规则的
 * 语义抽象：把「玩家靠近」「鬼被攻击」「该不该攻击这个玩家」
 * 这类与玩法有关的事情，从具体实现里抽出来，
 * 让规则只关心语义，不关心它是从 tick 还是从 mixin 里派发出来的。
 */
public interface GhostEvent {

    /**
     * 事件类型标识，规则用它与 {@code GhostRule#supports} 比对。
     */
    Identifier id();

    /**
     * 与该事件相关的玩家，没有则返回 {@code null}。
     *
     * <p>图鉴依赖它回答「是谁观察到了这条规律」。
     * 没有玩家的事件（纯 tick）不产生观察记录 ——
     * 没有旁观者，就谈不上被观察到。
     */
    default PlayerEntity player() {
        return null;
    }
}
