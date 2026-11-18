package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.manager.KakarotSlots;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.manager.GhostDomainManager;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 「超级赛亚人」C2S 请求包 —— 随开随放。
 *
 * 按一次开、再按一次关，没有冷却也没有固定时长。
 * 代价体现在持续消耗上：变身期间每秒 +2 复苏度（见 SuperSaiyanEffect）。
 */
public final class KakarotTransformPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "kakarot_transform");

    /** 变身所需的鬼等级（本体 getGhostSlotLevel，满级 10） */
    private static final int REQUIRED_GHOST_LEVEL = 10;

    private KakarotTransformPacket() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, KakarotTransformPacket::receive);
    }

    private static void receive(MinecraftServer server, ServerPlayerEntity player,
                                ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        // 面板发来的包带 true（允许融合物品），按键发来的带 false（只认鬼槽）。
        // 老包没有这个字节时按 false 处理，宁严不松。
        boolean allowFused = buf.isReadable() && buf.readBoolean();
        server.execute(() -> toggle(player, allowFused));
    }

    /**
     * @param allowFused 是否允许"只融在物品上"的卡卡罗特（面板 = true，按键 = false）
     */
    private static void toggle(ServerPlayerEntity player, boolean allowFused) {
        // 已变身 → 直接关掉（收尾逻辑在 onRemoved 里）
        if (player.hasStatusEffect(SmfsAddon.SUPER_SAIYAN)) {
            player.removeStatusEffect(SmfsAddon.SUPER_SAIYAN);
            return;
        }

        if (!allowFused) {
            // 按键路径：必须在鬼槽里养着，且到 10 级。
            // 注意不能走 canUseGhostSkill —— 它对融合物品是放行的（那正是面板要的），
            // 用它判"按键"就等于没判。
            int slot = KakarotSlots.findSlot(player);
            if (slot < 0) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.slot_only"), true);
                return;
            }
            int current = PlayerEvents.getGhostSlotLevel(player, slot);
            if (current < REQUIRED_GHOST_LEVEL) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.level_low",
                        current, REQUIRED_GHOST_LEVEL), true);
                return;
            }
        } else {
            // 面板路径：槽里的、以及只融在物品上的都放行
            GhostDomainManager.SkillCheckResult check = GhostDomainManager.canUseGhostSkill(
                    player, "kakarot_ghost", SmfsAddon.KAKAROT_GHOST_ITEM, REQUIRED_GHOST_LEVEL);
            if (check == GhostDomainManager.SkillCheckResult.NO_GHOST) {
                player.sendMessage(Text.translatable("message.smfsaddon.kakarot.no_ghost"), true);
                return;
            }
        }

        // 无冷却、无固定时长：常驻到玩家再按一次或死亡
        player.addStatusEffect(new StatusEffectInstance(
                SmfsAddon.SUPER_SAIYAN,
                Integer.MAX_VALUE,
                0,
                false,   // ambient
                true,    // 显示粒子
                true));  // 显示图标

        // 释放代价只记在卡卡罗特自己身上（槽里或物品上），
        // 不能用 balanceRevivalDegree —— 那会给主鬼槽里的**别的鬼**加复苏度
        KakarotSlots.chargeRelease(player, KakarotSlots.RELEASE_COST);
    }
}
