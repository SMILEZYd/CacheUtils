package com.smilezyd.cacheutils.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.smilezyd.cacheutils.CacheUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CacheCommands {

    public static void registerModCommands() {
        // Pass the dispatcher and dedicated boolean explicitly to your static method
        ClientCommandRegistrationCallback.EVENT.register(CacheCommands::register);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }

    // Helper method for a required argument
    private static RequiredArgumentBuilder<FabricClientCommandSource, String> argument(String name, StringArgumentType type) {
        return ClientCommandManager.argument(name, type);
    }
    private static RequiredArgumentBuilder<FabricClientCommandSource, Boolean> argument(String name, BoolArgumentType booltype) {
        return ClientCommandManager.argument(name, booltype);
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess dedicated) {

        // --- BUILD THE "DELETE" COMMAND STRUCTURE ---

        // Define the handler for /cacheutils delete <term> <bool>
        Command<FabricClientCommandSource> deleteWithParam = context -> clearCacheCommand(
                context,
                StringArgumentType.getString(context, "searchTerm"),
                BoolArgumentType.getBool(context, "protectUI")
        );

        // Define the handler for /cacheutils delete <term> (default true for protectUI)
        Command<FabricClientCommandSource> deleteDefaultCommand = context -> clearCacheCommand(
                context,
                StringArgumentType.getString(context, "searchTerm"),
                true
        );

        Command<FabricClientCommandSource> handleChatNoFilter = context -> dumpCacheCommand(context, false, null);
        Command<FabricClientCommandSource> handleLogNoFilter = context -> dumpCacheCommand(context, true, null);

        Command<FabricClientCommandSource> handleChatWithFilter = context -> dumpCacheCommand(context, false, StringArgumentType.getString(context, "filter"));
        Command<FabricClientCommandSource> handleLogWithFilter = context -> dumpCacheCommand(context, true, StringArgumentType.getString(context, "filter"));

        RequiredArgumentBuilder<FabricClientCommandSource, String> filterArg =
                ClientCommandManager.argument("filter", StringArgumentType.word());

        // Build the delete action using the helpers for better readability
        LiteralArgumentBuilder<FabricClientCommandSource> deleteAction = literal("delete")
                .then(argument("searchTerm", StringArgumentType.word())
                        // .executes() sets the default behavior if nothing else follows
                        .executes(deleteDefaultCommand)
                        .then(argument("protectUI", BoolArgumentType.bool())
                                .executes(deleteWithParam)
                        )
                );

        // --- BUILD OTHER COMMANDS (RESTORE, DUMP) SIMILARLY ---

        LiteralArgumentBuilder<FabricClientCommandSource> restoreAction = literal("restore")
                .executes(CacheCommands::restoreCacheCommand);

        LiteralArgumentBuilder<FabricClientCommandSource> listAction = literal("list");

        LiteralArgumentBuilder<FabricClientCommandSource> listChat = literal("chat")
                .executes(handleChatNoFilter) // /cacheutils list chat
                .then(filterArg.executes(handleChatWithFilter)); // /cacheutils dump chat <filter>

        LiteralArgumentBuilder<FabricClientCommandSource> listLog = literal("log")
                .executes(handleLogNoFilter) // /cacheutils list log
                .then(filterArg.executes(handleLogWithFilter)); // /cacheutils dump log <filter>




        // --- REGISTER THE BASE COMMAND ---

        dispatcher.register(literal("cacheutils")
                .then(deleteAction)
//                .then(restoreAction)
                .then(listAction.then(listChat).then(listLog))
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

    private static int dumpCacheCommand(CommandContext<FabricClientCommandSource> context, boolean log, @Nullable String searchTerm) {
        dumpCache(log, searchTerm);
        return 1;
    }

    public static void clearCache(String searchTerm, Boolean protectUI) {
        RenderSystem.recordRenderCall(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
            Map<Identifier, ?> internalTextureMap = textureManager.textures; // Requires access widener

            // Create a safe snapshot of the identifiers we want to remove
            List<Identifier> idsToRemove = new ArrayList<>();
            for (Identifier id : internalTextureMap.keySet()) {
                String name = id.toString();
                boolean matchesSearch = name.contains(searchTerm.toLowerCase());
                boolean isMissingno = name.contains("missingno");
                boolean isMinecraftUI = name.contains("gui") || name.contains("default/0");
                boolean isMinecraftText = name.contains("font") ||
                        name.contains("include/unifont") ||
                        name.equals("minecraft:default/0") ||
                        name.equals("minecraft:include/default/0");


                if (matchesSearch && !(isMinecraftUI && isMinecraftText && protectUI) && !isMissingno) {
                    idsToRemove.add(id);
                }
            }
            int texturesDeleted = 0;
            // Now iterate the safe snapshot list and remove from the original map safely
            for (Identifier id : idsToRemove) {
//                textureManager.destroyTexture(id);
                internalTextureMap.remove(id);
                client.player.sendMessage(Text.of(id), false);
                texturesDeleted++;
            }

            // Send feedback via the client message handler, as we can't access context here
            client.player.sendMessage(Text.of("Cleared cache of \"" + searchTerm + "\""), false);
            client.player.sendMessage(Text.of("Deleted " + texturesDeleted + " textures"), false);
        });
    }

    public static void dumpCache(Boolean inChat, @Nullable String searchTerm) {
        // Ensure this is run on the render thread if it interacts with client systems
         RenderSystem.recordRenderCall(() -> {

             MinecraftClient client = MinecraftClient.getInstance();
             TextureManager textureManager = client.getTextureManager();
             Map<Identifier, ?> internalTextureMap = textureManager.textures;

             for (Identifier id : internalTextureMap.keySet()) {
                 String name = id.toString();

                 // Centralize the filtering logic
                 boolean matchesFilter = (searchTerm == null || name.contains(searchTerm));

                 if (matchesFilter) {
                     // Centralize the output logic
                     if (!inChat) {
                         // client.player might be null if called at an odd time
                         if (client.player != null) {
                             client.player.sendMessage(Text.of(name), false);
                         }
                     } else {
                         CacheUtils.LOGGER.info(name);
                     }
                 }
             }
         });
    }
}
