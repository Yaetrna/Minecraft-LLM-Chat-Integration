package com.example.llmchat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Listens to server chat, detects @Name mentions, and orchestrates the AI reply.
 *
 * Flow:
 *   1. ServerChatEvent fires on the MAIN server thread when any player sends chat.
 *   2. We check whether the message mentions one of the configured trigger names.
 *   3. If so, we submit a job to a background executor. The job builds the request
 *      (reading the latest history), calls OpenRouter, and records the exchange in
 *      history -- all on the worker thread so the conversation stays ordered.
 *   4. When the reply arrives, we hop back onto the main thread via server.execute(...)
 *      to broadcast it, because Minecraft networking is not thread-safe.
 *
 * Ordering guarantee: with threadPoolSize=1 (the default), requests are processed
 * one at a time in the order they were asked. The request is built on the worker
 * (not the main thread), so it always sees the latest history including any prior
 * Q&A pairs that completed before it.
 */
public final class ChatHandler {

    private final Logger log;
    private final OpenRouterClient client;
    private final ConversationHistory history = new ConversationHistory();

    // Per-player cooldown tracking: UUID -> epoch millis of last accepted request.
    private final ConcurrentHashMap<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();

    // Lazily created on first use (config is guaranteed loaded by then).
    private volatile ExecutorService executor;

    // Cached compiled regex patterns, rebuilt when trigger names change in config.
    private volatile List<Pattern> cachedPatterns;
    private volatile List<String> cachedTriggerNames;

    // Cached knowledge file content (reloaded via /llmreload command).
    private volatile String cachedKnowledge;
    private volatile boolean knowledgeLoaded = false;

    // Cached name->model map, rebuilt when nameModelMap changes in config.
    private volatile Map<String, String> cachedModelMap;
    private volatile List<String> cachedMapEntries;

    public ChatHandler(Logger log) {
        this.log = log;
        this.client = new OpenRouterClient(log);
    }

    public ConversationHistory history() {
        return history;
    }

    /** Reloads the knowledge file from disk (called by /llmreload). */
    public void reloadKnowledge() {
        knowledgeLoaded = false;
        loadKnowledge();
    }

    private void loadKnowledge() {
        if (knowledgeLoaded) return;
        String path = Config.KNOWLEDGE_FILE.get();
        if (path == null || path.isBlank()) {
            cachedKnowledge = "";
            knowledgeLoaded = true;
            return;
        }
        try {
            File f = new File(path);
            if (!f.exists()) {
                log.info("Knowledge file '{}' not found -- skipping. Create it to inject domain docs.", path);
                cachedKnowledge = "";
            } else {
                String content = Files.readString(f.toPath());
                cachedKnowledge = content;
                log.info("Loaded knowledge file '{}' ({} chars).", path, content.length());
            }
        } catch (Exception e) {
            log.warn("Failed to load knowledge file '{}': {}", path, e.getMessage());
            cachedKnowledge = "";
        }
        knowledgeLoaded = true;
    }

    private ExecutorService executor() {
        ExecutorService local = executor;
        if (local == null) {
            synchronized (this) {
                local = executor;
                if (local == null) {
                    int size = Math.max(1, Config.THREAD_POOL_SIZE.get());
                    local = Executors.newFixedThreadPool(size, new ThreadFactory() {
                        private final AtomicInteger n = new AtomicInteger(1);
                        @Override public Thread newThread(Runnable r) {
                            Thread t = new Thread(r, "llmchat-api-" + n.getAndIncrement());
                            t.setDaemon(true);
                            return t;
                        }
                    });
                    executor = local;
                    log.info("LLM Chat executor started with {} thread(s).", size);
                }
            }
        }
        return local;
    }

    public void shutdown() {
        if (executor != null) executor.shutdownNow();
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
            return; // not addressed to the AI -- ignore
        }

        if (trigger.prompt().isBlank()) {
            return; // someone typed just "@Grok" with no question
        }

        final MinecraftServer server = player.getServer();
        if (server == null) return;

        // --- Per-player cooldown ---
        int cooldown = Config.COOLDOWN_SECONDS.get();
        if (cooldown > 0) {
            long now = System.currentTimeMillis();
            Long last = lastRequestTime.get(player.getUUID());
            long cooldownMs = cooldown * 1000L;
            if (last != null && (now - last) < cooldownMs) {
                long remaining = (cooldownMs - (now - last) + 999) / 1000;
                player.sendSystemMessage(Component.literal(
                        "\u00A7e[LLM Chat] Please wait " + remaining + "s before asking again.\u00A7r"));
                return;
            }
            lastRequestTime.put(player.getUUID(), now);
        }

        final String playerName = player.getGameProfile().getName();
        final String model = trigger.model();
        final String question = trigger.prompt();

        if (Config.SHOW_THINKING_MESSAGE.get()) {
            broadcast(server, "\u00A77\u00A7o" + Config.AI_DISPLAY_NAME.get() + " is thinking...\u00A7r");
        }

