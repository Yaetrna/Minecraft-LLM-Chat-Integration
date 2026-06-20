package com.example.llmchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Talks to OpenRouter's (OpenAI-compatible) chat-completions endpoint.
 *
 * All methods here are designed to be called OFF the main server thread, because
 * a network request can take hundreds of milliseconds to several seconds and we must
 * never block the game tick. The caller (ChatHandler) runs this on a background
 * executor and then hops back to the main thread to post the reply.
 */
public final class OpenRouterClient {

    private static final Gson GSON = new GsonBuilder().create();

    // One shared HttpClient instance — it manages its own connection pool.
    private final HttpClient http;
    private final Logger log;

    public OpenRouterClient(Logger log) {
        this.log = log;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Sends the conversation to the model and returns the assistant's reply text.
     *
     * @param model    the model slug, e.g. "x-ai/grok-2-1212"
     * @param messages the full message list (system + history + new user turn)
     * @return the reply text, or a human-readable error string prefixed with "[error]"
     */
    public String complete(String model, List<ChatMessage> messages) {
        String apiKey = Config.API_KEY.get();
        if (apiKey == null || apiKey.isBlank()) {
            return "[error] No OpenRouter API key configured. Edit config/llmchat-common.toml.";
        }

        String body = buildRequestBody(model, messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.BASE_URL.get()))
                .timeout(Duration.ofSeconds(Config.TIMEOUT_SECONDS.get()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                // OpenRouter likes these headers for attribution/ranking. Harmless if absent.
                .header("HTTP-Referer", "https://minecraft.server.local/llmchat")
                .header("X-Title", "Minecraft LLM Chat")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                log.warn("OpenRouter returned HTTP {}: {}", code, truncate(response.body(), 500));
                return "[error] The AI service returned an error (HTTP " + code + "). "
                        + extractApiError(response.body());
            }

            return parseReply(response.body());

        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("OpenRouter request timed out", e);
            return "[error] The AI took too long to respond (timeout).";
        } catch (Exception e) {
            log.error("OpenRouter request failed", e);
            return "[error] Could not reach the AI service: " + e.getClass().getSimpleName();
        }
    }

    /** Builds the JSON request body using Gson (so quoting/escaping is always correct). */
    private String buildRequestBody(String model, List<ChatMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", Config.MAX_TOKENS.get());
        root.addProperty("temperature", Config.TEMPERATURE.get());

        JsonArray msgs = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            msgs.add(o);
        }
        root.add("messages", msgs);
        return GSON.toJson(root);
    }

    /** Pulls choices[0].message.content out of the response JSON. */
    private String parseReply(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                // Some providers return an "error" object even with HTTP 200.
                if (root.has("error")) {
                    return "[error] " + extractApiError(json);
                }
                return "[error] The AI returned an empty response.";
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            JsonElement content = message.get("content");
            if (content == null || content.isJsonNull()) {
                return "[error] The AI returned no text.";
            }
            String text = content.getAsString().trim();
            return text.isEmpty() ? "[error] The AI returned an empty message." : text;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response: {}", truncate(json, 500), e);
            return "[error] Could not understand the AI's response.";
        }
    }

    /** Best-effort extraction of a provider error message for nicer in-game feedback. */
    private String extractApiError(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error")) {
                JsonElement err = root.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return err.getAsJsonObject().get("message").getAsString();
                }
                return err.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
