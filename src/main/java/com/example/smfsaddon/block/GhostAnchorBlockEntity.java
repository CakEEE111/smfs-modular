package com.example.smfsaddon.block;

import com.example.smfsaddon.ghost.module.GhostModuleContainer;
import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.LingyiHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.PersistentHost;
import com.example.smfsaddon.ghost.module.host.SpatialHost;
import com.example.smfsaddon.ghost.module.host.TickHost;
import com.example.smfsaddon.ghost.module.impl.SpatialPressureModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * 灵异锚点的方块实体 —— 一个非实体宿主。
 *
 * <p>它和 {@code GhostEntity} 共享同一套模块系统，但能力不同：
 * <ul>
 *   <li>有 {@link SpatialHost} —— 有位置，能做范围判定；</li>
 *   <li>有 {@link TickHost} —— 由方块 ticker 驱动；</li>
 *   <li>有 {@link PersistentHost} —— 方块实体自己有 NBT；</li>
 *   <li><b>没有</b> {@code EntityHost} / {@code GhostEntityHost} —— 它不会走、不会攻击。</li>
 * </ul>
 *
 * <p>因此只声明空间能力的模块能挂上来，需要鬼特性的模块会被拒绝装载。
 * 这就是「拼图咬合」在非实体宿主上的样子。
 */
public class GhostAnchorBlockEntity extends BlockEntity
        implements LingyiHost, SpatialHost, TickHost, PersistentHost {

    private final GhostModuleContainer modules = new GhostModuleContainer();

    /** 给插件用的持久化抽屉 */
    private final NbtCompound pluginState = new NbtCompound();

    /** 只在运行时存在：首次 tick 时挂上示例模块 */
    private boolean initialized = false;

    public GhostAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModHostBlocks.GHOST_ANCHOR_ENTITY, pos, state);
    }

    /**
     * 由 {@link GhostAnchorBlock#getTicker} 每 tick 调用。
     */
    public void serverTick() {
        if (world == null || world.isClient) {
            return;
        }
        if (!initialized) {
            initialized = true;
            // 已存档的方块读档后模块已存在，这里不会重复挂
            if (!modules.has(SpatialPressureModule.ID)) {
                GhostModuleDispatcher.attach(this, SpatialPressureModule.ID);
            }
        }
        GhostModuleDispatcher.tick(this);
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
        return Vec3d.ofCenter(pos);
    }

    @Override
    public long ticks() {
        return world == null ? 0L : world.getTime();
    }

    @Override
    public NbtCompound state() {
        return pluginState;
    }

    // markDirty() 由 BlockEntity 自带，直接满足 PersistentHost 的约定

    // ==================== 存档 ====================

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtCompound modulesNbt = modules.writeNbt();
        if (!modulesNbt.isEmpty()) {
            nbt.put("Modules", modulesNbt);
        }
        if (!pluginState.isEmpty()) {
            nbt.put("Plugins", pluginState.copy());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Modules", NbtElement.COMPOUND_TYPE)) {
            modules.readNbt(nbt.getCompound("Modules"));
        }
        if (nbt.contains("Plugins", NbtElement.COMPOUND_TYPE)) {
            NbtCompound loaded = nbt.getCompound("Plugins");
            pluginState.copyFrom(loaded);
        }
    }
}
