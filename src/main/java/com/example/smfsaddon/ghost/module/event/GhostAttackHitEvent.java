package com.example.smfsaddon.ghost.module.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 玩家用某件物品攻击命中了目标。
 *
 * <p>和 {@link GhostItemUseEvent} 是一对：一个管"用"，一个管"打"。
 * 规律移植到物品上之后，「谁被打了」只能从这类事件里知道 ——
 * 物品自己不会选目标。
 */
public final class GhostAttackHitEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "attack_hit");

    private final PlayerEntity attacker;
    private final LivingEntity target;

    public GhostAttackHitEvent(PlayerEntity attacker, LivingEntity target) {
        this.attacker = attacker;
        this.target = target;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public PlayerEntity player() {
        return attacker;
    }

    public LivingEntity target() {
        return target;
    }
}
