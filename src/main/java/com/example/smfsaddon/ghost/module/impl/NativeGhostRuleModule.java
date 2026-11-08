package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.SpatialModule;
import com.example.smfsaddon.ghost.module.event.GhostAttackHitEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.xie.smfs.damage.ModDamageSources;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 本源规律：直接调用鬼自己的判定函数。
 *
 * <p>这是为「别的附属的鬼」准备的通用通道。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>手写模块的问题在于<b>每一只鬼都要重写一遍它的规律</b> ——
 * 换一只附属鬼就得再写一次，那不叫插件化。
 *
 * <p>而本体的鬼虽然把规律写死在类里，却统一覆写了
 * {@link GhostEntity#shouldAttackPlayer(PlayerEntity)}。
 * 那就是唯一通用的入口：不去复制逻辑，而是<b>造一个"影子实例"</b>
 * 摆到施法者所在的位置，然后调用它自己的判定。
 *
 * <p>于是任何继承 {@code GhostEntity} 的鬼 —— 本体的、别的附属的、
 * 甚至将来新加的 —— 都不需要额外适配。
 *
 * <h2>代价与边界</h2>
 *
 * <p>影子实例不进入世界，只借用它的判定函数，用完即弃。
 * 它只能覆盖「杀人规律」这一层（{@code shouldAttackPlayer} 能表达的），
 * 覆盖不到鬼域、开门之类写在 tick 里的行为 —— 那部分仍然需要专门的模块。
 */
public final class NativeGhostRuleModule implements SpatialModule {

    public static final Identifier ID = new Identifier("smfsaddon", "native_rule");

    /** 单只鬼的绑定（旧存档兼容） */
    private static final String KEY_TYPE = "GhostType";

    /**
     * 多只鬼的绑定，逗号分隔。
     *
     * <p>一个物品只有一个 native_rule 实例，所以多次融合不能各存一份 ——
     * 早先只写单值，融第二只就把第一只覆盖了，表现是"只显示一条"。
     */
    private static final String KEY_TYPES = "GhostTypes";

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "本源规律";
    }

    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new NativeStrikeRule());
    }

    /**
     * 把模块绑定到某只鬼身上。合入时调用。
     */
    public static void bind(GhostModuleData data, Identifier entityType) {
        String path = entityType.toString();
        String existing = data.custom().getString(KEY_TYPES);
        if (existing.isEmpty()) {
            data.custom().putString(KEY_TYPES, path);
            data.markChanged();
            return;
        }
        String padded = "," + existing + ",";
        if (!padded.contains("," + path + ",")) {
            data.custom().putString(KEY_TYPES, existing + "," + path);
            data.markChanged();
        }
    }

    /**
     * 物品上绑定的全部鬼类型。
     */
    public static java.util.List<String> boundTypes(GhostModuleData data) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String joined = data.custom().getString(KEY_TYPES);
        if (!joined.isEmpty()) {
            for (String part : joined.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        if (result.isEmpty()) {
            String single = data.custom().getString(KEY_TYPE);
            if (!single.isEmpty()) {
                result.add(single);
            }
        }
        return result;
    }

    private static java.util.List<Identifier> boundTypes(GhostModuleContext context) {
        java.util.List<Identifier> ids = new java.util.ArrayList<>();
        for (String raw : boundTypes(context.data())) {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static final class NativeStrikeRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "native_rule/strike");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 200;
        }

        @Override
        public double revivalGain() {
            return 1.5D;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "本源规律",
                    "按照这只鬼自己的判断，决定目标该不该死"
            );
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostAttackHitEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            // 本源规律是「杀人规律」，只对玩家有意义
            return event instanceof GhostAttackHitEvent hit
                    && hit.target() instanceof PlayerEntity
                    && !boundTypes(context).isEmpty();
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackHitEvent hit) || context.world() == null) {
                return;
            }
            PlayerEntity attacker = hit.player();
            if (attacker == null || !(hit.target() instanceof PlayerEntity targetPlayer)) {
                return;
            }

            // 逐只鬼问一遍：任意一只认为该杀，就够了
            for (Identifier typeId : boundTypes(context)) {
                if (kills(context, typeId, attacker, targetPlayer)) {
                    targetPlayer.damage(ModDamageSources.ghost(context.world()), 1000.0F);
                    return;
                }
            }
        }

        /**
         * 造一个影子实例，问它"这个人该不该死"。
         */
        private boolean kills(GhostModuleContext context, Identifier typeId,
                              PlayerEntity attacker, PlayerEntity target) {
            EntityType<?> type = Registries.ENTITY_TYPE.get(typeId);
            if (type == null) {
                return false;
            }

            GhostEntity shadow = createShadow(type, context);
            if (shadow == null) {
                return false;
            }
            try {
                // 把影子摆到施法者所在的位置，让鬼自己的坐标判断成立
                shadow.setPosition(attacker.getX(), attacker.getY(), attacker.getZ());
                return shadow.shouldAttackPlayer(target);
            } finally {
                shadow.discard();
            }
        }

        private GhostEntity createShadow(EntityType<?> type, GhostModuleContext context) {
            try {
                return type.create(context.world()) instanceof GhostEntity ghost ? ghost : null;
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
