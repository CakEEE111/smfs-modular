package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「拜堂」留下的礼堂领域。
 *
 * <p>不是一个瞬时 AOE，而是一块持续 6 秒的地面：范围内的存在每隔 1 秒被礼的重量压一次。
 * 领域跟着施放者走 —— 你往哪走，婚礼就在哪继续。</p>
 */
public final class UnionDomain {

    private record Active(java.util.UUID owner, ServerWorld world, long endTick, int nextPulse) {
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final int RADIUS = 10;
    private static final int DURATION = 120;      // 6 秒
    private static final int PULSE = 20;          // 每秒一次
    private static final float PULSE_DAMAGE = 10.0F;

    private UnionDomain() {
    }

    public static void start(ServerPlayerEntity player) {
        ACTIVE.removeIf(active -> active.owner().equals(player.getUuid()));
        ACTIVE.add(new Active(player.getUuid(),
                (ServerWorld) player.getWorld(),
                player.getWorld().getTime() + DURATION,
                (int) player.getWorld().getTime()));
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active active = it.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(active.owner());
            long now = active.world().getTime();
            if (owner == null || now > active.endTick()) {
                it.remove();
                continue;
            }
            if (now < active.nextPulse()) {
                continue;
            }
            ACTIVE.set(ACTIVE.indexOf(active), new Active(active.owner(), active.world(),
                    active.endTick(), (int) now + PULSE));

            Vec3d center = owner.getPos();
            Box area = new Box(center, center).expand(RADIUS);
            for (LivingEntity target : active.world().getEntitiesByClass(LivingEntity.class, area,
                    e -> e != owner && e.isAlive())) {
                com.example.smfsaddon.combat.SpiritStrike.deal(owner, target, PULSE_DAMAGE);
                active.world().spawnParticles(ParticleTypes.SOUL,
                        target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
                if (target.isDead()) {
                    continue;
                }
                // 被礼压住的人走不快、也举不起手
                target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, 40, 3, false, false, false));
            }
            active.world().spawnParticles(ParticleTypes.END_ROD,
                    center.x, owner.getBodyY(0.2), center.z, 12, RADIUS * 0.5, 0.2, RADIUS * 0.5, 0.02);
        }
    }
}
