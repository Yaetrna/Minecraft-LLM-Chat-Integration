package com.example.llmchat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the LLM Chat mod.
 *
 * Responsibilities:
 *   - register the config spec so Forge writes config/llmchat-common.toml
 *   - register the ChatHandler on the Forge event bus so it hears chat
 *   - register /llmreset (clear shared memory) and /llmreload (reload knowledge file)
 *   - clean up the background executor on shutdown
 */
@Mod(LlmChatMod.MODID)
public final class LlmChatMod {

    public static final String MODID = "llmchat";
    public static final Logger LOGGER = LoggerFactory.getLogger("LlmChat");

    private final ChatHandler chatHandler = new ChatHandler(LOGGER);

    public LlmChatMod() {
        // Register the TOML config (created on first run under /config).
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "llmchat-common.toml");

        // Listen for the FML setup event on the mod bus.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        // Register chat + lifecycle handlers on the main Forge event bus.
        MinecraftForge.EVENT_BUS.register(chatHandler);
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("LLM Chat mod constructed. Configure your API key in config/llmchat-common.toml.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("LLM Chat ready. Trigger names: {} | default model: {} | knowledge file: {}",
                Config.TRIGGER_NAMES.get(), Config.DEFAULT_MODEL.get(), Config.KNOWLEDGE_FILE.get());
        if (Config.API_KEY.get() == null || Config.API_KEY.get().isBlank()) {
            LOGGER.warn("LLM Chat: no API key set yet -- the AI will not respond until you add one "
                    + "to config/llmchat-common.toml and restart.");
        }
    }

    /** Registers /llmreset and /llmreload (server operators only, permission level 2). */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("llmreset")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    chatHandler.history().clear();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("\u00A7aLLM Chat: shared conversation memory cleared."),
                        true);
                    return 1;
                })
        );

        dispatcher.register(
            Commands.literal("llmreload")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    chatHandler.reloadKnowledge();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("\u00A7aLLM Chat: knowledge file reloaded from disk."),
                        true);
                    return 1;
                })
        );
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        chatHandler.shutdown();
        LOGGER.info("LLM Chat shut down.");
    }
}
