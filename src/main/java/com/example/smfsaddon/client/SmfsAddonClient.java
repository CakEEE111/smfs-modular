package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.network.KakarotKamehamehaPacket;
import com.example.smfsaddon.network.KakarotTransformPacket;
import com.xie.smfs.api.GhostRendererAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：渲染器注册 + 两个技能按键。
 *
 * 实体实现了 CustomGhostModelProvider，本体的 UniversalGhostRenderer 会自动
 * 从实体身上取模型/贴图/动画路径和缩放，所以只需要指定 UNIVERSAL。
 *
 * 注意：不要用 registerWithSimpleResources —— 它把命名空间写死成 "smfs"。
 */
public class SmfsAddonClient implements ClientModInitializer {

    /** 超赛变身（与本体共用"神秘复苏"分类；本体已占用 J/K/G/N/V，这里用 R） */
    public static KeyBinding TRANSFORM_KEY;

    /** 龟派气功（避开原版的 F=副手交换 与本体的 J/K/G/N/V） */
    public static KeyBinding KAMEHAMEHA_KEY;

    /** 灵异技能面板（避开原版与本体的按键） */
    public static KeyBinding SKILL_PALETTE_KEY;

    @Override
    public void onInitializeClient() {
        registerRenderer();
        registerKeyBindings();
        registerFusionScreen();
        // 进服后预热技能通道表：面板打开前也能在日志里看到识别结果
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> client.execute(GhostSkillChannels::warmUp));
    }

    /**
     * 把灵异装置的界面挂到我们自己的 ScreenHandler 类型上。
     *
     * <p>这一步只在客户端做：服务端只知道「有个界面要开」，
     * 具体画成什么样是客户端的事。
     */
    private void registerFusionScreen() {
        HandledScreens.register(
                com.example.smfsaddon.fusion.FusionScreens.TYPE,
                com.example.smfsaddon.fusion.GhostFusionScreen::new
        );
        SmfsAddon.LOGGER.info("[smfsaddon] 灵异装置界面已绑定");
    }

    private void registerRenderer() {
        boolean ok = GhostRendererAPI.getInstance().registerWithRendererType(
                SmfsAddon.KAKAROT_GHOST,
                GhostRendererAPI.RendererType.UNIVERSAL);

        if (!ok) {
            SmfsAddon.LOGGER.error("[smfsaddon] 卡卡罗特鬼渲染器注册失败");
        } else {
            SmfsAddon.LOGGER.info("[smfsaddon] 卡卡罗特鬼渲染器注册完成");
        }
    }

    private void registerKeyBindings() {
        TRANSFORM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.transform",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.smfs.general"
        ));

        KAMEHAMEHA_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.kamehameha",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.smfs.general"
        ));

        SKILL_PALETTE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.skill_palette",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "category.smfs.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            while (SKILL_PALETTE_KEY.wasPressed()) {
                client.setScreen(new GhostSkillPaletteScreen());
            }

            // 是否满足条件交给服务端判断，客户端只负责把请求发出去
            while (TRANSFORM_KEY.wasPressed()) {
                ClientPlayNetworking.send(KakarotTransformPacket.ID, PacketByteBufs.empty());
            }
            while (KAMEHAMEHA_KEY.wasPressed()) {
                ClientPlayNetworking.send(KakarotKamehamehaPacket.ID, PacketByteBufs.empty());
            }
        });
    }
}
