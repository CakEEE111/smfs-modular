package com.example.smfsaddon.mixin;

import com.example.smfsaddon.item.ItemGhostBridge;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持有者受伤时，给双手上的物品一次介入机会。
 *
 * <p>替死这类能力不在"谁该杀"的判定里，也不在"使用时"的路径里，
 * 它只在<b>主人要死的那一刻</b>出现。所以钩在伤害结算之前。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$ownerDamaged(DamageSource source, float amount,
                                        CallbackInfoReturnable<Boolean> callbackInfo) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (ItemGhostBridge.ownerDamaged(self, source, amount)) {
            // 已经有人替他挡了，这次伤害不再结算
            callbackInfo.setReturnValue(false);
        }
    }
}
