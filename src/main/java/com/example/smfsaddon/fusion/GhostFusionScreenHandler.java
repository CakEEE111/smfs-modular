package com.example.smfsaddon.fusion;

import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 灵异装置的服务端逻辑：左槽放物品，右槽放厉鬼。
 *
 * <p>界面里做三件事：读右侧厉鬼的规律清单、用玩家的灵异值算压制、
 * 点「合入」把规律移植进左侧物品。
 *
 * <h2>为什么合入要按钮而不是自动</h2>
 *
 * <p>压制不足要掷骰并反噬。自动执行会变成「放进去就开始扣血」，
 * 玩家连发生了什么都不知道；按钮让「赌不赌」明确成为一次选择。
 */
public class GhostFusionScreenHandler extends ScreenHandler {

    public static final int STATE_EMPTY = 0;
    public static final int STATE_SAFE = 1;
    public static final int STATE_RISKY = 2;

    private final PlayerInventory playerInventory;
    private final SimpleInventory targetSlot = new SimpleInventory(1);
    private final SimpleInventory ghostSlot = new SimpleInventory(1);
    private final ArrayPropertyDelegate properties = new ArrayPropertyDelegate(5);

    private double suppression = 0.0D;
    private double required = 0.0D;
    private double chance = 1.0D;

    public GhostFusionScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(FusionScreens.TYPE, syncId);
        this.playerInventory = playerInventory;

