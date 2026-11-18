package com.example.smfsaddon.effect;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * 「皮已离体」—— 玩家用鬼剪刀把自己的皮剪下来之后的状态。
 *
 * <h2>这是不可逆的</h2>
 *
 * <p>设计定稿：剪皮不可逆。所以这个状态一旦挂上就不会自己消失（时长给满），
 * 属性惩罚也直接写死在状态里：</p>
 *
 * <ul>
 *   <li>生命上限 −2（少了那层皮）</li>
 *   <li>灵异抗性 −20%（没皮挡着）</li>
 * </ul>
 *
 * <p>作为交换，玩家会获得通往「礼成」的资格 —— 皮会主动找上门。</p>
 */
public class FleshRemovedEffect extends StatusEffect {

    private static final UUID HEALTH_MODIFIER_ID =
            UUID.fromString("8f2c1b7e-3a44-4a51-9a55-1c0e7d2b6f31");
    private static final UUID RESIST_MODIFIER_ID =
            UUID.fromString("1d9a4e63-77b2-4c8e-b0c1-5a2f3e9d8c47");

    public FleshRemovedEffect() {
        super(StatusEffectCategory.HARMFUL, 0xE8E4D6);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }

    @Override
    public void onApplied(LivingEntity entity,
                          net.minecraft.entity.attribute.AttributeContainer attributes,
                          int amplifier) {
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        var health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null && health.getModifier(HEALTH_MODIFIER_ID) == null) {
            health.addPersistentModifier(new EntityAttributeModifier(
                    HEALTH_MODIFIER_ID, "smfsaddon.flesh_removed.health", -2.0D,
                    EntityAttributeModifier.Operation.ADDITION));
        }
        // 灵异抗性走本体自己的数据（本体把 IPlayerData 混入到了 PlayerEntity 上）
        try {
            if (player instanceof com.xie.smfs.api.IPlayerData data) {
                data.setSpiritResistance(Math.max(0.0F, data.getSpiritResistance() * 0.8F));
            }
        } catch (Throwable ignored) {
            // 接口不可用时不影响主流程
        }
        player.sendMessage(net.minecraft.text.Text.literal(
                "§7皮落到地上，发出很轻的一声。§8你不再需要它了。"), false);
    }
}
