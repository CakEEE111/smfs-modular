package com.example.smfsaddon.block;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 非实体宿主方块。
 *
 * <p>刻意只注册一个方块：目的不是内容，而是验证「插件能不能挂在没有身体的东西上」。
 * 贴图直接借原版方块，不引入任何美术资源 —— 免得把验证和美术混在一起。
 */
public final class ModHostBlocks {

    public static Block GHOST_ANCHOR;
    public static BlockEntityType<GhostAnchorBlockEntity> GHOST_ANCHOR_ENTITY;

    private ModHostBlocks() {
    }

    public static void register() {
        Identifier id = new Identifier(SmfsAddon.MOD_ID, "ghost_anchor");

        GHOST_ANCHOR = new GhostAnchorBlock(
                AbstractBlock.Settings.copy(Blocks.POLISHED_DEEPSLATE).strength(2.5F)
        );
        Registry.register(Registries.BLOCK, id, GHOST_ANCHOR);
        Registry.register(Registries.ITEM, id, new BlockItem(GHOST_ANCHOR, new Item.Settings()));

        GHOST_ANCHOR_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id,
                FabricBlockEntityTypeBuilder.create(GhostAnchorBlockEntity::new, GHOST_ANCHOR).build()
        );

        SmfsAddon.LOGGER.info("[smfsaddon] 灵异锚点已注册（非实体宿主验证方块）");
    }
}
