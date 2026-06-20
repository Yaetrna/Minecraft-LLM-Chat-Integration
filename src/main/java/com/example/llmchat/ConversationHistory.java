package com.example.llmchat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A single, server-wide conversation shared by every player.
 *
 * Player A asking something and player B asking something later will both see and
 * build on the same context — exactly the "shared server-wide conversation" behaviour
 * requested.
 *
 * Thread-safety: the chat event fires on the main server thread, but the AI reply is
 * appended from a background thread. All access is therefore synchronized on a private
 * lock. The deque is bounded so memory can't grow without limit.
 */
public final class ConversationHistory {

    private final Object lock = new Object();
    private final Deque<ChatMessage> messages = new ArrayDeque<>();

    /** Records a player's question. We prefix the player name so the model knows who is speaking. */
    public void addUserTurn(String playerName, String text) {
        if (Config.HISTORY_SIZE.get() <= 0) {
            return; // memory disabled
        }
        synchronized (lock) {
            messages.addLast(ChatMessage.user(playerName + ": " + text));
            trim();
        }
    }

    /** Records the AI's reply so future turns have context. */
    public void addAssistantTurn(String text) {
        if (Config.HISTORY_SIZE.get() <= 0) {
            return;
        }
        synchronized (lock) {
            messages.addLast(ChatMessage.assistant(text));
            trim();
        }
    }

    /**
     * Builds the full message list to send to the model: system prompt first, then the
     * shared history, then the brand-new user turn (which is passed in directly so it is
     * always included even when history is disabled).
     */
    public List<ChatMessage> buildRequestMessages(String playerName, String newUserText) {
        List<ChatMessage> out = new ArrayList<>();
        out.add(ChatMessage.system(Config.SYSTEM_PROMPT.get()));
        synchronized (lock) {
            out.addAll(messages);
        }
        // Always include the current question explicitly.
        out.add(ChatMessage.user(playerName + ": " + newUserText));
        return out;
    }

    /** Wipes the shared memory (used by the /llmreset command). */
    public void clear() {
        synchronized (lock) {
            messages.clear();
        }
    }

    /** Drops oldest entries until we're within the configured history size. */
    private void trim() {
        int max = Config.HISTORY_SIZE.get();
        while (messages.size() > max) {
            messages.removeFirst();
        }
    }
}
