package com.example.smfsaddon.ghost.module.host;

import com.xie.smfs.entity.GhostEntity;

/**
 * 鬼专用宿主能力。
 *
 * <p>有些规则确实只对鬼有意义 —— 比如读 {@code ghostLevel}、鬼域层数、
 * 攻击冷却。这些概念在道具和玩家身上根本不存在，硬塞进通用能力里
 * 会让接口变得又大又空。
 *
 * <p>所以宁可多一层：需要鬼特性的规则声明 {@link GhostEntityHost}，
 * 于是它天然装不到道具上。这不是限制，这是**正确的装载约束** ——
 * 也正是「拼图咬合」的实现方式。
 */
public interface GhostEntityHost extends EntityHost {

    GhostEntity ghost();
}
