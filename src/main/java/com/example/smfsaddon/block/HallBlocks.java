package com.example.smfsaddon.block;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * 「席面」的三个装饰方块。
 *
 * <h2>为什么用方块而不是实体</h2>
 *
 * <p>礼堂做成实体时会把玩家推开，而且本体鬼那套物理关不掉（六种开关都试过）。</p>
 *
 * <p>方块没有这个问题：<b>noCollision</b> 之后玩家可以穿过去，
 * 同时它又能吃到原版光照 —— 灯笼是真的光源，能把影子打在木梁上，
 * 这是实体模型做不到的。</p>
 */
public final class HallBlocks {

    public static Block HALL_PILLAR;
    public static Block HALL_DRAPE;
    public static Block HALL_LANTERN;
    /** 薄红毯：1 像素厚，铺在脚底那一层，不改动地面、不会让玩家掉下去 */
    public static Block HALL_CARPET;
    /** 瓦（屋顶）与墙板 */
    public static Block HALL_ROOF;
    public static Block HALL_WALL;

    private HallBlocks() {
    }

    public static void register() {
        HALL_PILLAR = register("hall_pillar",
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.BROWN)
                        .strength(0.4F)
                        .noCollision()
                        .sounds(BlockSoundGroup.WOOD)
                        .pistonBehavior(PistonBehavior.DESTROY));

        HALL_DRAPE = register("hall_drape",
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.DARK_RED)
                        .strength(0.4F)
                        .noCollision()
                        .nonOpaque()
                        .sounds(BlockSoundGroup.WOOL)
                        .pistonBehavior(PistonBehavior.DESTROY));

        HALL_LANTERN = register("hall_lantern",
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.GOLD)
                        .strength(0.4F)
                        .noCollision()
                        .nonOpaque()
                        .luminance(state -> 15)          // 真光源
                        .sounds(BlockSoundGroup.LANTERN)
                        .pistonBehavior(PistonBehavior.DESTROY));

        HALL_CARPET = register("hall_carpet",
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.DARK_RED)
                        .strength(0.2F)
                        .noCollision()
                        .nonOpaque()
                        .sounds(BlockSoundGroup.WOOL)
                        .pistonBehavior(PistonBehavior.DESTROY));

        HALL_ROOF = register("hall_roof",
                AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_BLACK).strength(0.6F)
                        .noCollision().sounds(BlockSoundGroup.DEEPSLATE_TILES)
                        .pistonBehavior(PistonBehavior.DESTROY));
        HALL_WALL = register("hall_wall",
                AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.5F)
                        .noCollision().sounds(BlockSoundGroup.WOOD)
                        .pistonBehavior(PistonBehavior.DESTROY));
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        return Registry.register(Registries.BLOCK,
                new Identifier(SmfsAddon.MOD_ID, name), new Block(settings));
    }
}
