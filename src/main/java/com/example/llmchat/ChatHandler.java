package com.example.llmchat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens to server chat, detects @Name mentions, and orchestrates the AI reply.
 *
 * Flow:
 *   1. ServerChatEvent fires on the MAIN server thread when any player sends chat.
 *   2. We check whether the message mentions one of the configured trigger names.
 *   3. If so, we record the question in shared history and submit an async job.
 *   4. The async job (background thread) calls OpenRouter — this is the slow part and
 *      MUST NOT run on the main thread.
 *   5. When the reply arrives, we hop back onto the main thread via server.execute(...)
 *      to broadcast it, because Minecraft networking is not thread-safe.
 */
public final class ChatHandler {

    private final Logger log;
    private final OpenRouterClient client;
    private final ConversationHistory history = new ConversationHistory();

    // A small dedicated thread pool for outbound API calls so we never block the game.
    private final ExecutorService executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger(1);
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "llmchat-api-" + n.getAndIncrement());
            t.setDaemon(true); // don't keep the JVM alive on shutdown
            return t;
        }
    });

    public ChatHandler(Logger log) {
        this.log = log;
        this.client = new OpenRouterClient(log);
    }

    public ConversationHistory history() {
        return history;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Forge fires this for every chat message from a player. We do NOT cancel the event,
     * so the player's original message still appears in chat normally; we just *also*
     * reply if it mentioned the AI.
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        final String raw = event.getMessage().getString();
        final ServerPlayer player = event.getPlayer();
        if (player == null) return;

        Trigger trigger = detectTrigger(raw);
        if (trigger == null) {
            return; // not addressed to the AI — ignore
        }

        if (trigger.prompt().isBlank()) {
            return; // someone typed just "@Grok" with no question
        }

        final MinecraftServer server = player.getServer();
        if (server == null) return;

        final String playerName = player.getGameProfile().getName();
        final String model = trigger.model();
        final String question = trigger.prompt();

        // Build the request on the main thread (cheap), then go async for the network call.
        final List<ChatMessage> requestMessages = history.buildRequestMessages(playerName, question);
        history.addUserTurn(playerName, question);

        if (Config.SHOW_THINKING_MESSAGE.get()) {
            broadcast(server, "§7§o" + Config.AI_DISPLAY_NAME.get() + " is thinking...§r");
        }

        executor.submit(() -> {
            String reply;
            try {
                reply = client.complete(model, requestMessages);
            } catch (Throwable t) {
                log.error("Unexpected error during AI completion", t);
                reply = "[error] Internal error while contacting the AI.";
            }

            final String finalReply = postProcess(reply);
            final boolean isError = reply.startsWith("[error]");

            // Only remember successful replies in shared history.
            if (!isError) {
                history.addAssistantTurn(reply);
            }

            // Hop back to the main thread to actually send chat packets.
            server.execute(() -> broadcastReply(server, finalReply, isError));
        });
    }

    /** Detects whether the message mentions a configured @name and extracts the question. */
    private Trigger detectTrigger(String message) {
        List<? extends String> names = Config.TRIGGER_NAMES.get();
        if (names == null || names.isEmpty()) return null;

        for (String name : names) {
            if (name == null || name.isBlank()) continue;

            // Matches "@Name" at a word boundary, case-insensitive, optionally followed by
            // punctuation like a comma or colon. Captures everything after as the question.
            // Example matches: "@Grok hello", "hey @grok, what's up", "@AI: define entropy"
            Pattern p = Pattern.compile(
                    "(?i)(?:^|\\s)@" + Pattern.quote(name) + "\\b[\\s,:.!?-]*(.*)",
                    Pattern.DOTALL);
            Matcher m = p.matcher(message);
            if (m.find()) {
                String prompt = m.group(1) == null ? "" : m.group(1).trim();
                return new Trigger(name, modelFor(name), prompt);
            }
        }
        return null;
    }

    /** Resolves the model slug for a given trigger name using the nameModelMap, else default. */
    private String modelFor(String name) {
        String mapped = nameModelCache().get(name.toLowerCase(Locale.ROOT));
        return (mapped != null && !mapped.isBlank()) ? mapped : Config.DEFAULT_MODEL.get();
    }

    // Parse the "Name=model" list once and cache it (config is effectively static at runtime).
    private volatile Map<String, String> cachedMap;
    private Map<String, String> nameModelCache() {
        Map<String, String> local = cachedMap;
        if (local == null) {
            local = new ConcurrentHashMap<>();
            for (String entry : Config.NAME_MODEL_MAP.get()) {
                if (entry == null) continue;
                int eq = entry.indexOf('=');
                if (eq > 0 && eq < entry.length() - 1) {
                    String key = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String val = entry.substring(eq + 1).trim();
                    if (!key.isEmpty() && !val.isEmpty()) {
                        local.put(key, val);
                    }
                }
            }
            cachedMap = local;
        }
        return local;
    }

    /** Trims the reply to the configured max length and strips the [error] marker for display. */
    private String postProcess(String reply) {
        String text = reply;
        if (text.startsWith("[error]")) {
            text = text.substring("[error]".length()).trim();
        }
        int max = Config.MAX_REPLY_CHARS.get();
        if (text.length() > max) {
            text = text.substring(0, max).trim() + "…";
        }
        return text;
    }

    private void broadcastReply(MinecraftServer server, String text, boolean isError) {
        String name = Config.AI_DISPLAY_NAME.get();
        if (isError) {
            broadcast(server, "§c[" + name + "] §7" + text + "§r");
        } else {
            // Cyan name tag, white body, mimicking a chat line: "<AI> ...."
            broadcast(server, "§b<" + name + ">§r " + text);
        }
    }

    private void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);
        // false = system message (won't be re-broadcast as player chat).
        server.getPlayerList().broadcastSystemMessage(component, false);
        // Also mirror to the server console/log.
        log.info("[chat-broadcast] {}", message.replaceAll("§.", ""));
    }

    /** Small holder for a detected trigger. */
    private record Trigger(String name, String model, String prompt) {}
}