        // 左：目标物品
        this.addSlot(new Slot(targetSlot, 0, 62, 24));
        // 右：厉鬼容器
        this.addSlot(new Slot(ghostSlot, 0, 98, 24) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof BaseGhostEyeItem;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addProperties(properties);
        this.targetSlot.addListener(inventory -> this.recompute());
        this.ghostSlot.addListener(inventory -> this.recompute());
        this.recompute();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /**
     * 关闭界面时把槽里的东西还给玩家。
     *
     * <p>不写这个方法，直接关界面会把放在槽里的物品<b>吞掉</b> ——
     * 两个 SimpleInventory 不归玩家背包管，没人回收就消失了。
     */
    @Override
    public void onClosed(PlayerEntity player) {
        returnStack(player, targetSlot);
        returnStack(player, ghostSlot);
        super.onClosed(player);
    }

    private void returnStack(PlayerEntity player, SimpleInventory inventory) {
        ItemStack stack = inventory.getStack(0);
        if (stack.isEmpty()) {
            return;
        }
        inventory.setStack(0, ItemStack.EMPTY);
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        this.recompute();
    }

    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();
        PlayerEntity player = playerInventory.player;
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        // 灵异值会变，每秒重算一次显示
        if (player.getWorld().getTime() % 20 == 0) {
            this.recompute();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        boolean moved;
        if (index < 2) {
            moved = this.insertItem(stack, 2, this.slots.size(), true);
        } else if (stack.getItem() instanceof BaseGhostEyeItem) {
            moved = this.insertItem(stack, 1, 2, false);
            if (!moved) {
                moved = this.insertItem(stack, 0, 1, false);
            }
        } else {
            moved = this.insertItem(stack, 0, 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        slot.onTakeItem(player, stack);
        return original;
    }

    // ==================== 计算 ====================

    private void recompute() {
        PlayerEntity player = playerInventory.player;
        if (player == null) {
            return;
        }
        this.suppression = FusionFormula.suppression(player);

        Identifier ghostType = resolveGhostEntityType();
        List<Identifier> modules = ghostType == null
                ? List.of()
                : GhostGrafting.modulesOfGhost(ghostType);

        int existing = com.example.smfsaddon.item.ItemGhostHost
                .boundGhostTypes(targetSlot.getStack(0)).size();
        double requirement = FusionFormula.requiredFor(modules, existing);
        this.required = requirement;

        int state;
        if (targetSlot.getStack(0).isEmpty() || requirement <= 0.0D) {
            state = STATE_EMPTY;
            this.chance = 1.0D;
        } else if (suppression + 1.0E-6D >= requirement) {
            state = STATE_SAFE;
            this.chance = 1.0D;
        } else {
            state = STATE_RISKY;
            double ratio = suppression / requirement;
            this.chance = Math.max(0.05D, Math.min(0.75D, ratio * ratio * 0.9D));
        }

        properties.set(0, (int) Math.round(suppression * 10.0D));
        properties.set(1, (int) Math.round(requirement * 10.0D));
        properties.set(2, state);
        properties.set(3, (int) Math.round(chance * 100.0D));
        properties.set(4, modules.size());
    }

    /**
     * 列出装不上的模块。
     *
     * <p>不告诉玩家的话，只会看到「明明有两条规律，怎么只进了一条」——
     * 而原因是那条规律要求宿主是鬼，物品根本不满足。
     */
    private static String skippedNames(ItemStack target, List<Identifier> modules, PlayerEntity player) {
        com.example.smfsaddon.item.ItemGhostHost probe =
                com.example.smfsaddon.item.ItemGhostHost.open(target, player.getWorld(), player);
        StringBuilder builder = new StringBuilder();
        for (Identifier moduleId : modules) {
            GhostModule module = GhostModuleRegistry.get(moduleId);
            if (module == null || probe.supportsCapabilities(module)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(module.displayName());
        }
        return builder.length() == 0 ? "" : " §8跳过：" + builder + "（宿主不满足）";
    }

    private static String moduleNames(List<Identifier> modules) {
        StringBuilder builder = new StringBuilder();
        for (Identifier moduleId : modules) {
            GhostModule module = GhostModuleRegistry.get(moduleId);
            if (module == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(module.displayName());
        }
        return builder.length() == 0 ? "（无）" : builder.toString();
    }

    private Identifier resolveGhostEntityType() {
        ItemStack ghost = ghostSlot.getStack(0);
        if (!(ghost.getItem() instanceof BaseGhostEyeItem eye)) {
            return null;
        }
        return GhostGrafting.entityTypeOf(eye.getGhostType());
    }

    // ==================== 合入 ====================

    /**
     * 由客户端按钮触发。
     */
    public void performGraft(PlayerEntity player) {
        ItemStack target = targetSlot.getStack(0);
        Identifier ghostType = resolveGhostEntityType();

        if (target.isEmpty()) {
            player.sendMessage(Text.literal("§7左边没有物品"), true);
            return;
        }
        if (ghostType == null) {
            player.sendMessage(Text.literal("§7右边没有厉鬼"), true);
            return;
        }

        // 本源规律永远可用，所以这里不会因为「没有装配表」而拒绝
        List<Identifier> modules = GhostGrafting.graftPlanFor(ghostType);

        // 先算出哪些模块根本装不上（能力不匹配），好让玩家知道为什么少了
        String skipped = skippedNames(target, modules, player);

        GhostGrafting.Result result = GhostGrafting.tryGraftForGhost(target, ghostType, player);
        String names = moduleNames(modules);

        if (result.grafted() == 0) {
            player.sendMessage(Text.literal("§7这些规律已经在物品上了"), true);
        } else if (result.lucky()) {
            // 赌赢了也算合入成功，厉鬼同样消耗
            this.ghostSlot.setStack(0, ItemStack.EMPTY);
            player.sendMessage(Text.literal("§e压制不足，赌赢了 §7移植：§f" + names
                    + " §8成功率 " + Math.round(result.chance() * 100) + "%"
                    + " 现有 " + result.grafted() + " 条"), true);
        } else if (result.success()) {
            // 规律已经进了物品，厉鬼被「用掉」
            this.ghostSlot.setStack(0, ItemStack.EMPTY);
            player.sendMessage(Text.literal("§a已移植：§f" + names
                    + " §8（物品现有 " + result.grafted() + " 条）"
                    + skipped), true);
        } else {
            player.sendMessage(Text.literal("§c压制不足，赌输了 §8成功率 "
                    + Math.round(result.chance() * 100) + "%"), true);
        }

        // 槽内物品 NBT 变了，必须把槽同步给客户端，否则界面还显示旧物品
        this.syncState();
        this.recompute();
    }

    // ==================== 客户端读取 ====================

    public double clientSuppression() {
        return properties.get(0) / 10.0D;
    }

    public double clientRequired() {
        return properties.get(1) / 10.0D;
    }

    public int clientState() {
        return properties.get(2);
    }

    public int clientChancePercent() {
        return properties.get(3);
    }

    public int clientModuleCount() {
        return properties.get(4);
    }
}
