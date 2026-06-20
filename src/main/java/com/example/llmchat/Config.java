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
    public static final ForgeConfigSpec.IntValue HISTORY_SIZE;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;
    public static final ForgeConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_REPLY_CHARS;
    public static final ForgeConfigSpec.ConfigValue<String> AI_DISPLAY_NAME;
    public static final ForgeConfigSpec.BooleanValue SHOW_THINKING_MESSAGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("LLM Chat — connect in-game chat to an LLM through OpenRouter.").push("connection");

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
                         "Examples: x-ai/grok-2-1212, openai/gpt-4o-mini, anthropic/claude-3.5-sonnet",
                         "Full list: https://openrouter.ai/models")
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
                         "Example: [\"Grok=x-ai/grok-2-1212\", \"GPT=openai/gpt-4o-mini\"]")
                .defineListAllowEmpty(Arrays.asList("nameModelMap"),
                        () -> Arrays.asList("Grok=x-ai/grok-2-1212"),
                        o -> o instanceof String);

        b.pop();

        b.comment("How the AI behaves and how replies are shown.").push("behaviour");

        AI_DISPLAY_NAME = b
                .comment("The name shown in chat in front of the AI's replies.")
                .define("aiDisplayName", "AI");

        SYSTEM_PROMPT = b
                .comment("System prompt sent to the model on every request.")
                .define("systemPrompt",
                        "You are a helpful assistant living inside a Minecraft server running the "
                      + "TerraFirmaGreg-Modern modpack. Players talk to you through chat. Keep answers "
                      + "concise and friendly because they appear in a small chat window. Avoid markdown "
                      + "formatting and very long replies.");

        HISTORY_SIZE = b
                .comment("How many previous messages (shared across ALL players) to keep as context.",
                         "0 = no memory (each question is standalone). Higher = more context but more tokens.")
                .defineInRange("historySize", 12, 0, 200);

        MAX_TOKENS = b
                .comment("Maximum tokens in the model's reply.")
                .defineInRange("maxTokens", 400, 1, 8000);

        TEMPERATURE = b
                .comment("Sampling temperature (0.0 = deterministic, ~1.0 = creative).")
                .defineInRange("temperature", 0.7D, 0.0D, 2.0D);

        TIMEOUT_SECONDS = b
                .comment("How long to wait for the API before giving up.")
                .defineInRange("timeoutSeconds", 30, 5, 120);

        MAX_REPLY_CHARS = b
                .comment("Hard cap on reply length posted to chat (safety against huge messages).")
                .defineInRange("maxReplyChars", 1500, 100, 10000);

        SHOW_THINKING_MESSAGE = b
                .comment("If true, show a brief '<AI> is thinking...' line while waiting for the reply.")
                .define("showThinkingMessage", true);

        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
