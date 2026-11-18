package com.example.smfsaddon.network;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** 通知客户端：守护灵礼堂开始 / 结束（只影响显示，不做任何物理） */
public final class UnionBowFxPacket {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "union_bow_fx");

    private UnionBowFxPacket() {
    }

    public static void sendStart(ServerPlayerEntity receiver, int ownerEntityId, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(1);
        buf.writeVarInt(ownerEntityId);
        buf.writeVarInt(durationTicks);
        ServerPlayNetworking.send(receiver, ID, buf);
    }
}
