package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.SpatialModule;
import com.example.smfsaddon.ghost.module.event.GhostAttackRuleEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * 环境类机制的公共壳子。
 *
 * <h2>为什么要有这一层</h2>
 *
 * <p>本体 43 只鬼的行为都写死在各自的类里，没有"可复用的单元"这种东西。
 * 想让 A 鬼拥有 B 鬼的手段，只能把那段判定抽出来重写成能挂任意宿主的模块。
 *
 * <p>而这类判定有一个共同形状：<b>只看玩家此刻的状态</b>（朝向、水位、光照、脚下方块），
 * 不读鬼自己的记忆。所以抽一次壳子，每个机制只写一行条件就够了。
 *
 * <p>反过来说，凡是依赖"鬼自己记了什么"的规律（雾鬼的移动计时、静悄悄的冷却）
 * 都不适合做成这一层 —— 换个宿主实例，那些记忆就是空的，装上也永远不触发。
 */
abstract class EnvGateModule implements SpatialModule {

    private final Identifier id;
    private final String displayName;
    private final String rulePath;
    private final double cost;

    protected EnvGateModule(Identifier id, String displayName, String rulePath, double cost) {
        this.id = id;
        this.displayName = displayName;
        this.rulePath = rulePath;
        this.cost = cost;
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * 纯条件判定，不靠复苏度推进 —— 和踩人鬼一样，中了就是中了。
     */
    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public double suppressionCost() {
        return cost;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new GateRule());
    }

    /**
     * 目标玩家此刻是否满足这条规律。
     *
     * @param player 被问的目标
     * @param self   宿主鬼，可能为 {@code null}
     * @param world  所在世界
     */
    protected abstract boolean test(PlayerEntity player, GhostEntity self, World world);

    /** 图鉴里那句"什么情况下会死" */
    protected abstract String condition();

    private final class GateRule implements GhostRule {

        private final Identifier ruleId = new Identifier(id.getNamespace(), rulePath);

        @Override
        public Identifier id() {
            return ruleId;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(displayName, condition());
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostAttackRuleEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackRuleEvent attack)) {
                return false;
            }
            World world = context.world();
            PlayerEntity player = attack.player();
            if (world == null || player == null) {
                return false;
            }
            return test(player, context.ghost(), world);
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            // 只表态允许，不否决 —— 多一条杀你的理由，不是抹掉宿主自己的判断
            ((GhostAttackRuleEvent) event).allow();
        }
    }
}
