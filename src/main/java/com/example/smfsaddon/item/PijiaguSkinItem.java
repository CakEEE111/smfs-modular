package com.example.smfsaddon.item;

import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * 皮嫁骨·「皮」的驾驭容器。
 *
 * <p>玩家自己剪下来的那层皮。温柔、听话、从不反抗 —— 但它在婚礼上做的那些事，
 * 你都记得。</p>
 */
public class PijiaguSkinItem extends BaseGhostEyeItem {

    public PijiaguSkinItem(Settings settings, int maxSpiritBonus, int spiritResistanceBonus,
                           int spiritDamageBonus, int sanityBonus, double revivalFactor) {
        super(settings, maxSpiritBonus, spiritResistanceBonus, spiritDamageBonus, sanityBonus, revivalFactor);
    }

    @Override
    public String getGhostType() {
        return "pijiagu_skin";
    }

    @Override
    protected Text getBindSuccessMessage(int slot) {
        return Text.translatable("item.smfsaddon.pijiagu_skin.bind_success", slot + 1);
    }

    @Override
    protected void sendSlotsFullMessage(PlayerEntity player) {
        player.sendMessage(Text.translatable("item.smfsaddon.slots_full"), true);
    }
}
