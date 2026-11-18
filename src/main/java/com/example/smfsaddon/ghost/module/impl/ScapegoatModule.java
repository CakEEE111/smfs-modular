package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.SpatialModule;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.event.GhostOwnerDamagedEvent;
import com.xie.smfs.item.ScapegoatGhostItem;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 替死。
 *
 * <p>这条能力不在 {@code shouldAttackPlayer} 那条线上，所以「本源规律」借不到它 ——
 * 本体的实现放在 {@code ScapegoatGhostItem.handleScapegoatPassiveSkill} 里，
 * 是个公开静态方法。这里直接调它，不复制任何逻辑。
 *
 * <p>于是把替死鬼合进盾牌：盾牌会替持有者挡下那一次致命伤害。
 *
 * <h2>这条也说明「通用」的边界在哪</h2>
 *
 * <p>主模组把一个鬼的能力分散在几处：杀人规律在 {@code shouldAttackPlayer}（可通用借用），
 * 而替死、鬼域这类写在别的地方，各自有各自的入口。
 * 想全部零适配做不到；能做到的是<b>每一处都直接调本体，不重写</b>。
 */
public final class ScapegoatModule implements SpatialModule {

    public static final Identifier ID = new Identifier("smfsaddon", "scapegoat");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "替死";
    }

    @Override
    public double suppressionCost() {
        return 80.0D;
    }

    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new ScapegoatRule());
    }

    private static final class ScapegoatRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "scapegoat/substitute");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 500;
        }

        @Override
        public double revivalGain() {
            return 8.0D;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "替死",
                    "持有者本该受的致命伤，由它顶下来"
            );
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostOwnerDamagedEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            return event instanceof GhostOwnerDamagedEvent damaged && !damaged.isCancelled();
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostOwnerDamagedEvent damaged)) {
                return;
            }
            // 直接调本体的替死逻辑，返回 true 表示这次由它接了
            boolean handled = ScapegoatGhostItem.handleScapegoatPassiveSkill(
                    damaged.player(), damaged.source(), damaged.amount());
            if (handled) {
                damaged.cancel();
            }
        }
    }
}
