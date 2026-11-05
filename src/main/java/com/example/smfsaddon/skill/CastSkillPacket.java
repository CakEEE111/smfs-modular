package com.example.smfsaddon.skill;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 「释放技能」的请求通道。
 *
 * <p>包里只带鬼种名，服务端会再核一次「你手上是不是真的融合了这只鬼」——
 * 客户端说想放什么就放什么，那等于没有校验。
 */
public final class CastSkillPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "cast_skill");

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
                        if (GhostSkillTable.cast(player, ghostType, key)) {
                            player.sendMessage(
                                    Text.literal("§8释放 §b"
                                            + GhostSkillTable.labelOf(ghostType, key)), true);
                        }
                    });
                });
    }
}
