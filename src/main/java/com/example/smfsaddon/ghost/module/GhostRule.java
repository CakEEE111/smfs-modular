package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.event.GhostEvent;
import net.minecraft.util.Identifier;

/**
 * 灵异规则。
 *
 * <p>规则只回答两个问题：<b>关不关心这个事件</b>、<b>现在满不满足条件</b>，
 * 以及条件满足时<b>做什么</b>。
 *
 * <p>规则与能力的区分：
 * <ul>
 *   <li>规则决定「何时触发」——被动、由事件驱动；</li>
 *   <li>能力决定「产生什么效果」——主动、由复苏值驱动。</li>
 * </ul>
 *
 * <h2>组合的三个旋钮</h2>
 *
 * <p>一只鬼身上挂多条规则时，能不能组合出稳定行为，取决于三件事。
 * 它们都在本接口里声明，不散落在实现代码中：
 *
 * <ol>
 *   <li>{@link #priority()} —— 同时满足条件时谁先执行，数字大的先。</li>
 *   <li>{@link #exclusionGroup()} —— 同组内只能有一条触发，
 *       用来表达「这两条规则互斥」。</li>
 *   <li>{@link #revivalGain()} —— 触发一次模块复苏值涨多少，
 *       代价按规则算，不按鬼算。</li>
 * </ol>
 *
 * <p>规则实现应当是无状态的。需要跨 tick 记忆的东西写进 {@code context.state()}。
 */
public interface GhostRule {

    /**
     * 规则在所属模块内的唯一标识。
     *
     * <p>该 ID 同时是全局调参的键，见 {@link GhostRuleTuning}。
     */
    Identifier id();

    /**
     * 图鉴条目。返回 {@code null} 表示这条规则不进入图鉴（内部规则）。
     *
     * <p>只有出现在图鉴里的规则，才会在玩家观察到时被记录。
     */
    default GhostCodexEntry codex() {
        return null;
    }

    /**
     * 执行优先级，数字大的先执行。默认 0。
     *
     * <p>只在同一次事件里有多条规则同时满足时才有意义。
     */
    default int priority() {
        return 0;
    }

    /**
     * 互斥组标识，返回 {@code null} 表示不参与互斥。
     *
     * <p>同一事件中，同组内优先级最高的那条触发后，其余同组规则跳过。
     * 这是表达「规则互相制约」的手段 —— 原著里规则冲突本身就是
     * 最危险的时刻，不该被写成「两条都生效」。
     */
    default Identifier exclusionGroup() {
        return null;
    }

    /**
     * 触发一次的复苏代价（复苏值增量）。默认 0。
     *
     * <p>越强的规则应当给得越高 —— 代价是按规则算的。
     * 实际增量会再乘以 {@link GhostRuleTuning} 里的全局倍率，便于统一调平衡。
     */
    default double revivalGain() {
        return 0.0D;
    }

    /**
     * 是否关注指定事件。
     *
     * <p>此方法只做类型筛选，不应产生任何副作用。
     * 引擎会在每个事件上调用它，因此必须是廉价的判断。
     */
    boolean supports(Identifier eventId);

    /**
     * 当前是否满足触发条件。
     *
     * <p>此方法只做判断，不应修改世界状态。
     */
    boolean canTrigger(GhostModuleContext context, GhostEvent event);

    /**
     * 执行规则逻辑。
     *
     * <p>引擎保证调用前已完成 {@link #supports} 与 {@link #canTrigger} 检查。
     */
    void onTrigger(GhostModuleContext context, GhostEvent event);
}
