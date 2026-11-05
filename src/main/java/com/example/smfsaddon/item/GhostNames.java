package com.example.smfsaddon.item;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 鬼种短名 → 玩家看得懂的名字。
 *
 * <p>顺序是「驾驭物 → 实体」：玩家拿到的是驾驭物，所以物品名优先。
 * <b>不能只查 smfs / smfsaddon 两个命名空间</b> —— 别的附属的鬼在自己的命名空间里，
 * 写死命名空间的结果就是界面上一串英文短名。
 *
 * <p>结果缓存：tooltip 每帧重算、技能面板每次打开都要用，跨命名空间查找是遍历整张注册表。
 */
public final class GhostNames {

    private static final Map<String, String> CACHE = new HashMap<>();

    private GhostNames() {
    }

    public static String of(String ghostType) {
        if (ghostType == null || ghostType.isEmpty()) {
            return "?";
        }
        String cached = CACHE.get(ghostType);
        if (cached != null) {
            return cached;
        }

        String path = ghostType;
        Identifier direct = Identifier.tryParse(ghostType);
        if (direct != null) {
            path = direct.getPath();
        }

        String resolved = lookup(path);
        String result = resolved != null ? resolved : path;
        CACHE.put(ghostType, result);
        return result;
    }

    private static String lookup(String path) {
        for (String namespace : new String[]{"smfs", "smfsaddon"}) {
            Identifier itemId = Identifier.tryParse(namespace + ":" + path);
            if (itemId == null) {
                continue;
            }
            var item = Registries.ITEM.getOrEmpty(itemId);
            if (item.isPresent()) {
                return item.get().getName().getString();
            }
        }
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!id.getPath().equals(path)) {
                continue;
            }
            var item = Registries.ITEM.getOrEmpty(id);
            if (item.isPresent()) {
                return item.get().getName().getString();
            }
        }

        for (String namespace : new String[]{"smfs", "smfsaddon"}) {
            Identifier entityId = Identifier.tryParse(namespace + ":" + path);
            if (entityId == null) {
                continue;
            }
            var entity = Registries.ENTITY_TYPE.getOrEmpty(entityId);
            if (entity.isPresent()) {
                return entity.get().getName().getString();
            }
        }
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            if (!id.getPath().equals(path)) {
                continue;
            }
            var entity = Registries.ENTITY_TYPE.getOrEmpty(id);
            if (entity.isPresent()) {
                return entity.get().getName().getString();
            }
        }

        return null;
    }
}
