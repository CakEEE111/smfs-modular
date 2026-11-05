package com.example.smfsaddon.item;

import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;

import java.util.List;

/**
 * 卡卡罗特的容器物品。
 *
 * 约束：必须提供 public (Settings,int,int,int,int,double) 构造器 —— 本体用反射构造。
 * 绑定流程（找空槽 → 写 NBT → 弹能力面板）由 BaseGhostEyeItem#use 全自动完成。
 */
public class KakarotGhostItem extends BaseGhostEyeItem {

    public KakarotGhostItem(Settings settings, int maxSpiritBonus, int spiritResistanceBonus,
                            int spiritDamageBonus, int sanityBonus, double revivalFactor) {
        super(settings, maxSpiritBonus, spiritResistanceBonus, spiritDamageBonus, sanityBonus, revivalFactor);
    }

    /** 必须与实体注册时用的鬼种 ID 一致 */
    @Override
    public String getGhostType() {
        return "kakarot_ghost";
    }

    @Override
    protected Text getBindSuccessMessage(int slot) {
        return Text.translatable("item.smfsaddon.kakarot_ghost.bind_success", slot + 1);
    }

    @Override
    protected void sendSlotsFullMessage(PlayerEntity player) {
        player.sendMessage(Text.translatable("item.smfsaddon.slots_full"), true);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.smfsaddon.kakarot_ghost");
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.EPIC;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.smfsaddon.kakarot_ghost.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.smfsaddon.kakarot_ghost.desc2").formatted(Formatting.DARK_RED));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("item.smfsaddon.kakarot_ghost.skill_domain").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.smfsaddon.kakarot_ghost.skill_kame").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.smfsaddon.kakarot_ghost.skill_ssj").formatted(Formatting.YELLOW));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
