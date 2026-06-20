# LLM Chat — Minecraft Forge mod (1.20.1)

Talk to an LLM from inside Minecraft. A player types a message that mentions a
configured name (e.g. **`@Grok what is the speed of light?`**) and the AI's answer is
broadcast to everyone on the server.

Built for **Minecraft 1.20.1 / Forge 47.x**, which is what **TerraFirmaGreg-Modern
0.12.7** runs on. It is a **server-side** mod — players do **not** need to install
anything. It uses **OpenRouter**, so one API key gives you access to Grok, GPT, Claude,
Llama and many more just by changing a model string.

---

## 1. Why a mod (not a "plugin")

Minecraft "plugins" (Bukkit/Spigot/Paper) only exist on a different server software that
is **incompatible with Forge modpacks** like TerraFirmaGreg. To run alongside TFG you need
a **Forge mod**, which is exactly what this is. It hooks Forge's `ServerChatEvent`,
so it sees chat from every player and can reply through the server.

---

## 2. Build the mod (produce the .jar)

You need nothing but this folder. The Gradle wrapper auto-downloads the correct
Gradle, and the build is configured to auto-download a **JDK 17** toolchain (required by
MC 1.20.1) even if your system Java is a different version.

```bash
# from this folder
./gradlew build           # macOS / Linux / Git-Bash on Windows
gradlew.bat build         # Windows cmd / PowerShell
```

When it finishes, the mod jar is at:

```
build/libs/llmchat-1.0.0.jar
```

> First build downloads Forge + decompiles Minecraft and can take several minutes.
> Subsequent builds are fast.

---

## 3. Install on your server

1. Stop the server.
2. Copy `build/libs/llmchat-1.0.0.jar` into the server's **`mods/`** folder
   (the same folder that holds all the TerraFirmaGreg mods).
3. Start the server **once**. This generates the config file, then you'll edit it.
4. Stop the server.
5. Open **`config/llmchat-common.toml`** and set your API key (next section).
6. Start the server again. Done.

---

## 4. Configure — `config/llmchat-common.toml`

The most important setting is your OpenRouter API key. Get one at
<https://openrouter.ai/keys>.

```toml
[connection]
    apiKey = "sk-or-v1-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"   # REQUIRED
    baseUrl = "https://openrouter.ai/api/v1/chat/completions"
    defaultModel = "x-ai/grok-2-1212"

[triggers]
    # Players summon the AI by mentioning any of these with an @
    triggerNames = ["Grok", "AI"]
    # Optional: give specific names specific models
    nameModelMap = ["Grok=x-ai/grok-2-1212"]

[behaviour]
    aiDisplayName = "AI"
    systemPrompt = "You are a helpful assistant living inside a Minecraft server..."
    historySize = 12      # shared, server-wide memory; 0 disables memory
    maxTokens = 400
    temperature = 0.7
    timeoutSeconds = 30
    maxReplyChars = 1500
    showThinkingMessage = true
```

### Picking a model
`defaultModel` (and any entry in `nameModelMap`) is an OpenRouter model slug. Examples:

| What you want      | Slug                              |
|--------------------|-----------------------------------|
| xAI Grok           | `x-ai/grok-2-1212`                |
| OpenAI GPT-4o mini | `openai/gpt-4o-mini`              |
| Anthropic Claude   | `anthropic/claude-3.5-sonnet`     |
| Meta Llama (cheap) | `meta-llama/llama-3.1-8b-instruct`|

Full list: <https://openrouter.ai/models>

### Multiple AIs at once
Want `@Grok` and `@GPT` to be *different* models?

```toml
triggerNames = ["Grok", "GPT"]
nameModelMap = ["Grok=x-ai/grok-2-1212", "GPT=openai/gpt-4o-mini"]
```

---

## 5. How players use it

Just mention a trigger name in normal chat:

```
@Grok what is the speed of light?
hey @AI, how do I make bronze in TerraFirmaGreg?
@grok: write a haiku about cobblestone
```

The trigger is **case-insensitive** and can appear anywhere in the line. The AI replies
to the whole server, e.g.:

```
<AI> The speed of light in a vacuum is about 299,792,458 m/s.
```

Because memory is **shared server-wide**, the AI remembers recent questions from
everyone (up to `historySize` messages).

### Operator command
```
/llmreset      # clears the shared conversation memory (requires OP / permission level 2)
```

---

## 6. Design notes (the "how it works")

- **`ServerChatEvent`** fires on the main server thread for every chat line. We detect an
  `@Name` mention with a regex, but we **never cancel** the event, so the player's
  original message still shows normally.
- The **network call is async**. A 3-thread daemon pool (`llmchat-api-*`) makes the HTTP
  request so the game tick is never blocked. When the reply arrives we hop back to the
  main thread with `server.execute(...)` before sending any chat packets (Minecraft
  networking is **not** thread-safe).
- **History** is one shared, bounded deque guarded by a lock — safe to touch from both
  the chat thread and the API thread.
- **No hardcoded secrets**: the API key lives only in the config file.
- **Pure Java HTTP + Gson** (Gson is already bundled in Minecraft), so there are no extra
  runtime dependencies to shade in.

---

## 7. Troubleshooting

| Symptom | Fix |
|--------|-----|
| AI never responds | Check `apiKey` is set and the server was restarted. See `logs/latest.log` for `LlmChat` lines. |
| `HTTP 401` in chat | API key is wrong/expired. |
| `HTTP 402` | OpenRouter account out of credit. |
| `HTTP 404` on model | The `model` slug is wrong — copy it exactly from openrouter.ai/models. |
| "took too long (timeout)" | Raise `timeoutSeconds`, or pick a faster/smaller model. |
| Build fails on Java version | The toolchain auto-downloads JDK 17; ensure you have internet on first build. |

---

## 8. File map

```
build.gradle                      ForgeGradle build + JDK17 toolchain
settings.gradle                   Foojay resolver (auto JDK download)
gradle.properties                 build memory/daemon settings
gradlew / gradlew.bat             Gradle wrapper launchers
src/main/resources/
  META-INF/mods.toml              Forge mod metadata
  pack.mcmeta                     resource/data pack version
src/main/java/com/example/llmchat/
  LlmChatMod.java                 entry point, config + command registration
  Config.java                     all TOML config options
  ChatHandler.java                detects @Name, runs async call, broadcasts reply
  OpenRouterClient.java           OpenAI-compatible HTTP client (Java HttpClient)
  ConversationHistory.java        shared, bounded, thread-safe memory
  ChatMessage.java                role/content record
```
