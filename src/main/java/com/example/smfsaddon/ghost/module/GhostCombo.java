package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.HostCapability;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 协同：拼图拼上之后产生的东西。
 *
 * <p>之前只有互斥（两块拼不上），没有协同（两块拼上会多出什么）。
 * 只有负向约束的话，模块化最多叫「插件化」，谈不上「拼图」——
 * 因为玩家永远感受不到「这只鬼被拼完整了」的那个瞬间。
 *
 * <h2>为什么不新造一套机制</h2>
 *
 * <p>协同本身就是一种 {@link GhostModule}：它有 id、有规则、有代价、能进图鉴。
 * 唯一的区别是它不由人手动挂载，而是由调度器根据「宿主是否同时具备所需模块」
 * 自动装上 / 卸下。
 *
 * <p>这样优先级、互斥组、元规则、复苏代价、图鉴记录，全部原样复用，
 * 不需要第二套调度逻辑，也不会出现「协同规则和普通规则行为不一致」的裂缝。
 */
public final class GhostCombo implements GhostModule {

    private final Identifier id;
    private final Set<Identifier> requiredModules;
    private final List<GhostRule> rules;
    private final Set<Class<? extends HostCapability>> requiredCapabilities;

    private GhostCombo(Builder builder) {
        this.id = builder.id;
        this.requiredModules = Set.copyOf(builder.requiredModules);
        this.rules = List.copyOf(builder.rules);
        this.requiredCapabilities = Set.copyOf(builder.requiredCapabilities);
    }

    public static Builder of(Identifier id) {
        return new Builder(id);
    }

    @Override
    public Identifier id() {
        return id;
    }

    /**
     * 必须同时存在的模块 ID。
     */
    public Set<Identifier> requiredModules() {
        return requiredModules;
    }

    @Override
    public Set<Class<? extends HostCapability>> requires() {
        return requiredCapabilities;
    }

    @Override
    public List<GhostRule> rules() {
        return rules;
    }

    /**
     * 协同不自然复苏：它靠被「拼起来」存在，不靠时间累积。
     */
    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    public static final class Builder {

        private final Identifier id;
        private final Set<Identifier> requiredModules = new LinkedHashSet<>();
        private final List<GhostRule> rules = new ArrayList<>();
        private final Set<Class<? extends HostCapability>> requiredCapabilities = new LinkedHashSet<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        /**
         * 声明「这些模块同时存在时，我才会出现」。
         *
         * <p>至少要写一个，否则调度器每 tick 都会尝试挂上它。
         */
        public Builder needs(Identifier... moduleIds) {
            for (Identifier moduleId : moduleIds) {
                requiredModules.add(moduleId);
            }
            return this;
        }

        /**
         * 声明宿主必须具备的能力。
         *
         * <p>通常与所需模块的能力一致；写清楚可以避免
         * 在奇怪的宿主上被误挂。
         */
        public Builder needsCapability(Class<? extends HostCapability> capability) {
            requiredCapabilities.add(capability);
            return this;
        }

        public Builder rules(GhostRule... comboRules) {
            for (GhostRule rule : comboRules) {
                rules.add(rule);
            }
            return this;
        }

        public GhostCombo build() {
            if (requiredModules.isEmpty()) {
                throw new IllegalStateException(
                        "combo " + id + " must declare at least one required module"
                );
            }
            if (rules.isEmpty()) {
                throw new IllegalStateException("combo " + id + " has no rules");
            }
            return new GhostCombo(this);
        }
    }
}