        // Everything below runs on the worker thread. This is deliberate:
        // building the request on the worker ensures it sees the latest history
        // (including any Q&A that completed just before it), keeping the shared
        // conversation perfectly ordered when threadPoolSize=1.
        executor().submit(() -> {
            // Load knowledge file (cached after first load; use /llmreload to refresh).
            loadKnowledge();
            String knowledge = cachedKnowledge;

            // Build the request from the latest history snapshot.
            final List<ChatMessage> requestMessages =
                    history.buildRequestMessages(playerName, question, knowledge);

            String reply;
            try {
                reply = client.complete(model, requestMessages);
            } catch (Throwable t) {
                log.error("Unexpected error during AI completion", t);
                reply = "[error] Internal error while contacting the AI.";
            }

            final boolean isError = reply.startsWith("[error]");

            // Record this exchange in history (on the worker thread, ordered).
            history.addUserTurn(playerName, question);
            if (!isError) {
                history.addAssistantTurn(reply);
            }

            final String finalReply = postProcess(reply);

            // Hop back to the main thread to send chat packets.
            server.execute(() -> broadcastReply(server, finalReply, isError));
        });
    }

    /** Detects whether the message mentions a configured @name and extracts the question. */
    private Trigger detectTrigger(String message) {
        List<Pattern> patterns = getPatterns();
        List<? extends String> names = Config.TRIGGER_NAMES.get();
        if (names == null || names.isEmpty()) return null;

        int i = 0;
        for (Pattern p : patterns) {
            var m = p.matcher(message);
            if (m.find()) {
                String name = names.get(i);
                String prompt = m.group(1) == null ? "" : m.group(1).trim();
                return new Trigger(name, modelFor(name), prompt);
            }
            i++;
        }
        return null;
    }

    /**
     * Returns cached compiled patterns, rebuilding if the trigger names changed in config.
     * This avoids recompiling regexes on every chat line.
     */
    private List<Pattern> getPatterns() {
        List<String> names = Config.TRIGGER_NAMES.get().stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .toList();

        List<Pattern> local = cachedPatterns;
        List<String> localNames = cachedTriggerNames;
        if (local == null || !names.equals(localNames)) {
            local = new ArrayList<>();
            for (String name : names) {
                local.add(Pattern.compile(
                        "(?i)(?:^|\\s)@" + Pattern.quote(name) + "\\b[\\s,:.!?-]*(.*)",
                        Pattern.DOTALL));
            }
            cachedPatterns = local;
            cachedTriggerNames = names;
        }
        return local;
    }

    /** Resolves the model slug for a given trigger name using the nameModelMap, else default. */
    private String modelFor(String name) {
        Map<String, String> map = getModelMap();
        String mapped = map.get(name.toLowerCase(Locale.ROOT));
        return (mapped != null && !mapped.isBlank()) ? mapped : Config.DEFAULT_MODEL.get();
    }

    private Map<String, String> getModelMap() {
        List<String> entries = new ArrayList<>();
        for (var e : Config.NAME_MODEL_MAP.get()) {
            if (e != null) entries.add(e);
        }
        List<String> localEntries = cachedMapEntries;
        Map<String, String> local = cachedModelMap;
        if (local == null || !entries.equals(localEntries)) {
            local = new ConcurrentHashMap<>();
            for (String entry : entries) {
                int eq = entry.indexOf('=');
                if (eq > 0 && eq < entry.length() - 1) {
                    String key = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String val = entry.substring(eq + 1).trim();
                    if (!key.isEmpty() && !val.isEmpty()) {
                        local.put(key, val);
                    }
                }
            }
            cachedModelMap = local;
            cachedMapEntries = entries;
        }
        return local;
    }

    /**
     * Post-processes the reply: strips the [error] marker for display, removes stray
     * Minecraft formatting codes (\u00A7 + char) that the model might emit, and caps length.
     */
    private String postProcess(String reply) {
        String text = reply;
        if (text.startsWith("[error]")) {
            text = text.substring("[error]".length()).trim();
        }
        // Defensive: strip Minecraft formatting codes so the AI can't accidentally
        // inject color/formatting into the chat line.
        text = text.replaceAll("\u00A7.", "");
        int max = Config.MAX_REPLY_CHARS.get();
        if (text.length() > max) {
            text = text.substring(0, max).trim() + "...";
        }
        return text;
    }

    private void broadcastReply(MinecraftServer server, String text, boolean isError) {
        String name = Config.AI_DISPLAY_NAME.get();
        String prefix = isError
                ? "\u00A7c[" + name + "] \u00A77"
                : "\u00A7b<" + name + ">\u00A7r ";

        if (!Config.SPLIT_LONG_MESSAGES.get()) {
            broadcast(server, prefix + text);
            return;
        }

        int threshold = Config.SPLIT_THRESHOLD.get();
        if (text.length() <= threshold) {
            broadcast(server, prefix + text);
            return;
        }

        // Split long replies into multiple chat lines at word boundaries.
        List<String> chunks = splitAtWordBoundary(text, threshold);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (chunk.isEmpty()) continue;
            // First chunk gets the <AI> prefix, continuation lines get a subtle "... ".
            String chunkPrefix = (i == 0) ? prefix : "\u00A77... \u00A7r";
            broadcast(server, chunkPrefix + chunk);
        }
    }

    /** Splits text into chunks of at most maxChars, breaking at word boundaries. */
    private static List<String> splitAtWordBoundary(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                // Walk back to the last space within this chunk.
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            result.add(text.substring(start, end));
            start = end;
            // Skip the space we split on.
            while (start < text.length() && text.charAt(start) == ' ') start++;
        }
        return result;
    }

    private void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);
        server.getPlayerList().broadcastSystemMessage(component, false);
        log.info("[chat-broadcast] {}", message.replaceAll("\u00A7.", ""));
    }

    /** Small holder for a detected trigger. */
    private record Trigger(String name, String model, String prompt) {}
}
