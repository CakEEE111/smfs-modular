package com.example.smfsaddon.mixin;

import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.item.GhostNames;
import com.example.smfsaddon.item.ItemGhostHost;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 给带规律的物品加词条。
 *
 * <p>只显示「里面是哪几只鬼」。模块名是实现细节，玩家不关心，
 * 而且一条规律可能由多只鬼提供，按模块列反而会撒谎。
 *
 * <p>名字解析在 {@link GhostNames}，技能面板复用同一套 —— 早先这里私有一份，
 * 面板那边却直接打原始 ID，同一个物品在词条里是「完整鬼影」、在面板里是
 * {@code complete_shadow_ghost}，看着像两个东西。
 */
@Mixin(Item.class)
public abstract class ItemTooltipMixin {

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
            String name = GhostNames.of(ghostType);
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

        // 灵异伤害：与本体灵异武器同一套模型 —— 基础值 + 玩家灵异伤害 × 继承倍率
        float base = com.example.smfsaddon.item.GhostSpiritPower.baseOf(stack);
        if (base > 0.0F) {
            lines.add(Text.literal("§8· §7基础灵异伤害 §c+" + String.format("%.1f", base)));
            lines.add(Text.literal("§8· §7继承倍率 §d+"
                    + String.format("%.0f", com.example.smfsaddon.item.GhostSpiritPower.INHERIT_MULTIPLIER * 100)
                    + ".0%"));
        }

        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§8灵异词条 ×" + ItemGhostHost.boundGhostTypes(stack).size()));
        tooltip.addAll(lines);
    }
}
