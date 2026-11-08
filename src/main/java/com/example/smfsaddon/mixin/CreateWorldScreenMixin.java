package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.AddonConfigScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「创建世界」界面挂一个配置入口，和本体那两个按钮排在一起。
 *
 * <h2>为什么放在这里</h2>
 *
 * <p>本体在同一个界面用 {@code width/3} 起、每行 +24 挂了「模组配置」和「世界配置」。
 * 我们照它的排版往下接一行 —— 玩家的心智模型是"模组设置在这一块"，
 * 另起一个入口只会让人找不到。
 *
 * <p>注意本模组的配置是<b>全局</b>的（{@code config/smfsaddon.json}），
 * 不像本体的"世界配置"跟着存档走。放在创建界面只是为了入口集中，
 * 改的是所有世界共用的那份。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {

    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void smfsaddon$addConfigEntry(CallbackInfo callbackInfo) {
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;

        int buttonWidth = 210;
        int buttonHeight = 20;
        int buttonX = (screen.width - buttonWidth) / 2;
        // 本体用 width/3 起、每行 24；我们在它两行之后
        int buttonY = screen.width / 3 + 48;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("厉鬼模块化配置"),
                        button -> this.client.setScreen(new AddonConfigScreen(this)))
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .build());
    }
}
