package com.example.llmchat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the LLM Chat mod.
 *
 * Forge reads these defaults the first time the mod runs and writes a human-editable
 * file to:  <server folder>/config/llmchat-common.toml
 *
 * After the first launch, STOP the server, open that file, paste your OpenRouter API
 * key, tweak anything you like, and start the server again.
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    // --- Connection ---
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_MODEL;

    // --- Triggers ---
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TRIGGER_NAMES;
    // Optional per-name model overrides, e.g. "Grok=x-ai/grok-2-1212".
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAME_MODEL_MAP;

    // --- Behaviour ---
    public static final ForgeConfigSpec.ConfigValue<String> SYSTEM_PROMPT;
    public static final ForgeConfigSpec.ConfigValue<String> KNOWLEDGE_FILE;
    public static final ForgeConfigSpec.IntValue HISTORY_SIZE;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;
    public static final ForgeConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_REPLY_CHARS;
    public static final ForgeConfigSpec.ConfigValue<String> AI_DISPLAY_NAME;
    public static final ForgeConfigSpec.BooleanValue SHOW_THINKING_MESSAGE;

    // --- Limits ---
    public static final ForgeConfigSpec.IntValue THREAD_POOL_SIZE;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SPLIT_LONG_MESSAGES;
    public static final ForgeConfigSpec.IntValue SPLIT_THRESHOLD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("LLM Chat -- connect in-game chat to an LLM through OpenRouter.").push("connection");

        API_KEY = b
                .comment("Your OpenRouter API key. Get one at https://openrouter.ai/keys",
                         "REQUIRED. Leave blank to disable the mod.")
                .define("apiKey", "");

        BASE_URL = b
                .comment("OpenRouter chat-completions endpoint.",
                         "Change this only if you use a different OpenAI-compatible provider.")
                .define("baseUrl", "https://openrouter.ai/api/v1/chat/completions");

        DEFAULT_MODEL = b
                .comment("Default model slug used when a trigger name has no specific override.",
                         "Examples: x-ai/grok-2-1212, openai/gpt-4o, anthropic/claude-3.5-sonnet",
                         "Full list: https://openrouter.ai/models",
                         "For knowledge-heavy questions about niche modpacks, bigger models",
                         "(gpt-4o, claude-3.5-sonnet, grok-2) perform much better than mini/small ones.")
                .define("defaultModel", "x-ai/grok-2-1212");

        b.pop();

        b.comment("Which @names players can mention to summon the AI.").push("triggers");

        TRIGGER_NAMES = b
                .comment("Names that trigger the AI when mentioned with an @, e.g. typing",
                         "  @Grok what is the speed of light?",
                         "Case-insensitive. You can list several.")
                .defineListAllowEmpty(Arrays.asList("triggerNames"),
                        () -> Arrays.asList("Grok", "AI"),
                        o -> o instanceof String);

        NAME_MODEL_MAP = b
                .comment("Optional: map a specific trigger name to a specific model.",
                         "Format: \"Name=model/slug\". Names not listed here use defaultModel.",
                         "Example: [\"Grok=x-ai/grok-2-1212\", \"GPT=openai/gpt-4o\"]")
                .defineListAllowEmpty(Arrays.asList("nameModelMap"),
                        () -> Arrays.asList("Grok=x-ai/grok-2-1212"),
                        o -> o instanceof String);

        b.pop();

        b.comment("How the AI behaves and how replies are shown.").push("behaviour");

        AI_DISPLAY_NAME = b
                .comment("The name shown in chat in front of the AI's replies.")
                .define("aiDisplayName", "AI");

        SYSTEM_PROMPT = b
                .comment("System prompt sent to the model on every request.",
                         "This defines the AI's personality and instructions. You can make it",
                         "as detailed as you want. The knowledgeFile below is ALSO injected as",
                         "a separate system message, so keep this for personality/instructions.")
                .define("systemPrompt",
                        "You are a helpful assistant living inside a Minecraft server running the "
                      + "TerraFirmaGreg-Modern modpack (version 0.12.7, Minecraft 1.20.1 with Forge). "
                      + "Players talk to you through the in-game chat. You are knowledgeable about "
                      + "Minecraft, TerraFirmaCraft, GregTech, and the TerraFirmaGreg modpack. Give "
                      + "thorough, accurate answers. You may use multiple chat lines for longer answers "
                      + "-- they will be split automatically. Avoid markdown formatting (no **bold**, "
                      + "no #headers, no code blocks). Use plain text only. Be friendly and conversational.");

        KNOWLEDGE_FILE = b
                .comment("Path to a markdown/text file whose content is injected into every request",
                         "as an additional system message. Use this to give the AI domain knowledge,",
                         "e.g. TerraFirmaGreg crafting recipes, mechanics, version-specific changes.",
                         "Leave empty to disable. The file is read on first use and cached;",
                         "use /llmreload to reload it without restarting the server.",
                         "Example: \"config/llmchat-knowledge.md\"")
                .define("knowledgeFile", "config/llmchat-knowledge.md");

        HISTORY_SIZE = b
                .comment("How many previous messages (shared across ALL players) to keep as context.",
                         "0 = no memory (each question is standalone). Higher = more context but more tokens.",
                         "Each message pair (question + answer) uses 2 slots.")
                .defineInRange("historySize", 50, 0, 500);

        MAX_TOKENS = b
                .comment("Maximum tokens in the model's reply.",
                         "Higher = longer, more detailed answers. 1500 is roughly 1000-1200 words.",
                         "If the AI seems to cut off mid-sentence, increase this.")
                .defineInRange("maxTokens", 1500, 1, 16000);

        TEMPERATURE = b
                .comment("Sampling temperature (0.0 = deterministic, ~1.0 = creative).")
                .defineInRange("temperature", 0.7D, 0.0D, 2.0D);

        TIMEOUT_SECONDS = b
                .comment("How long to wait for the API before giving up.")
                .defineInRange("timeoutSeconds", 45, 5, 300);

        MAX_REPLY_CHARS = b
                .comment("Hard cap on total reply length posted to chat (safety against huge messages).",
                         "The reply is split into multiple chat lines at splitThreshold chars each.")
                .defineInRange("maxReplyChars", 4000, 100, 20000);

        SHOW_THINKING_MESSAGE = b
                .comment("If true, show a brief '<AI> is thinking...' line while waiting for the reply.")
                .define("showThinkingMessage", true);

        b.pop();

        b.comment("Rate limiting and threading.").push("limits");

        THREAD_POOL_SIZE = b
                .comment("Number of background threads for API calls.",
                         "1 (default) = requests are processed one at a time in order, which keeps",
                         "conversation history perfectly sequenced. Higher values allow parallel",
                         "requests but may cause out-of-order history with the shared memory model.",
                         "Recommended: keep at 1 unless you have many concurrent players.")
                .defineInRange("threadPoolSize", 1, 1, 16);

        COOLDOWN_SECONDS = b
                .comment("Per-player cooldown in seconds between AI requests.",
                         "0 = no cooldown. Prevents a single player from spamming expensive API calls.",
                         "Each violation shows a 'please wait' message to that player only.")
                .defineInRange("cooldownSeconds", 10, 0, 600);

        SPLIT_LONG_MESSAGES = b
                .comment("If true, long AI replies are split into multiple chat messages.",
                         "Minecraft chat lines have a practical display limit; splitting keeps",
                         "long answers readable instead of one giant scroll.")
                .define("splitLongMessages", true);

        SPLIT_THRESHOLD = b
                .comment("Maximum characters per chat line when splitLongMessages is true.",
                         "Lines longer than this are broken at word boundaries.",
                         "250 is a good default for most Minecraft chat UIs.")
                .defineInRange("splitThreshold", 250, 50, 2000);

        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
