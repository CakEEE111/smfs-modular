package com.example.smfsaddon.item;

import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * 皮嫁骨·「骨」的驾驭容器。
 *
 * <p>被剥干净之后剩下的那具。它会自己走过来，会跪，会拜 ——
 * 但它已经不知道该听谁的了。</p>
 */
public class PijiaguBoneItem extends BaseGhostEyeItem {

    public PijiaguBoneItem(Settings settings, int maxSpiritBonus, int spiritResistanceBonus,
                           int spiritDamageBonus, int sanityBonus, double revivalFactor) {
        super(settings, maxSpiritBonus, spiritResistanceBonus, spiritDamageBonus, sanityBonus, revivalFactor);
    }

    @Override
    public String getGhostType() {
        return "pijiagu_bone";
    }

    @Override
    protected Text getBindSuccessMessage(int slot) {
        return Text.translatable("item.smfsaddon.pijiagu_bone.bind_success", slot + 1);
    }

    @Override
    protected void sendSlotsFullMessage(PlayerEntity player) {
        player.sendMessage(Text.translatable("item.smfsaddon.slots_full"), true);
    }
}
