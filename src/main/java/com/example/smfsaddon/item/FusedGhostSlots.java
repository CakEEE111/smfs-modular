package com.example.smfsaddon.item;

import com.xie.smfs.common.events.PlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把「物品上融合的厉鬼」投影成鬼槽，供本体 API 的调用方看见。
 *
 * <h2>为什么需要这一层</h2>
 *
 * <p>附属模组判断「玩家有没有某只鬼」有两条路：
 *
 * <pre>
 * PlayerEvents.hasGhostType(player, 鬼种)          → 我们的混入已经放行
 * TameableItemAPI.isGhostSlotOccupied + getGhostTypeInSlot  → 直接扫鬼槽，看不见融合物品
 * </pre>
 *
 * <p>实测「梦游鬼」那个附属走的是第二条：它 `for (slot = 0; slot &lt; 10; slot++)`
 * 逐个问 API「这格有没有鬼、是什么鬼」。融合在剑上的鬼没有实体槽位，
 * 于是它的被动（夜里失控）永远不会触发 —— 跟上一条混入一点关系都没有。
 *
 * <h2>做法</h2>
 *
 * <p>不改编物品，也不改本体：往<b>空闲的真实槽位</b>上投影。
 * 第 k 只融合鬼占用「第 k 个空槽」，两个调用（问占用、问鬼种）用同一张映射，
 * 因此附属看到的是自洽的一组槽位。
 *
 * <p>只影响 API 的调用方。本体内部逻辑走的是 {@code PlayerEvents} 自己的方法，
 * 而它对 API 的依赖只有 {@code hasTamedGhost} / {@code getGhostType}（这两条我们不碰），
 * 所以本体原有行为不变。
 *
 * <p>每 tick 最多重建一次：{@code hasGhostType} 这类判定在附属里是每 tick 循环调用，
 * 每次扫 41 格背包不可接受。
 */
public final class FusedGhostSlots {

    /** 一只被投影的鬼：类型 + 承载它的物品栈 */
    public record Bound(String ghostType, ItemStack stack) {
    }

    private record Cache(long time, List<Bound> ghosts, Map<Integer, String> bySlot) {
    }

    private static final Map<UUID, Cache> CACHE = new ConcurrentHashMap<>();

    private FusedGhostSlots() {
    }

    /**
     * 玩家身上所有融合鬼，按背包顺序去重。
     */
    public static List<Bound> carried(PlayerEntity player) {
        return cache(player).ghosts();
    }

    /**
     * 槽位号 → 鬼种。只有空闲的真实槽位才会出现在这里。
     */
    public static Map<Integer, String> bySlot(PlayerEntity player) {
        return cache(player).bySlot();
    }

    /** 该槽位是否被投影 */
    public static boolean isVirtual(PlayerEntity player, int slot) {
        return cache(player).bySlot().containsKey(slot);
    }

    /** 该槽位上的融合物品（真实槽位返回 {@code null}） */
    public static ItemStack stackAt(PlayerEntity player, int slot) {
        String type = cache(player).bySlot().get(slot);
        if (type == null) {
            return null;
        }
        for (Bound bound : cache(player).ghosts()) {
            if (bound.ghostType().equals(type)) {
                return bound.stack();
            }
        }
        return null;
    }

    private static Cache cache(PlayerEntity player) {
        if (player == null) {
            return new Cache(0L, List.of(), Map.of());
        }
        long now = player.getWorld() == null ? 0L : player.getWorld().getTime();
        UUID id = player.getUuid();
        Cache cached = CACHE.get(id);
        if (cached != null && cached.time() == now) {
            return cached;
        }
        Cache rebuilt = build(player, now);
        CACHE.put(id, rebuilt);
        return rebuilt;
    }

    /** 玩家退场时释放，别把 UUID 攒起来 */
    public static void forget(UUID uuid) {
        CACHE.remove(uuid);
    }

    private static Cache build(PlayerEntity player, long now) {
        Map<String, ItemStack> firstStack = new LinkedHashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (String ghostType : ItemGhostHost.boundGhostTypes(stack)) {
                firstStack.putIfAbsent(ghostType, stack);
            }
        }

        List<Bound> ghosts = new ArrayList<>(firstStack.size());
        firstStack.forEach((type, stack) -> ghosts.add(new Bound(type, stack)));

        // 空闲槽位按升序取，和真实占用互不重叠
        List<Integer> freeSlots = new ArrayList<>();
        for (int slot = 0; slot < PlayerEvents.GHOST_SLOT_COUNT; slot++) {
            if (!PlayerEvents.isGhostSlotOccupied(player, slot)) {
                freeSlots.add(slot);
            }
        }

        Map<Integer, String> bySlot = new HashMap<>();
        int count = Math.min(ghosts.size(), freeSlots.size());
        for (int i = 0; i < count; i++) {
            bySlot.put(freeSlots.get(i), ghosts.get(i).ghostType());
        }
        return new Cache(now, List.copyOf(ghosts), Map.copyOf(bySlot));
    }
}
