package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.event.GhostItemUseEvent;
import com.xie.smfs.damage.ModDamageSources;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * 开门必死。
 *
 * <p>这是「把厉鬼当插件合进物品」的第一个示例：一把普通的剑装上这条规律之后，
 * 对着门挥一下，门后一片全灭。
 *
 * <p>它只监听 {@link GhostItemUseEvent}，所以挂在鬼身上不会有任何反应 ——
 * 这不是缺陷，而是这类规律的定义：它属于「被使用的东西」，不属于「会走的东西」。
 * 也正因如此，它天然只能装进装置、装不到鬼身上。
 */
public final class DoorBaneModule implements GhostModule {

    public static final Identifier ID = new Identifier("smfsaddon", "door_bane");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "开门必死";
    }

    /**
     * 这条规律很贵：让一件普通武器获得必杀能力，代价应当明显。
     */
    @Override
    public double suppressionCost() {
        return 60.0D;
    }

    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new DoorBaneRule());
    }

    private static final class DoorBaneRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "door_bane/on_door");

        /** 门附近这个范围里的一切都会死 */
        private static final int REACH = 4;

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "开门必死",
                    "对着门挥动，门后一片尽灭"
            );
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostItemUseEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostItemUseEvent useEvent)) {
                return false;
            }
            // 必须是冲着门去的：对空气挥、对石头挥都不算
            if (useEvent.state() == null) {
                com.example.smfsaddon.ghost.module.GhostDebug.notice(useEvent.player(), "开门必死：不是对方块使用");
                return false;
            }
            // 中文语境里的"门"包括活板门和栅栏门
            var block = useEvent.state().getBlock();
            boolean isDoor = block instanceof DoorBlock
                    || block instanceof TrapdoorBlock
                    || block instanceof FenceGateBlock;
            if (!isDoor) {
                com.example.smfsaddon.ghost.module.GhostDebug.notice(useEvent.player(), "开门必死：不是门 → " + block);
            }
            return isDoor;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostItemUseEvent useEvent)) {
                return;
            }
            PlayerEntity player = useEvent.player();
            BlockPos pos = useEvent.pos();
            if (player == null || pos == null || context.world() == null) {
                return;
            }

            Box area = new Box(
                    pos.getX() - REACH, pos.getY() - 2, pos.getZ() - REACH,
                    pos.getX() + REACH + 1, pos.getY() + 3, pos.getZ() + REACH + 1
            );

            // 必须用本体的伤害源：原版 playerAttack 打在鬼身上会被当成"无效伤害"，
            // 有数字飘出来但实际不掉灵异强度。
            var source = ModDamageSources.ghost(context.world());
            int hit = 0;
            for (LivingEntity target : context.world().getEntitiesByClass(
                    LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
                target.damage(source, 1000.0F);
                hit++;
            }
            // 无论有没有人被打中，都给一次反馈 ——
            // 否则「规律没触发」和「触发了但附近没人」在外部完全一样。
            context.world().playSound(null, pos,
                    net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_SCREAM,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 0.5F);
            com.example.smfsaddon.ghost.module.GhostDebug.notice(player, "开门必死 命中 " + hit);
        }
    }
}
