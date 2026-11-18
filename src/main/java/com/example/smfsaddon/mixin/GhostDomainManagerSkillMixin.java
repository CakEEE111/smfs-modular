package com.example.smfsaddon.mixin;

import com.example.smfsaddon.skill.FusedGhostIndex;
import com.xie.smfs.manager.GhostDomainManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让本体的技能资格检查认识「融合物品持有者」。
 *
 * <h2>为什么需要这一层</h2>
 *
 * <p>本体的技能包在放技能前会问 {@code canUseGhostSkill(player, 鬼种, 鬼物品, 等级)}：
 * 它只查<b>主鬼槽</b>里的那个物品，以及手上有没有碎片。玩家的鬼是融在剑上的，
 * 主鬼槽里当然没有，于是直接被判 {@code NO_GHOST} —— 表现是
 * 「点了技能，弹一句『你还没有驾驭X鬼』」。
 *
 * <p>内部的 {@code hasGhostType} 已经被 {@link PlayerEventsGhostMixin} 放行了，
 * 所以这里只需补上这一道闸门。补完之后，<b>本体的技能包本身</b>就能被
 * 融合物品持有者使用 —— 冷却、倍率、复苏平衡全部照本体那份走。
 *
 * <p>这是有意为之的行为改变：手上拿着融合了某只鬼的物品，就当作驾驭了它。
 * 与 {@code hasGhostType} 那条保持同一口径。
 *
 * <p>必须 {@code remap = false}：目标方法是模组自己的，不是 Minecraft 的。
 */
@Mixin(GhostDomainManager.class)
public abstract class GhostDomainManagerSkillMixin {

    @Inject(method = "canUseGhostSkill", at = @At("HEAD"), cancellable = true, remap = false)
    private static void smfsaddon$fusedHolderPassesGate(PlayerEntity player, String ghostType,
                                                        Item ghostItem, int requiredLevel,
                                                        CallbackInfoReturnable<GhostDomainManager.SkillCheckResult> callbackInfo) {
        if (FusedGhostIndex.carried(player, ghostType)) {
            callbackInfo.setReturnValue(GhostDomainManager.SkillCheckResult.SUCCESS);
        }
    }
}
