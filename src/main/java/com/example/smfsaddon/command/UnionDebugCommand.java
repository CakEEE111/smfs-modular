package com.example.smfsaddon.command;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.pijiagu.UnionCapture;
import com.mojang.brigadier.CommandDispatcher;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.util.GhostUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 调试用：检查并修复「礼成体」的驾驭物品。
 *
 * <p>用途是排查"槽位里显示成纸"这类问题：会打印本体查表拿到的物品、
 * 再把槽位里的旧数据换成正式物品。</p>
 */
public final class UnionDebugCommand {

    private UnionDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("unionfix")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> run(context.getSource()))));
    }

    private static int run(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只能由玩家执行"));
            return 0;
        }

        // 1) 本体查表能拿到什么
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", UnionCapture.GHOST_TYPE);
        ItemStack probe = GhostUtils.createTamedItem(UnionCapture.GHOST_TYPE, nbt);
        source.sendFeedback(() -> Text.literal("§7createTamedItem(" + UnionCapture.GHOST_TYPE
                + ") → §f" + probe.getItem() + " §7x" + probe.getCount()), false);

        // 2) 槽位现状 + 就地覆盖旧的纸
        int rewritten = -1;
        for (int i = 0; i < 10; i++) {
            final int slotIndex = i;
            ItemStack stack = PlayerEvents.getGhostSlotItem(player, slotIndex);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            source.sendFeedback(() -> Text.literal("§7槽位 " + (slotIndex + 1) + "：§f"
                    + stack.getItem() + " §7x" + stack.getCount()), false);

            boolean looksLikeUnion = stack.getItem() == net.minecraft.item.Items.PAPER
                    || stack.getItem() == SmfsAddon.PIJIAGU_UNION_ITEM;
            if (looksLikeUnion && rewritten < 0) {
                ItemStack token = new ItemStack(SmfsAddon.PIJIAGU_UNION_ITEM);
                PlayerEvents.setGhostSlotData(player, slotIndex, token);
                rewritten = slotIndex;
            }
        }
        if (rewritten >= 0) {
            PlayerEvents.validateGhostSlots(player);
            final int slot = rewritten;
            source.sendFeedback(() -> Text.literal("§a已把槽位 " + (slot + 1) + " 换成正式物品"), false);
            return 1;
        }

        // 3) 没有旧槽位就按正常流程写一个
        boolean ok = UnionCapture.capture(player);
        source.sendFeedback(() -> Text.literal(ok ? "§a已写入新槽位" : "§c写入失败（没有空槽？）"), false);
        return 1;
    }
}
