package com.example.smfsaddon.haunt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/**
 * 世界存档里的「鬼方块」表：坐标 → 里面关着哪只鬼。
 *
 * <h2>为什么不做成新方块</h2>
 *
 * <p>鬼方块要和原地形一模一样 —— 老家的平原还是草方块，地底还是原石。
 * 做成新方块就得给每个被替换的方块配一套模型和贴图，工程量和美术成本都不划算。
 *
 * <p>所以这里只往区块存档里挂数据：<b>方块本身没变，变的是它下面的位置被记了一笔</b>。
 * 存储用维度级的 {@link PersistentState}，随世界保存、存档迁移也跟着走。
 *
 * <h2>字段</h2>
 *
 * <ul>
 *   <li>{@code ghostType} 里面关着哪只鬼；</li>
 *   <li>{@code judged} 上次判定的 tick，用来控制节奏；</li>
 *   <li>{@code revival} 复苏进度，满了就自己破封；</li>
 *   <li>{@code warned} 是否已经对玩家播过遇鬼音，离开范围后复位。</li>
 * </ul>
 */
public final class HauntStore extends PersistentState {

    private static final String ID = "smfsaddon_haunts";

    private final Map<BlockPos, Entry> haunts = new HashMap<>();

    public static HauntStore of(net.minecraft.server.world.ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                HauntStore::fromNbt, HauntStore::new, ID);
    }

    public Map<BlockPos, Entry> haunts() {
        return haunts;
    }

    public Entry get(BlockPos pos) {
        return haunts.get(pos);
    }

    public void put(BlockPos pos, Entry entry) {
        haunts.put(pos, entry);
        markDirty();
    }

    public void remove(BlockPos pos) {
        if (haunts.remove(pos) != null) {
            markDirty();
        }
    }

    /** 一条鬼方块的运行时状态 */
    public static final class Entry {

        public final Identifier ghostType;
        public long judgedAt;
        public int revival;
        public boolean warned;

        public Entry(Identifier ghostType) {
            this.ghostType = ghostType;
        }

        NbtCompound write() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Type", ghostType.toString());
            nbt.putLong("JudgedAt", judgedAt);
            nbt.putInt("Revival", revival);
            nbt.putBoolean("Warned", warned);
            return nbt;
        }

        static Entry read(NbtCompound nbt) {
            Identifier type = Identifier.tryParse(nbt.getString("Type"));
            if (type == null) {
                return null;
            }
            Entry entry = new Entry(type);
            entry.judgedAt = nbt.getLong("JudgedAt");
            entry.revival = nbt.getInt("Revival");
            entry.warned = nbt.getBoolean("Warned");
            return entry;
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<BlockPos, Entry> item : haunts.entrySet()) {
            NbtCompound one = item.getValue().write();
            BlockPos pos = item.getKey();
            one.putInt("X", pos.getX());
            one.putInt("Y", pos.getY());
            one.putInt("Z", pos.getZ());
            list.add(one);
        }
        nbt.put("Haunts", list);
        return nbt;
    }

    private static HauntStore fromNbt(NbtCompound nbt) {
        HauntStore store = new HauntStore();
        NbtList list = nbt.getList("Haunts", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < list.size(); index++) {
            NbtCompound one = list.getCompound(index);
            Entry entry = Entry.read(one);
            if (entry == null) {
                continue;
            }
            store.haunts.put(new BlockPos(one.getInt("X"), one.getInt("Y"), one.getInt("Z")), entry);
        }
        return store;
    }
}
