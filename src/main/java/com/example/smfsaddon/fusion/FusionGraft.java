package com.example.smfsaddon.fusion;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/**
 * 「合入」按钮的请求通道。
 *
 * <p>按钮在客户端，判定与写入必须在服务端（要读灵异值、要改物品 NBT）。
 * 包里不带任何数据 —— 目标物品和厉鬼都在界面自己的槽里，服务端直接读。
 * 这样客户端也无法伪造要移植哪些规律。
 */
public final class FusionGraft {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "fusion_graft");

    private FusionGraft() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> {
                            if (player.currentScreenHandler instanceof GhostFusionScreenHandler fusion) {
                                fusion.performGraft(player);
                            }
                        }));
    }
}
