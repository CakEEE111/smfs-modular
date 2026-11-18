package com.example.smfsaddon.effect;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * 剥皮 —— 皮嫁骨·皮 碰到你之后留下的标记。
 *
 * <h2>它是什么</h2>
 *
 * <p>原作里鬼新娘的手一碰，人就「骨骼尽失，只剩人皮」。我们没有做真正的骨骼形态，
 * 所以用这个状态代替两件事：</p>
 *
 * <ol>
 *   <li><b>标记</b>：只有挂着它的玩家，皮嫁骨·骨才会来追（骨只认"同类/新郎"）；</li>
 *   <li><b>代价</b>：移速下降、灵异抗性被削 —— 皮被剥掉之后，人就是虚的。</li>
 * </ol>
 *
 * <p>不做成即死，是因为它应该是"被盯上了"，不是"已经死了" ——
 * 给玩家一次跑掉或者把骨解决掉的机会。</p>
 */
public class SkinShedEffect extends StatusEffect {

    private static final UUID SPEED_ID = UUID.fromString("3f1e9c62-1d4a-4f6b-9a77-2c58d1b0e4a1");
    private static final UUID RESIST_ID = UUID.fromString("8b7d4a10-2c33-4c8e-bd61-5f0a9e7c2d18");

    public SkinShedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8B1E1E);
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        var speed = attributes.getCustomInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.addPersistentModifier(new EntityAttributeModifier(
                    SPEED_ID, "pijiagu_skin_shed_slow", -0.20D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        var resist = attributes.getCustomInstance(
                com.xie.smfs.effect.SpiritAttributes.SPIRIT_RESISTANCE);
        if (resist != null) {
            resist.addPersistentModifier(new EntityAttributeModifier(
                    RESIST_ID, "pijiagu_skin_shed_resist", -0.30D,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        var speed = attributes.getCustomInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
        }
        var resist = attributes.getCustomInstance(
                com.xie.smfs.effect.SpiritAttributes.SPIRIT_RESISTANCE);
        if (resist != null) {
            resist.removeModifier(RESIST_ID);
        }
    }

    /** 每 10 tick 冒一次骨白粒子，让"正在被剥皮"有持续感 */
    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) {
            return;
        }
        var world = (net.minecraft.server.world.ServerWorld) entity.getWorld();
        world.spawnParticles(net.minecraft.particle.ParticleTypes.WHITE_ASH,
                entity.getX(), entity.getY() + entity.getHeight() * 0.6D, entity.getZ(),
                6, 0.24D, 0.35D, 0.24D, 0.01D);
    }

    /** 供外部快速判断 */
    public static boolean isMarked(PlayerEntity player) {
        return player != null && player.hasStatusEffect(SmfsAddon.SKIN_SHED);
    }
}
