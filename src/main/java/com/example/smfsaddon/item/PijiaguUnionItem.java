package com.example.smfsaddon.item;

import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 皮嫁骨·礼成体的驾驭容器。
 *
 * <p>约束：必须提供 public (Settings,int,int,int,int,double) 构造器 —— 本体用反射构造。
 * 绑定流程（找空槽 → 写 NBT → 弹能力面板）由 {@code BaseGhostEyeItem#use} 全自动完成。</p>
 */
public class PijiaguUnionItem extends BaseGhostEyeItem {

    public PijiaguUnionItem(Settings settings, int maxSpiritBonus, int spiritResistanceBonus,
                            int spiritDamageBonus, int sanityBonus, double revivalFactor) {
        super(settings, maxSpiritBonus, spiritResistanceBonus, spiritDamageBonus, sanityBonus, revivalFactor);
    }

    /** 必须和注册鬼种时用的 ID 一致 */
    @Override
    public String getGhostType() {
        return "pijiagu_union";
    }

    @Override
    protected Text getBindSuccessMessage(int slot) {
        return Text.translatable("item.smfsaddon.pijiagu_union.bind_success", slot + 1);
    }

    @Override
    protected void sendSlotsFullMessage(PlayerEntity player) {
        player.sendMessage(Text.translatable("item.smfsaddon.slots_full"), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.smfsaddon.pijiagu_union.desc")
                .formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("item.smfsaddon.pijiagu_union.skill")
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
