package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 用鬼剪刀剪掉自己的皮。
 *
 * <h2>触发方式</h2>
 *
 * <p>手持本体的 {@code smfs:ghost_scissors}，<b>潜行 + 右键</b>。
 * 潜行是为了和剪刀原本的用途（剪断追击）区分开 —— 平时右键是救命，
 * 蹲下右键才是把自己献出去。</p>
 *
 * <h2>为什么不可逆</h2>
 *
 * <p>设计定稿。皮剪掉之后挂上「皮已离体」，生命上限 −2、灵异抗性 −20%，
 * 换来的是一条通往「礼成」的路：皮会主动找上门。</p>
 */
public final class SelfSkinning {

    private static final Identifier SCISSORS = new Identifier("smfs", "ghost_scissors");

    private SelfSkinning() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!Registries.ITEM.getId(stack.getItem()).equals(SCISSORS)) {
                return TypedActionResult.pass(stack);
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                // 客户端只负责不打断，真正逻辑在服务端
                return player.isSneaking() ? TypedActionResult.pass(stack) : TypedActionResult.pass(stack);
            }
            if (!serverPlayer.isSneaking()) {
                return TypedActionResult.pass(stack);   // 交给剪刀原本的行为
            }
            if (serverPlayer.hasStatusEffect(SmfsAddon.FLESH_REMOVED)) {
                serverPlayer.sendMessage(Text.literal("§7你身上已经没有皮了。"), true);
                return TypedActionResult.success(stack);
            }

            serverPlayer.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    SmfsAddon.FLESH_REMOVED, Integer.MAX_VALUE, 0, false, false, true));

            world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 1.0F, 0.6F);
            world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.4F, 0.5F);
            serverPlayer.setHealth(Math.min(serverPlayer.getHealth(),
                    serverPlayer.getMaxHealth() - 0.5F));

            // 剪下来的皮就是"你自己的皮"——它会站起来，然后来找你成亲
            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                var bride = SmfsAddon.PIJIAGU_SKIN.create(serverWorld);
                if (bride != null) {
                    bride.refreshPositionAndAngles(serverPlayer.getX() + 1.2D, serverPlayer.getY(),
                            serverPlayer.getZ() + 1.2D, serverPlayer.getYaw(), 0.0F);
                    bride.setOwner(serverPlayer.getUuid());
                    bride.setCustomName(Text.literal("§d你自己的皮"));
                    bride.setCustomNameVisible(true);
                    serverWorld.spawnEntity(bride);
                }
            }
            return TypedActionResult.success(stack);
        });
    }
}
