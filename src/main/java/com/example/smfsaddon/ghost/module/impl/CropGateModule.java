package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 践踏庄稼必杀：玩家踩在农田里（且没穿金靴），它就下手。
 *
 * <p>本体「庄稼鬼」的规律。金靴豁免那一条也保留 ——
 * 那是这条规律的"破解方式"，去掉就等于废掉玩家的应对手段。
 */
public final class CropGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "crop_gate");

    public CropGateModule() {
        super(ID, "践踏庄稼", "crop_gate/trample", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                              GhostEntity self, World world) {
        if (player.getInventory().armor.get(0).getItem() == Items.GOLDEN_BOOTS) {
            return false;
        }
        BlockPos feet = player.getBlockPos();
        return world.getBlockState(feet.down()).getBlock() instanceof FarmlandBlock
                || world.getBlockState(feet).getBlock() instanceof CropBlock;
    }

    @Override
    protected String condition() {
        return "踩进农田的人，它才动手（金靴除外）";
    }
}
