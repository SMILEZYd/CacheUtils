package com.smilezyd.cacheutils.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CacheCommands {

    public static void registerModCommands() {
        // Pass the dispatcher and dedicated boolean explicitly to your static method
        ClientCommandRegistrationCallback.EVENT.register(CacheCommands::register);
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess dedicated) {
        // Defines the command: /clearcache
        LiteralArgumentBuilder<FabricClientCommandSource> baseCommand = ClientCommandManager.literal("cacheutils");

        // 1. "clear" Action branch: /cacheutils clear [searchTerm] [deleteUI]
        LiteralArgumentBuilder<FabricClientCommandSource> clearAction = ClientCommandManager.literal("deleteAll")
                .then(ClientCommandManager.argument("searchTerm", StringArgumentType.word())
                        // Add optional boolean logic here
                        .executes(context ->
                                clearCacheCommand(
                                    context,
                                    StringArgumentType.getString(context, "searchTerm"),
                                    false)) // Default false
                        .then(ClientCommandManager.argument("deleteUI", BoolArgumentType.bool())
                                .executes(context ->
                                        clearCacheCommand(
                                                context,
                                                StringArgumentType.getString(context,"searchTerm"),
                                                BoolArgumentType.getBool(context, "deleteUI")))
                        )
                );

        // 2. "read" Action branch: /cacheutils read <ID>
        LiteralArgumentBuilder<FabricClientCommandSource> restoreAction = ClientCommandManager.literal("restore")
                .executes(CacheCommands::restoreCacheCommand); // Link to the new handler

        // 3. "add" Action branch: /cacheutils add ...
        LiteralArgumentBuilder<FabricClientCommandSource> addAction = ClientCommandManager.literal("add")
                // Add sub-arguments for adding a texture
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Add action requires more parameters."));
                    return 0;
                });

        // Register all action branches under the base command
        dispatcher.register(baseCommand
                .then(clearAction)
                .then(restoreAction)
                .then(addAction)
        );
    }

    private static int clearCacheCommand(CommandContext<FabricClientCommandSource> context, String searchTerm, Boolean protectUI) {
        // Call your texture clearing logic here
        try {
            clearCache(searchTerm, protectUI);
        }
        catch (Exception e) {
        }
        // Send a confirmation message to the player's chat

        // Return 1 to indicate success
        return 1;
    }

    private static int restoreCacheCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.literal("Starting resource reload (may take a moment)..."));

        MinecraftClient client = MinecraftClient.getInstance();
        // This initiates the F3+T reload process
        CompletableFuture<Void> reloadFuture = client.reloadResources();

        // Optionally, add a callback when finished
        reloadFuture.thenRun(() -> {
            context.getSource().sendFeedback(Text.literal("Resource reload complete."));
        });

        return 1;
    }

    public static void clearCache(String searchTerm, Boolean protectUI) {
        RenderSystem.recordRenderCall(() -> {
            TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
            Map<Identifier, ?> internalTextureMap = textureManager.textures; // Requires access widener

            // Create a safe snapshot of the identifiers we want to remove
            List<Identifier> idsToRemove = new ArrayList<>();
            for (Identifier id : internalTextureMap.keySet()) {
                boolean matchesSearch = id.toString().contains(searchTerm.toLowerCase());
                boolean isMinecraftUI;


                if (matchesSearch) {
                    idsToRemove.add(id);
                }
            }

            // Now iterate the safe snapshot list and remove from the original map safely
            for (Identifier id : idsToRemove) {
                textureManager.destroyTexture(id);
                internalTextureMap.remove(id);
            }

            // Send feedback via the client message handler, as we can't access context here
            MinecraftClient.getInstance().player.sendMessage(Text.of("Cleared cache of \"" + searchTerm + "\""), false);
        });
    }

}
