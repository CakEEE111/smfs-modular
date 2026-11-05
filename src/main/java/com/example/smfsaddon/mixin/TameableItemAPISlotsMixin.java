package com.example.smfsaddon.mixin;

import com.example.smfsaddon.item.FusedGhostSlots;
import com.xie.smfs.api.impl.TameableItemAPIImpl;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让本体的鬼槽 API 看得见「融合在物品上的鬼」。
 *
 * <h2>为什么改在这里，而不是每个附属写一条</h2>
 *
 * <p>附属判断玩家有没有某只鬼，除了 {@code PlayerEvents.hasGhostType}，
 * 还有一条官方路径：{@code TameableItemAPI.isGhostSlotOccupied(player, slot)}
 * ＋ {@code getGhostTypeInSlot(player, slot)} 逐槽扫描。走这条路的附属
 * 完全绕开了 {@code hasGhostType}，所以那边的混入再对也没用 —— 实测「梦游鬼」
 * 的夜间失控就是这么失效的。
 *
 * <p>这里给 <b>空闲的真实槽位</b> 投影出融合鬼（映射见 {@link FusedGhostSlots}），
 * 附属扫槽时看到的就是「这只鬼在槽里」。一处改动覆盖所有按 API 扫槽的附属，
 * 不需要认识它们。
 *
 * <h2>为什么只碰读取，不碰写入</h2>
 *
 * <p>投影槽位在 {@code PlayerEvents} 里是空的。附属若对这些槽位写复苏度/等级，
 * 会往一个不存在的槽里写数据，把玩家存档搞脏。所以写入一律取消 ——
 * 融合物品的强度本来就该由物品自身（模块、复苏度）决定，而不是被外部改。
 *
 * <p>本体自己只用到 API 的 {@code hasTamedGhost} / {@code getGhostType}，
 * 这两条<b>不在这里动</b>，所以本体原有行为不变（尤其是「绑定新鬼」那条判定）。
 *
 * <p>全部 {@code remap = false}：目标是模组自己的类。
 */
@Mixin(TameableItemAPIImpl.class)
public abstract class TameableItemAPISlotsMixin {

    @Inject(method = "isGhostSlotOccupied", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualOccupied(PlayerEntity player, int slot,
                                           CallbackInfoReturnable<Boolean> callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "getGhostTypeInSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualType(PlayerEntity player, int slot,
                                       CallbackInfoReturnable<String> callbackInfo) {
        String type = FusedGhostSlots.bySlot(player).get(slot);
        if (type != null) {
            callbackInfo.setReturnValue(type);
        }
    }

    @Inject(method = "getGhostSlotItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualItem(PlayerEntity player, int slot,
                                       CallbackInfoReturnable<ItemStack> callbackInfo) {
        ItemStack stack = FusedGhostSlots.stackAt(player, slot);
        if (stack != null) {
            callbackInfo.setReturnValue(stack);
        }
    }

    @Inject(method = "getGhostSlotLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualLevel(PlayerEntity player, int slot,
                                        CallbackInfoReturnable<Integer> callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            // 融合物品没有槽位等级，按 1 级算；要更强就堆模块
            callbackInfo.setReturnValue(1);
        }
    }

    @Inject(method = "getGhostSlotRevivalDegree", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualRevival(PlayerEntity player, int slot,
                                          CallbackInfoReturnable<Integer> callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.setReturnValue(0);
        }
    }

    @Inject(method = "getGhostSlotRequiredRevivalDegree", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$virtualRequiredRevival(PlayerEntity player, int slot,
                                                  CallbackInfoReturnable<Integer> callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            // 用本体新槽的默认值，避免附属拿 0 去做除法
            callbackInfo.setReturnValue(1000);
        }
    }

    // ==================== 写入一律拒绝 ====================

    @Inject(method = "setGhostSlotItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$blockSetItem(PlayerEntity player, int slot, ItemStack stack,
                                        CallbackInfo callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setGhostSlotRevivalDegree", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$blockSetRevival(PlayerEntity player, int slot, int degree,
                                           CallbackInfo callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setGhostSlotLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$blockSetLevel(PlayerEntity player, int slot, int level,
                                         CallbackInfo callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setGhostSlotRequiredRevivalDegree", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$blockSetRequiredRevival(PlayerEntity player, int slot, int degree,
                                                   CallbackInfo callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "clearGhostSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void smfsaddon$blockClear(PlayerEntity player, int slot, CallbackInfo callbackInfo) {
        if (FusedGhostSlots.isVirtual(player, slot)) {
            callbackInfo.cancel();
        }
    }
}
