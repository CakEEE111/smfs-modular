package com.example.smfsaddon.mixin;

import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.item.ItemGhostHost;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 给带规律的物品加词条。
 *
 * <p>只显示「里面是哪几只鬼」。模块名是实现细节，玩家不关心，
 * 而且一条规律可能由多只鬼提供，按模块列反而会撒谎。
 */
@Mixin(Item.class)
public abstract class ItemTooltipMixin {

    /**
     * 鬼种 → 显示名 的缓存。
     *
     * <p>tooltip 每帧都会重算，而跨命名空间查找要遍历物品表，
     * 不缓存的话每帧扫几千条，纯属浪费。
     */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    @Inject(method = "appendTooltip", at = @At("TAIL"))
    private void smfsaddon$appendModuleEntries(ItemStack stack, @Nullable World world,
                                               List<Text> tooltip, TooltipContext context,
                                               CallbackInfo callbackInfo) {
        List<Identifier> modules = ItemGhostHost.moduleIds(stack);
        if (modules.isEmpty()) {
            return;
        }

        List<Text> lines = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (String ghostType : ItemGhostHost.boundGhostTypes(stack)) {
            String name = ghostName(ghostType);
            if (!seen.add(name)) {
                continue;
            }
            lines.add(Text.literal("§8· §b" + name));
        }

        // 没有绑定鬼的物品（比如只有手写模块的鬼灯笼），退回显示模块名
        if (lines.isEmpty()) {
            for (Identifier moduleId : modules) {
                if ("smfsaddon:native_rule".equals(moduleId.toString())) {
                    continue;
                }
                GhostModule module = GhostModuleRegistry.get(moduleId);
                lines.add(Text.literal("§8· §b"
                        + (module == null ? moduleId.getPath() : module.displayName())));
            }
        }

        if (lines.isEmpty()) {
            return;
        }

        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§8灵异词条 ×" + lines.size()));
        tooltip.addAll(lines);
    }

    /**
     * 鬼种短名 → 显示名。
     *
     * <p>顺序是「驾驭物 → 实体」。
     * <b>不能只查 smfs / smfsaddon 两个命名空间</b> —— 别的附属的鬼在自己的
     * 命名空间里，写死命名空间的结果是英文短名。
     * 因此这里跨全部命名空间按「路径同名」查找。
     */
    private static String ghostName(String ghostType) {
        String cached = NAME_CACHE.get(ghostType);
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
        NAME_CACHE.put(ghostType, result);
        return result;
    }

    private static String lookup(String path) {
        // ① 驾驭物：先试常见命名空间，再全表按路径找
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

        // ② 实体：同样跨命名空间
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
