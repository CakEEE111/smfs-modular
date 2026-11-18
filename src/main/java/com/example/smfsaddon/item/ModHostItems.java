package com.example.smfsaddon.item;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 非实体宿主道具。
 *
 * <p>和 `ghost_anchor` 方块是一对：一个验证「没有身体」，一个验证「没有心跳」。
 * 两者都只是验证载体，不是设计好的玩法内容。
 */
public final class ModHostItems {

    public static Item GHOST_LANTERN;

    private ModHostItems() {
    }

    public static void register() {
        Identifier id = new Identifier(SmfsAddon.MOD_ID, "ghost_lantern");

        GHOST_LANTERN = new GhostLanternItem(new Item.Settings().maxCount(1));
        Registry.register(Registries.ITEM, id, GHOST_LANTERN);

        SmfsAddon.LOGGER.info("[smfsaddon] 鬼灯笼已注册（物品宿主验证道具）");
    }
}
