package com.example.smfsaddon.item;

import com.example.smfsaddon.ghost.module.GhostModuleContainer;
import com.example.smfsaddon.ghost.module.LingyiHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.PersistentHost;
import com.example.smfsaddon.ghost.module.host.SpatialHost;
import com.example.smfsaddon.ghost.module.host.TickHost;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * 物品宿主 —— 能力最尴尬的一种。
 *
 * <p>它有位置（跟着持有者跑），却没有身体；它能存档（物品 NBT），
 * 却<b>不会自己 tick</b>（靠 {@code Item#inventoryTick} 被持有者带着走）。
 *
 * <p>所以它实现 {@link SpatialHost}、{@link TickHost}、{@link PersistentHost}，
 * 但<b>不</b>实现 {@code EntityHost} —— 物品不是活物，持有者才是。
 * 这个区分很重要：一旦把持有者的能力算到物品头上，
 * 「同一个插件挂上去行为一致」这个前提就破了。
 *
 * <h2>生命周期与开销</h2>
 *
 * <p>每次开启都是一次分配，用完 {@link #flush()} 写回物品 NBT 后即丢弃。
 * 为了不让背包里几十个物品每 tick 都分配一次，调用方做了降频
 * （见 {@code GhostLanternItem#inventoryTick}）。
 * 这是物品宿主特有的成本，实体宿主没有这个问题。
 */
public final class ItemGhostHost implements LingyiHost, SpatialHost, TickHost, PersistentHost {

    /** 物品 NBT 里我们独占的子树，避免和其他模组的键打架 */
    private static final String KEY_ROOT = "SmfsAddonHost";

    private final ItemStack stack;
    private final World world;
    private final Entity holder;

    private final GhostModuleContainer modules = new GhostModuleContainer();
    private final NbtCompound pluginState = new NbtCompound();

    private boolean dirty = false;

    private ItemGhostHost(ItemStack stack, World world, Entity holder) {
        this.stack = stack;
        this.world = world;
        this.holder = holder;
    }

    /**
     * 从物品 NBT 还原一个宿主实例。
     *
     * <p>用完必须 {@link #flush()}，否则修改丢失。
     */
    public static ItemGhostHost open(ItemStack stack, World world, Entity holder) {
        ItemGhostHost host = new ItemGhostHost(stack, world, holder);

        NbtCompound root = stack.getSubNbt(KEY_ROOT);
        if (root != null) {
            if (root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
                host.modules.readNbt(root.getCompound("Modules"));
            }
            if (root.contains("Plugins", NbtElement.COMPOUND_TYPE)) {
                host.pluginState.copyFrom(root.getCompound("Plugins"));
            }
        }
        return host;
    }

    /**
     * 把改动写回物品 NBT。
     *
     * <p>判据演进过两次：
     *
     * <ol>
     *   <li>只看 {@link #markDirty()} —— 漏掉 {@code attach} 那条路径，
     *       模块一次都没落盘，复苏值每 10 tick 被清零；</li>
     *   <li>改成「有内容就写」 —— 正确但浪费：每次攻击都要序列化全部模块、
     *       并把物品标记为变过，服务端还要多发一个同步包；</li>
     *   <li>现在：结构增删、复苏值/强度变化、模块自报的 {@code markChanged()}，
     *       三者任一才写。</li>
     * </ol>
     *
     * @return 是否真的写了
     */
    public boolean flush() {
        // 判据是「内容真的变了」：结构增删由容器置位，复苏值/强度由 setter 置位，
        // 手写 custom() 的模块自己调 markChanged()。
        // 只读遍历（大多数攻击规律只是看一眼）不会置位，于是打一下不再重写整个 NBT。
        boolean changed = modules.consumeChanged();
        if (!dirty && !changed) {
            return false;
        }
        NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
        root.put("Modules", modules.writeNbt());
        root.put("Plugins", pluginState.copy());
        dirty = false;
        return true;
    }

    /**
     * 只读地数一下物品上挂了几条模块，供 tooltip 用。
     *
     * <p>不构造宿主、不分配容器 —— tooltip 每帧都可能被调用，不能有开销。
     */
    public static int moduleCount(ItemStack stack) {
        NbtCompound root = stack.getSubNbt(KEY_ROOT);
        if (root == null || !root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            return 0;
        }
        NbtCompound modulesNbt = root.getCompound("Modules");
        if (!modulesNbt.contains("Modules", NbtElement.LIST_TYPE)) {
            return 0;
        }
        return modulesNbt.getList("Modules", NbtElement.COMPOUND_TYPE).size();
    }

    /**
     * 只读地取出物品上的模块 ID 列表，供 tooltip 词条用。
     *
     * <p>同样不构造宿主、不反序列化整份容器 —— 只把 "Id" 字段捞出来。
     * tooltip 每帧都可能被调用，这里不能有分配之外的开销。
     */
    public static java.util.List<net.minecraft.util.Identifier> moduleIds(ItemStack stack) {
        NbtCompound root = stack.getSubNbt(KEY_ROOT);
        if (root == null || !root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            return java.util.List.of();
        }
        NbtCompound modulesNbt = root.getCompound("Modules");
        if (!modulesNbt.contains("Modules", NbtElement.LIST_TYPE)) {
            return java.util.List.of();
        }
        net.minecraft.nbt.NbtList list =
                modulesNbt.getList("Modules", NbtElement.COMPOUND_TYPE);
        java.util.List<net.minecraft.util.Identifier> ids = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            net.minecraft.util.Identifier id =
                    net.minecraft.util.Identifier.tryParse(entry.getString("Id"));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 读取物品融合的是哪只鬼（短名，如 {@code qiaomen_ghost}）。
     *
     * <p>来源是「本源规律」模块的绑定信息 —— 合入时写进去的。
     * 没有合并过本源规律的物品返回 {@code null}。
     */
    public static String boundGhostType(ItemStack stack) {
        NbtCompound root = stack.getSubNbt(KEY_ROOT);
        if (root == null || !root.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        NbtCompound modulesNbt = root.getCompound("Modules");
        if (!modulesNbt.contains("Modules", NbtElement.LIST_TYPE)) {
            return null;
        }
        net.minecraft.nbt.NbtList list =
                modulesNbt.getList("Modules", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (!"smfsaddon:native_rule".equals(entry.getString("Id"))) {
                continue;
            }
            NbtCompound custom = entry.getCompound("Custom");
            java.util.List<String> paths = new java.util.ArrayList<>();

            String joined = custom.getString("GhostTypes");
            if (!joined.isEmpty()) {
                for (String part : joined.split(",")) {
                    if (!part.isBlank()) {
                        net.minecraft.util.Identifier id =
                                net.minecraft.util.Identifier.tryParse(part.trim());
                        if (id != null) {
                            paths.add(id.getPath());
                        }
                    }
                }
            }
            if (paths.isEmpty()) {
                net.minecraft.util.Identifier id =
                        net.minecraft.util.Identifier.tryParse(custom.getString("GhostType"));
                if (id != null) {
                    paths.add(id.getPath());
                }
            }
            return paths.isEmpty() ? null : String.join(",", paths);
        }
        return null;
    }

    /**
     * 物品融合的全部鬼种（短名）。
     */
    public static java.util.List<String> boundGhostTypes(ItemStack stack) {
        String joined = boundGhostType(stack);
        if (joined == null || joined.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.of(joined.split(","));
    }

    // ==================== LingyiHost ====================

    @Override
    public <T extends HostCapability> Optional<T> capability(Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }

    @Override
    public GhostModuleContainer modules() {
        return modules;
    }

    // ==================== 能力实现 ====================

    @Override
    public World world() {
        return world;
    }

    @Override
    public Vec3d position() {
        return holder.getPos();
    }

    @Override
    public long ticks() {
        return world.getTime();
    }

    @Override
    public NbtCompound state() {
        dirty = true;
        return pluginState;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }
}
