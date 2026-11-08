package com.example.smfsaddon.fusion;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「合入」按钮的请求通道。
 *
 * <p>按钮在客户端，判定与写入必须在服务端（要读灵异值、要改物品 NBT）。
 * 包里不带任何数据 —— 目标物品和厉鬼都在界面自己的槽里，服务端直接读。
 * 这样客户端也无法伪造要移植哪些规律。
 */
public final class FusionGraft {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "fusion_graft");

    /**
     * 同一玩家最小请求间隔（tick）。
     *
     * <p>包里没有数据，客户端伪造不出"融什么"，但可以<b>狂刷这个包</b>——
     * 每次都会走一遍压制判定、反噬、复苏掷骰。多人服上这就是个放大器，
     * 所以按玩家限流，不是防作弊，是防手滑和防脚本。
     */
    private static final long MIN_INTERVAL_TICKS = 4L;

    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private FusionGraft() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> {
                            long now = server.getTicks();
                            Long last = LAST_REQUEST.get(player.getUuid());
                            if (last != null && now - last < MIN_INTERVAL_TICKS) {
                                return;
                            }
                            LAST_REQUEST.put(player.getUuid(), now);
                            if (player.currentScreenHandler instanceof GhostFusionScreenHandler fusion) {
                                fusion.performGraft(player);
                            }
                        }));
    }
}
