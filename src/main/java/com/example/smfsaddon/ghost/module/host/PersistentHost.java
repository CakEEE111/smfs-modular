package com.example.smfsaddon.ghost.module.host;

import net.minecraft.nbt.NbtCompound;

/**
 * 宿主提供持久化槽位。
 *
 * <p>插件在宿主上存的东西，最终都要落到宿主的存档格式里：
 * 实体存实体 NBT、道具存物品 NBT、方块存方块实体 NBT、玩家存玩家数据。
 * 这四套格式完全不同，插件不该各自处理。
 *
 * <p>由宿主提供统一槽位，插件只管把 {@link #state()} 当成自己的抽屉。
 *
 * <p>注意：鬼实体目前<b>不</b>实现本接口 —— 它的模块数据由
 * {@code GhostEntityModuleMixin} 统一写进实体 NBT，插件用
 * {@code context.state()} 即可，不需要再要一个槽位。
 * 本接口是为道具、方块这类新宿主预留的。
 */
public interface PersistentHost extends HostCapability {

    /**
     * 插件可读写的持久化抽屉。修改后需调用 {@link #markDirty()}。
     */
    NbtCompound state();

    /**
     * 标记数据已变更，等待宿主写盘。
     */
    void markDirty();
}
