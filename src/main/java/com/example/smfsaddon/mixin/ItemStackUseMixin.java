package com.example.smfsaddon.mixin;

import com.example.smfsaddon.item.ItemGhostBridge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让<b>任意</b>物品都能携带并触发厉鬼规律。
 *
 * <p>只给自家物品加钩子是不够的 —— 那样「万物插件化」就退化成
 * 「专用物品插件化」。所以挂在 {@code ItemStack} 上：
 * 只要这件物品的 NBT 里有模块，用它对空气、对方块，都会派发一次规律。
 *
 * <p>两处注入刻意都用 HEAD 且不改返回值：物品原本的行为完全保留，
 * 规律只是叠加上去的效果。剑还是剑，只是开了门会死人。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackUseMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void smfsaddon$onUse(World world, PlayerEntity user, Hand hand,
                                 CallbackInfoReturnable<TypedActionResult<ItemStack>> callbackInfo) {
        ItemGhostBridge.fired((ItemStack) (Object) this, user, world, null, null);
    }

    /**
     * 攻击命中。
     *
     * <p>「用」和「打」是两条独立的路：规律要能在剑上生效，
     * 就得知道打中了谁。物品自己不选目标，只能从这条路径拿到。
     */
    @Inject(method = "postHit", at = @At("HEAD"))
    private void smfsaddon$onPostHit(LivingEntity target, PlayerEntity player,
                                     CallbackInfo callbackInfo) {
        ItemGhostBridge.attackFired((ItemStack) (Object) this, player, target);
    }
}
