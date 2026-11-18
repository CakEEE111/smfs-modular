package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 婚礼期间的视角锁定（服务端这一半）。
 *
 * <p>发一个包告诉客户端"盯住这个实体，持续这么多 tick"，客户端自己每帧把镜头
 * 转向它。服务端不做任何强控 —— 玩家仍然能走，只是看不见别的方向。</p>
 *
 * <p>这样做的好处：不碰玩家输入、不需要每 tick 发包，客户端断线也不会留下卡死的镜头。</p>
 */
public final class CameraLock {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "pijiagu_camera_lock");

    private CameraLock() {
    }

    /**
     * 锁定某个玩家，持续 ticks。
     *
     * @param takeCamera 是否连相机一起接管。关掉的话玩家只是站着不能动，
     *                   但可以自由看四周 —— 强扭镜头很容易拍成"卡在梁柱里"。
     */
    public static void lock(ServerPlayerEntity player, Entity target, int ticks, boolean takeCamera) {
        if (player == null || target == null || ticks <= 0) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(target.getId());
        buf.writeVarInt(ticks);
        buf.writeBoolean(takeCamera);
        ServerPlayNetworking.send(player, ID, buf);
    }

    /** 解除锁定 */
    public static void release(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(-1);
        buf.writeVarInt(0);
        buf.writeBoolean(false);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
