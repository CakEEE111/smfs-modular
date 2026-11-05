package com.example.smfsaddon.skill;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.manager.GhostDomainManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 「释放技能」的回退通道。
 *
 * <p>正常路径是面板直接发<b>本体自己的</b>技能包（见 {@code SkillChannels}），
 * 冷却与资格都在那边算，我们只负责凑巧发出去。只有当本体没为某个技能
 * 开通道时（命名不守规律、或版本改成别的形式），才落到这条路上：
 * 服务端反射调 handler。
 *
 * <p>既然绕过了本体的包层，就得自己补一道冷却 —— 直接借本体的
 * {@code checkAndSetJSkillCooldown}，用的是它共享的 J 键冷却键，
 * 这样回退路径之间、以及与本体按键之间不会各算各的。
 *
 * <p>包里只带鬼种名，服务端会再核一次「你手上是不是真的融合了这只鬼」——
 * 客户端说想放什么就放什么，那等于没有校验。
 */
public final class CastSkillPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "cast_skill");

    /** 回退路径共用的冷却键，与本体普通技能一致 */
    private static final String FALLBACK_COOLDOWN_KEY = "j_key_skill";
    private static final int FALLBACK_COOLDOWN_TICKS = 20;

    private CastSkillPacket() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) -> {
                    String ghostType = buf.readString(32767);
                    String key = buf.readString(8);
                    server.execute(() -> {
                        if (!FusedGhostIndex.holds(player, ghostType)) {
                            player.sendMessage(
                                    Text.literal("§7你手上没有融合这只鬼的东西"), true);
                            return;
                        }
                        String label = GhostSkillTable.labelOf(ghostType, key);
                        if (!GhostDomainManager.checkAndSetJSkillCooldown(
                                player, FALLBACK_COOLDOWN_KEY, FALLBACK_COOLDOWN_TICKS, label)) {
                            return;
                        }
                        if (GhostSkillTable.cast(player, ghostType, key)) {
                            PlayerEvents.balanceRevivalDegree(player);
                            player.sendMessage(
                                    Text.literal("§8释放 §b" + label), true);
                        }
                    });
                });
    }
}
