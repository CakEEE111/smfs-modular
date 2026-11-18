package com.example.smfsaddon.fusion;

import com.example.smfsaddon.SmfsAddon;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * 灵异装置的界面类型。
 *
 * <p>用普通 {@code ScreenHandlerType} 而不是 {@code ExtendedScreenHandlerType}：
 * 这个界面没有需要额外同步的打开数据（槽位、玩家背包都是标准的），
 * 少一层网络协议就少一处会出错的地方。
 */
public final class FusionScreens {

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, "ghost_fusion");

    public static ScreenHandlerType<GhostFusionScreenHandler> TYPE;

    private FusionScreens() {
    }

    public static void register() {
        TYPE = Registry.register(
                Registries.SCREEN_HANDLER,
                ID,
                new ScreenHandlerType<>(GhostFusionScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );
        SmfsAddon.LOGGER.info("[smfsaddon] 灵异装置界面已注册");
    }
}
