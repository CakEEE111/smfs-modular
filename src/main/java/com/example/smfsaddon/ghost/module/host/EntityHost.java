package com.example.smfsaddon.ghost.module.host;

import net.minecraft.entity.LivingEntity;

/**
 * 有生命体的宿主。
 *
 * <p>比 {@link SpatialHost} 多一个「活物」身份：能受伤、能被攻击、有朝向。
 * 鬼、玩家、傀儡都满足；道具和方块不满足。
 *
 * <p>规则如果只是要位置，就用 {@link SpatialHost} —— 别顺手写 EntityHost，
 * 那会把规则白白锁死在「必须挂在活物上」。
 */
public interface EntityHost extends SpatialHost {

    LivingEntity living();
}
