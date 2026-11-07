package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.crossing.GhostRuleCrossing;
import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostOnlyModule;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.event.GhostAttackRuleEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵异交错：这只鬼除了自己的规律，还借了别人的。
 *
 * <h2>它为什么单独成一个模块</h2>
 *
 * <p>交错是"鬼"的能力（鬼要打人时多问几个判定），而本源规律是"物品"的能力
 * （拿着剑打人时按绑定的鬼判）。两者事件不同、宿主不同、生命周期也不同，
 * 混在一起会让一件事的失败牵连另一件。
 *
 * <h2>和老鬼的兼容</h2>
 *
 * <p>没交错过的鬼根本没有这个模块，{@code attack_rule} 上没有任何模块表态，
 * 裁决保持 ABSTAIN，宿主回落自己的老逻辑 —— 一只老鬼的行为一个字节都不变。
 */
public final class CrossingModule implements GhostOnlyModule {

    public static final Identifier ID = new Identifier("smfsaddon", "crossing");

    /** 借来的鬼种，逗号分隔，与本源规律同一种存法 */
    private static final String KEY_TYPES = "Types";

    /** 借来的机制模块 id，逗号分隔 */
    private static final String KEY_MECHANISMS = "Mechanisms";

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "灵异交错";
    }

    @Override
    public double suppressionCost() {
        // 它是自动挂在鬼身上的，不参与融合计价
        return 0.0D;
    }

    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new CrossedStrikeRule());
    }

    public static List<Identifier> types(GhostModuleData data) {
        return parse(data, KEY_TYPES);
    }

    public static void setTypes(GhostModuleData data, List<Identifier> types) {
        if (data == null) {
            return;
        }
        data.custom().putString(KEY_TYPES, join(types));
    }

    /**
     * 借来的机制模块（踩头、空间压制、鬼域在场这类）。
     *
     * <p>与"借规律"分开记：规律决定该不该杀，机制决定用什么手段，
     * 排查时要能分开看 —— 规律全白借但机制生效，是完全不同的结论。
     */
    public static List<Identifier> mechanisms(GhostModuleData data) {
        return parse(data, KEY_MECHANISMS);
    }

    public static void setMechanisms(GhostModuleData data, List<Identifier> ids) {
        if (data == null) {
            return;
        }
        data.custom().putString(KEY_MECHANISMS, join(ids));
    }

    private static List<Identifier> parse(GhostModuleData data, String key) {
        List<Identifier> result = new ArrayList<>();
        if (data == null) {
            return result;
        }
        for (String raw : data.custom().getString(key).split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            Identifier id = Identifier.tryParse(raw.trim());
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private static String join(List<Identifier> ids) {
        StringBuilder builder = new StringBuilder();
        for (Identifier id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private static final class CrossedStrikeRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "crossing/strike");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            // 比常规规律低：自己人的判断优先表态，交错只做"补刀"
            return 50;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "灵异交错",
                    "它杀人的依据，不只来自它自己"
            );
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostAttackRuleEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            return event instanceof GhostAttackRuleEvent
                    && !types(context.data()).isEmpty();
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackRuleEvent attackEvent)) {
                return;
            }
            GhostEntity self = context.ghost();
            PlayerEntity target = attackEvent.player();
            if (self == null || target == null || context.world() == null) {
                return;
            }

            for (Identifier typeId : types(context.data())) {
                if (!GhostRuleCrossing.ask(typeId, self, target)) {
                    continue;
                }
                // 命中之后才过冷却闸：冷却期内不表态（ABSTAIN），
                // 让本体照常去问鬼自己的规律，原版的出手间隔就回来了。
                // 注意闸门放在这里而不是循环之前 —— 否则"没命中"也会白吃 40 tick。
                if (!GhostRuleCrossing.tryConsumeAllow(
                        self.getUuid(), target.getUuid(), context.ticks())) {
                    return;
                }
                // 只表态允许，不表态否决：
                // 交错的意思是多一条杀你的理由，不是抹掉它自己的判断
                attackEvent.allow();
                return;
            }
        }
    }
}
