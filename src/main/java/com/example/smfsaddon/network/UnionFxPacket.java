package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** 席面的客户端演出指令（只影响镜头，不做任何逻辑） */
public final class UnionFxPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "union_fx");

    /** 礼成：短促震屏 */
    public static final byte SHAKE = 1;
    /** 入席：镜头开始往下沉 */
    public static final byte SEATED = 2;
    /** 离开席面：恢复 */
    public static final byte RELEASE = 3;

    private UnionFxPacket() {
    }

    public static void send(ServerPlayerEntity player, byte action, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
