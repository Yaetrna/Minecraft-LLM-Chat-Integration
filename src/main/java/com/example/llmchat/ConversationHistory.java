package com.example.llmchat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A single, server-wide conversation shared by every player.
 *
 * Player A asking something and player B asking something later will both see and
 * build on the same context -- exactly the "shared server-wide conversation" behaviour
 * requested.
 *
 * Thread-safety: the chat event fires on the main server thread, but the AI reply is
 * appended from a background thread. All access is therefore synchronized on a private
 * lock. The deque is bounded so memory can't grow without limit.
 *
 * Ordering: buildRequestMessages and the add*Turn methods are called from the worker
 * thread (not the main thread), so with a single-thread executor the conversation
 * history stays perfectly sequenced: each request sees all prior completed exchanges.
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
     * Builds the full message list to send to the model:
     *   1. System prompt (personality / instructions)
     *   2. Knowledge file content (if any) as a second system message
     *   3. Shared conversation history
     *   4. The brand-new user turn (always included, even when history is disabled)
     *
     * @param knowledge    content of the knowledge file, or empty/null to skip
     */
    public List<ChatMessage> buildRequestMessages(String playerName, String newUserText, String knowledge) {
        List<ChatMessage> out = new ArrayList<>();
        out.add(ChatMessage.system(Config.SYSTEM_PROMPT.get()));

        // Inject domain knowledge (e.g. TerraFirmaGreg docs) as a second system message.
        if (knowledge != null && !knowledge.isBlank()) {
            out.add(ChatMessage.system(knowledge));
        }

        synchronized (lock) {
            out.addAll(messages);
        }

        // Always include the current question explicitly (it's not in history yet).
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
