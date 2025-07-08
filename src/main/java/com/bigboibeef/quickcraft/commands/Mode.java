package com.bigboibeef.quickcraft.commands;

import com.bigboibeef.quickcraft.QuickCraft;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class Mode {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("qc")
                            .then(
                                    ClientCommandManager.literal("mode")
                                            .then(
                                                    ClientCommandManager.literal("single")
                                                            .executes(context -> {
                                                                QuickCraft.single = true;
                                                                QuickCraft.saveData();
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                            )
                                            .then(
                                                    ClientCommandManager.literal("stack")
                                                            .executes(context -> {
                                                                QuickCraft.single = false;
                                                                QuickCraft.saveData();
                                                                return Command.SINGLE_SUCCESS;
                                                            })
                                            )
                            )
                            .then(ClientCommandManager.literal("enable")
                                    .executes(context -> {
                                        QuickCraft.enable();
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                            .then(ClientCommandManager.literal("disable")
                                    .executes(context -> {
                                        QuickCraft.disable();
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )

            );
        });
    }
}