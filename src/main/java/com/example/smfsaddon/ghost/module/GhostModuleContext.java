package com.example.smfsaddon.ghost.module;

import com.example.smfsaddon.ghost.module.host.EntityHost;
import com.example.smfsaddon.ghost.module.host.GhostEntityHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.SpatialHost;
import com.example.smfsaddon.ghost.module.host.TickHost;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.Optional;

/**
 * 单次模块执行的上下文。
 *
 * <p>规则通过它访问宿主能力与模块状态，<b>不直接依赖具体的鬼实体类型</b>。
 *
 * <h2>怎么取宿主</h2>
 *
 * <p>优先用 {@link #capability(Class)} 声明式取能力；{@link #ghost()} 与
 * {@link #entity()} 只是便捷方法，取不到时返回 {@code null}。
 * 新写规则时尽量走能力查询 —— 那是唯一能跨宿主复用的写法。
 */
public final class GhostModuleContext {

    private final LingyiHost host;
    private final GhostModule module;
    private final GhostModuleData data;

    public GhostModuleContext(LingyiHost host, GhostModule module, GhostModuleData data) {
        this.host = Objects.requireNonNull(host, "host");
        this.module = Objects.requireNonNull(module, "module");
        this.data = Objects.requireNonNull(data, "data");
    }

    public LingyiHost host() {
        return host;
    }

    public GhostModule module() {
        return module;
    }

    public GhostModuleData data() {
        return data;
    }

    /**
     * 查询宿主能力。
     */
    public <T extends HostCapability> Optional<T> capability(Class<T> type) {
        return host.capability(type);
    }

    /**
     * 宿主若是鬼实体则返回，否则 {@code null}。
     */
    public GhostEntity ghost() {
        return capability(GhostEntityHost.class)
                .map(GhostEntityHost::ghost)
                .orElse(null);
    }

    /**
     * 宿主若是活物则返回，否则 {@code null}。
     *
     * <p>这是比 {@link #ghost()} 更通用的写法：同样一段逻辑，
     * 将来挂在玩家或傀儡上也能用。
     */
    public LivingEntity entity() {
        return capability(EntityHost.class)
                .map(EntityHost::living)
                .orElse(null);
    }

    public World world() {
        return capability(SpatialHost.class)
                .map(SpatialHost::world)
                .orElse(null);
    }

    public Vec3d position() {
        return capability(SpatialHost.class)
                .map(SpatialHost::position)
                .orElse(null);
    }

    public long ticks() {
        return capability(TickHost.class)
                .map(TickHost::ticks)
                .orElse(0L);
    }

    public double intensity() {
        return data.intensity();
    }

    public double revival() {
        return data.revival();
    }

    public double maxRevival() {
        return module.maxRevival();
    }

    /**
     * 复苏值是否已经顶到上限。
     *
     * <p>框架只提供这个状态，含义由内容决定 ——
     * 可以是规则变强、可以是鬼失控、也可以什么都不做。
     */
    public boolean revived() {
        return data.revival() >= module.maxRevival();
    }

    /**
     * 增加复苏值，自动按模块上限截断。
     *
     * <p>宿主不允许刺激时（{@link LingyiHost#allowsRevivalStimulation()} 为 false）
     * 本次调用直接忽略。
     */
    public void addRevival(double amount) {
        if (!host.allowsRevivalStimulation()) {
            return;
        }
        double next = data.revival() + amount;
        double max = module.maxRevival();
        if (next > max) {
            next = max;
        }
        if (next < 0) {
            next = 0;
        }
        data.setRevival(next);
    }

    /**
     * 规则私有的持久化状态，随宿主存档一起保存。
     */
    /**
     * 声明"我改了 {@link #state()}"。
     *
     * <p>NbtCompound 的 put 我们拦不到，所以模块手写状态后必须自己喊一声，
     * 否则物品宿主 flush 时会认为没变、不落盘，状态下次 open() 就丢了。
     */
    public void markChanged() {
        data.markChanged();
    }

    public NbtCompound state() {
        return data.custom();
    }
}
