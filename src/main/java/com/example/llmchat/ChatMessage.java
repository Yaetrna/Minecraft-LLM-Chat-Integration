package com.example.llmchat;

/**
 * A single chat turn in the conversation, in the role/content shape the
 * OpenAI-compatible API expects ("system", "user", or "assistant").
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }
}
