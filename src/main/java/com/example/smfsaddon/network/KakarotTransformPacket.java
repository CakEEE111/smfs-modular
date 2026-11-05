package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.manager.GhostDomainManager;
import com.xie.smfs.manager.MainGhostManager;
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
        server.execute(() -> toggle(player));
    }

    private static void toggle(ServerPlayerEntity player) {
        // 已变身 → 直接关掉（收尾逻辑在 onRemoved 里）
        if (player.hasStatusEffect(SmfsAddon.SUPER_SAIYAN)) {
            player.removeStatusEffect(SmfsAddon.SUPER_SAIYAN);
            return;
        }

        // 开启条件：主鬼是卡卡罗特 + 等级 10
        GhostDomainManager.SkillCheckResult check = GhostDomainManager.canUseGhostSkill(
                player, "kakarot_ghost", SmfsAddon.KAKAROT_GHOST_ITEM, REQUIRED_GHOST_LEVEL);

        if (check == GhostDomainManager.SkillCheckResult.NO_GHOST) {
            player.sendMessage(Text.translatable("message.smfsaddon.kakarot.no_ghost"), true);
            return;
        }
        if (check == GhostDomainManager.SkillCheckResult.LEVEL_TOO_LOW) {
            int slot = MainGhostManager.getMainGhostSlot(player);
            int current = PlayerEvents.getGhostSlotLevel(player, slot);
            player.sendMessage(Text.translatable("message.smfsaddon.kakarot.level_low",
                    current, REQUIRED_GHOST_LEVEL), true);
            return;
        }

        // 无冷却、无固定时长：常驻到玩家再按一次或死亡
        player.addStatusEffect(new StatusEffectInstance(
                SmfsAddon.SUPER_SAIYAN,
                Integer.MAX_VALUE,
                0,
                false,   // ambient
                true,    // 显示粒子
                true));  // 显示图标

        // 与本体技能一致：释放即结算一次复苏度（净 +12）
        PlayerEvents.balanceRevivalDegree(player);
    }
}
