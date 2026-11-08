package com.example.smfsaddon.ghost.module;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 宿主上的模块集合。
 *
 * <p>纯存储，不含调度逻辑 —— 调度在 {@link GhostModuleDispatcher}。
 * 使用有序 Map 保证序列化顺序稳定，便于对比存档差异。
 */
public final class GhostModuleContainer {

    private static final String KEY_MODULES = "Modules";

    /**
     * 存档格式版本。
     *
     * <p>现在还是 1，没人读它。加它的唯一原因是：将来要改条目结构时，
     * {@link #readNbt} 能据版本号判断「这是旧格式」，而不是靠猜字段在不在。
     * 存档一旦发出去就无法回收，这个字段是唯一廉价的后悔药。
     */
    private static final int SCHEMA_VERSION = 1;

    private boolean structuralChanged;

    private final Map<Identifier, GhostModuleData> modules = new LinkedHashMap<>();

    public GhostModuleData get(Identifier moduleId) {
        return modules.get(moduleId);
    }

    public boolean has(Identifier moduleId) {
        return modules.containsKey(moduleId);
    }

    /**
     * 是否同时携带全部指定模块。
     *
     * <p>协同的激活判据。空集合返回 {@code true}，但协同本身不允许空需求
     * （见 {@code GhostCombo.Builder#build()}）。
     */
    public boolean hasAll(Collection<Identifier> moduleIds) {
        for (Identifier moduleId : moduleIds) {
            if (!modules.containsKey(moduleId)) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return modules.size();
    }

    public boolean isEmpty() {
        return modules.isEmpty();
    }

    public Collection<GhostModuleData> all() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public void put(GhostModuleData data) {
        modules.put(data.moduleId(), data);
        structuralChanged = true;
    }

    public GhostModuleData remove(Identifier moduleId) {
        GhostModuleData removed = modules.remove(moduleId);
        if (removed != null) {
            structuralChanged = true;
        }
        return removed;
    }

    public void clear() {
        if (!modules.isEmpty()) {
            structuralChanged = true;
        }
        modules.clear();
    }

    /**
     * 取走并清除「自上次落盘以来有没有变过」。
     *
     * <p>判定分两层：容器结构（增删模块）与模块自身（复苏值、强度、私有状态）。
     * 只读遍历不会置位 —— 这是「打一下但什么都没变就不写 NBT」的前提。
     */
    public boolean consumeChanged() {
        boolean changed = structuralChanged;
        structuralChanged = false;
        for (GhostModuleData data : modules.values()) {
            changed |= data.consumeChanged();
        }
        return changed;
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        if (modules.isEmpty()) {
            return nbt;
        }
        nbt.putInt("v", SCHEMA_VERSION);
        NbtList list = new NbtList();
        for (GhostModuleData data : modules.values()) {
            list.add(data.writeNbt());
        }
        nbt.put(KEY_MODULES, list);
        return nbt;
    }

    public void readNbt(NbtCompound nbt) {
        modules.clear();
        if (!nbt.contains(KEY_MODULES, NbtElement.LIST_TYPE)) {
            return;
        }
        // 目前只有 v=1 一种格式；将来出现 v=2 时在这里分支迁移，
        // 而不是去猜每个条目缺了哪个字段。
        int version = nbt.contains("v") ? nbt.getInt("v") : 1;
        if (version > SCHEMA_VERSION) {
            // 存档比当前代码新：不猜测、不覆盖，保留原样交给上层决定
            return;
        }
        NbtList list = nbt.getList(KEY_MODULES, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            GhostModuleData data = GhostModuleData.readNbt(list.getCompound(i));
            if (data != null) {
                modules.put(data.moduleId(), data);
            }
        }
    }
}
