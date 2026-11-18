package com.example.smfsaddon.ghost.module.host;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 有位置和世界。
 *
 * <p>最基本的空间能力。绝大多数规则只需要这一个 ——
 * 「离玩家多近」「脚下是什么方块」这类判定不关心宿主是鬼还是道具。
 */
public interface SpatialHost extends HostCapability {

    World world();

    Vec3d position();
}
