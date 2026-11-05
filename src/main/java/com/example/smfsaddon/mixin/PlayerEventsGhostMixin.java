package com.example.smfsaddon.mixin;

import com.example.smfsaddon.skill.FusedGhostIndex;
import com.xie.smfs.common.events.PlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「手上拿着融合了某只鬼的物品」等同于「驾驭了那只鬼」。
 *
 * <p>本体的 17 个技能，内部全部靠 {@code hasGhostType(player, ghostType)} 判断资格。
 * 在这一个方法上加一条，<b>所有技能同时可用</b> —— 不需要逐个适配，
 * 也不需要在每个技能里另开分支。
 *
 * <p>必须 {@code remap = false}：这是模组自己的方法，不是 Minecraft 方法，
 * 没有混淆映射可查。
 */
@Mixin(PlayerEvents.class)
public abstract class PlayerEventsGhostMixin {

    @Inject(method = "hasGhostType", at = @At("RETURN"), cancellable = true, remap = false)
    private static void smfsaddon$fusedCountsAsOwned(PlayerEntity player, String ghostType,
                                                      CallbackInfoReturnable<Boolean> callbackInfo) {
        if (Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            return;
        }
        if (FusedGhostIndex.holds(player, ghostType)) {
            callbackInfo.setReturnValue(true);
        }
    }
}
