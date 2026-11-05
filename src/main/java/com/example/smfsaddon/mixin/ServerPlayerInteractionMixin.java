package com.example.smfsaddon.mixin;

import com.example.smfsaddon.item.ItemGhostBridge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家右键方块的总入口。
 *
 * <p>为什么不用 {@code ItemStack.useOnBlock}：实测右键<b>门</b>时那个方法根本不会被调用
 * ——对空气能触发、对方块（门）一点反应都没有。门属于「方块自己处理交互」的一类，
 * 物品的 useOnBlock 在其中一条分支上被跳过了。
 *
 * <p>{@code ServerPlayerInteractionManager#interactBlock} 是所有这些分支的上游，
 * 一定被调用，所以把规律派发放在这里，门的场景才能覆盖到。
 *
 * <p>与 {@code ItemStack.use}（对空气右键）互补，两者不会重叠：
 * 对空气不会进 interactBlock，对方块不会进 use。
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void smfsaddon$onInteractBlock(ServerPlayerEntity player, World world, ItemStack stack,
                                           Hand hand, BlockHitResult hitResult,
                                           CallbackInfoReturnable<ActionResult> callbackInfo) {
        if (hitResult == null) {
            return;
        }
        ItemGhostBridge.fired(
                stack,
                player,
                world,
                hitResult.getBlockPos(),
                world.getBlockState(hitResult.getBlockPos())
        );
    }
}
