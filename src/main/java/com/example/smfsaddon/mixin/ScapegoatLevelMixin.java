package com.example.smfsaddon.mixin;

import com.example.smfsaddon.skill.FusedGhostIndex;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.item.ScapegoatGhostItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 融合物品上的替死，等级按「驾驭的厉鬼中最高的那个复苏等级」算。
 *
 * <h2>为什么需要这条</h2>
 *
 * <p>本体的 {@code getScapegoatGhostLevel} 只会去<b>鬼槽</b>里找替死鬼那一格，
 * 读它的 {@code level}。融合在盾牌上的替死鬼根本没有槽位，于是永远返回默认的 1 级 ——
 * 理智/灵异只回 20%，跟玩家实际驾驭了多少鬼、养到多高完全无关。
 *
 * <p>等级在本体里是随复苏度走的（{@code slotData.level} 在复苏度跨档时被改写），
 * 所以「最高的复苏等级」直接取所有已占用槽位的 {@code level} 最大值即可。
 *
 * <h2>只在融合物品上生效</h2>
 *
 * <p>没有拿着融合了替死鬼的物品时直接放行，本体原有行为一个字都不改 ——
 * 免得把「鬼槽里替死鬼 2 级、另一只 5 级」的玩家也一起改了。
 *
 * <p>必须 {@code remap = false}：目标方法是模组自己的。
 */
@Mixin(ScapegoatGhostItem.class)
public abstract class ScapegoatLevelMixin {

    @Inject(method = "getScapegoatGhostLevel", at = @At("RETURN"), cancellable = true, remap = false)
    private static void smfsaddon$maxOwnedRevivalLevel(PlayerEntity player,
                                                       CallbackInfoReturnable<Integer> callbackInfo) {
        if (!FusedGhostIndex.carried(player, "scapegoat_ghost")) {
            return;
        }
        callbackInfo.setReturnValue(maxOwnedLevel(player));
    }

    /**
     * 所有已占用鬼槽里的最高等级；一个都没有则 1 级。
     */
    private static int maxOwnedLevel(PlayerEntity player) {
        int best = 1;
        for (int slot = 0; slot < PlayerEvents.GHOST_SLOT_COUNT; slot++) {
            ItemStack stack = PlayerEvents.getGhostSlotItem(player, slot);
            if (stack.isEmpty()) {
                continue;
            }
            best = Math.max(best, PlayerEvents.getGhostSlotLevel(player, slot));
        }
        return best;
    }
}
