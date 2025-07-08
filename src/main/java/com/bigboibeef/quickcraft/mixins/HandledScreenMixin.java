package com.bigboibeef.quickcraft.mixins;

import com.bigboibeef.quickcraft.QuickCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Unique
    private static long timer = 0;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void quickCraft$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (QuickCraft.enabled) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (!(client.currentScreen instanceof HandledScreen<?> screen) || client.world == null) return;
            ScreenHandler handler = screen.getScreenHandler();
            if (!(handler instanceof PlayerScreenHandler || handler instanceof CraftingScreenHandler)) return;

            long currentTick = client.world.getTime();
            if (QuickCraft.isQuickCraftKey(keyCode, scanCode)) {
                if (currentTick - timer < 5) return;
                timer = currentTick;

                if (QuickCraft.single) {
                    QuickCraft.loadSingleGrid(client);
                } else {
                    QuickCraft.loadStackGrid(client);
                }

                cir.setReturnValue(true);
            } else if (handler.getSlot(0).hasStack()) {
                QuickCraft.saveGrid(client);
            }
        }
    }
}
