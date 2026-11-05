package com.example.smfsaddon.ghost.module.event;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 玩家使用物品。
 *
 * <p>厉鬼的规律本来都建立在「鬼主动害人」上（靠近、袭击、受伤）。
 * 但当规律被移植进物品，触发方式就变成了「玩家拿着它做了什么」——
 * 用剑开门、用刀砍人、拿起符纸。
 *
 * <p>这个事件就是那个转轴：同一个 {@code GhostRule}，
 * 挂在鬼身上时响应 {@code tick}，移植到物品上时响应 {@code item_use}。
 */
public final class GhostItemUseEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "item_use");

    private final PlayerEntity player;
    private final ItemStack stack;
    private final BlockPos pos;
    private final BlockState state;

    public GhostItemUseEvent(PlayerEntity player, ItemStack stack, BlockPos pos, BlockState state) {
        this.player = player;
        this.stack = stack;
        this.pos = pos;
        this.state = state;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public PlayerEntity player() {
        return player;
    }

    public ItemStack stack() {
        return stack;
    }

    /**
     * 被作用方块的坐标，对着空气使用时为 {@code null}。
     */
    public BlockPos pos() {
        return pos;
    }

    /**
     * 被作用方块的状态，对着空气使用时为 {@code null}。
     */
    public BlockState state() {
        return state;
    }
}
