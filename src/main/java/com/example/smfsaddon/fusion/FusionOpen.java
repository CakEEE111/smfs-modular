package com.example.smfsaddon.fusion;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 打开灵异装置的请求通道。
 *
 * <p>客户端不能直接打开带服务端槽位的界面，必须由服务端调
 * {@code openHandledScreen}，所以这里只是一条「请帮我开一下」的空包。
 */
public final class FusionOpen {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "open_fusion");

    private FusionOpen() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) ->
                        server.execute(() -> player.openHandledScreen(
                                new SimpleNamedScreenHandlerFactory(
                                        (syncId, inventory, p) -> new GhostFusionScreenHandler(syncId, inventory),
                                        Text.literal("灵异装置")
                                ))));
    }
}
