package com.example.smfsaddon.ghost.module;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

/**
 * 单个模块实例的运行时数据。
 *
 * <p>模块定义（{@link GhostModule}）是单例，模块数据才是每只鬼各自持有的东西。
 * 三部分状态：
 * <ul>
 *   <li>{@code intensity} 灵异强度：该模块对宿主行为的权重，规则可读取；</li>
 *   <li>{@code revival} 复苏值：模块被激活后累积，能力消耗它；</li>
 *   <li>{@code custom} 自定义 NBT：规则自己的记忆，例如上次触发时间、目标 UUID。</li>
 * </ul>
 */
public final class GhostModuleData {

    private final Identifier moduleId;

    private double intensity;
    private double revival;
    private final NbtCompound custom;

    /** 内容自上次落盘以来是否变过 */
    private boolean changed;

    public GhostModuleData(Identifier moduleId, double intensity, double revival, NbtCompound custom) {
        this.moduleId = moduleId;
        this.intensity = intensity;
        this.revival = revival;
        this.custom = custom == null ? new NbtCompound() : custom;
    }

    public static GhostModuleData initial(Identifier moduleId, double intensity, double revival) {
        return new GhostModuleData(moduleId, intensity, revival, new NbtCompound());
    }

    public Identifier moduleId() {
        return moduleId;
    }

    public double intensity() {
        return intensity;
    }

    public void setIntensity(double value) {
        if (value == this.intensity) {
            return;
        }
        this.intensity = value;
        markChanged();
    }

    public double revival() {
        return revival;
    }

    public void setRevival(double value) {
        if (value == this.revival) {
            return;
        }
        this.revival = value;
        markChanged();
    }

    public void addRevival(double amount) {
        if (amount == 0.0D) {
            return;
        }
        this.revival += amount;
        markChanged();
    }

    /**
     * 规则私有的持久化状态。
     */
    public NbtCompound custom() {
        return custom;
    }

    /**
     * 手写 {@link #custom()} 的模块必须显式调用 ——
     * NbtCompound 的 put 我们拦不到，只能靠调用方声明。
     */
    public void markChanged() {
        changed = true;
    }

    /** 取走并清除变更标记 */
    public boolean consumeChanged() {
        boolean value = changed;
        changed = false;
        return value;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Id", moduleId.toString());
        nbt.putDouble("Intensity", intensity);
        nbt.putDouble("Revival", revival);
        nbt.put("Custom", custom.copy());
        return nbt;
    }

    /**
     * 从 NBT 还原。
     *
     * @return 标识非法时返回 {@code null}，调用方应跳过该项
     */
    public static GhostModuleData readNbt(NbtCompound nbt) {
        Identifier id = Identifier.tryParse(nbt.getString("Id"));
        if (id == null) {
            return null;
        }
        NbtCompound custom = nbt.contains("Custom", NbtElement.COMPOUND_TYPE)
                ? nbt.getCompound("Custom").copy()
                : new NbtCompound();
        return new GhostModuleData(
                id,
                nbt.getDouble("Intensity"),
                nbt.getDouble("Revival"),
                custom
        );
    }
}
