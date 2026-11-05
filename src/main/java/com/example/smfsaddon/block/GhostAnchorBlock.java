package com.example.smfsaddon.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 灵异锚点方块。
 *
 * <p>它存在的唯一目的是**验证宿主抽象**：一个没有身体、不会走路、
 * 但同样能承载厉鬼规律的宿主。
 *
 * <p>如果这个方块能挂上和鬼一样的模块并正常运转，
 * 就说明「插件」这层是真的，而不是只对鬼成立。
 */
public class GhostAnchorBlock extends BlockWithEntity {

    public GhostAnchorBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GhostAnchorBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /**
     * 方块本身没有生命，必须由方块实体主动驱动。
     *
     * <p>这正是 {@code TickHost} 存在的理由：物品和方块不会自己 tick，
     * 插件不能假设「心跳」是免费拿到的。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return (tickWorld, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof GhostAnchorBlockEntity anchor) {
                anchor.serverTick();
            }
        };
    }
}
