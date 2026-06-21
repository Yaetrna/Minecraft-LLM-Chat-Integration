# LLM Chat -- Minecraft Forge mod (1.20.1)

Talk to an LLM from inside Minecraft. A player types a message that mentions a
configured name (e.g. **`@Grok what is the speed of light?`**) and the AI's answer is
broadcast to everyone on the server.

Built for **Minecraft 1.20.1 / Forge 47.x**, which is what **TerraFirmaGreg-Modern
0.12.7** runs on. It is a **server-side** mod -- players do **not** need to install
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
3. Copy `tfg_knowledge.md` from this repo into the server's **`config/`** folder,
   renamed to **`llmchat-knowledge.md`**. This gives the AI its TerraFirmaGreg
   domain knowledge. (See section 5 for details.)
4. Start the server **once**. This generates the config file, then you'll edit it.
5. Stop the server.
6. Open **`config/llmchat-common.toml`** and set your API key (next section).
7. Start the server again. Done.

---

## 4. Configure -- `config/llmchat-common.toml`

The most important setting is your OpenRouter API key. Get one at
<https://openrouter.ai/keys>.

```toml
[connection]
    apiKey = "sk-or-...xxxx"   # REQUIRED
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
    knowledgeFile = "config/llmchat-knowledge.md"   # TFG domain knowledge
    historySize = 50        # shared server-wide memory; 0 disables memory
    maxTokens = 1500        # max reply length in tokens (~1000-1200 words)
    temperature = 0.7
    timeoutSeconds = 45
    maxReplyChars = 4000    # hard cap on total reply text
    showThinkingMessage = true

[limits]
    threadPoolSize = 1      # 1 = ordered history (recommended); higher = parallel but unordered
    cooldownSeconds = 10    # per-player cooldown between requests; 0 = no limit
    splitLongMessages = true
    splitThreshold = 250    # chars per chat line before splitting
```

### Picking a model

`defaultModel` (and any entry in `nameModelMap`) is an OpenRouter model slug. For
knowledge-heavy questions about a niche modpack like TerraFirmaGreg, **bigger models
perform much better**. Smaller/cheaper models will give shorter, less accurate answers.

| What you want      | Slug                              | Notes |
|--------------------|-----------------------------------|-------|
| xAI Grok           | `x-ai/grok-2-1212`                | Good default, strong reasoning |
| OpenAI GPT-4o      | `openai/gpt-4o`                   | Excellent knowledge, higher cost |
| OpenAI GPT-4o mini | `openai/gpt-4o-mini`              | Cheap but less knowledgeable |
| Anthropic Claude   | `anthropic/claude-3.5-sonnet`     | Excellent, thorough answers |
| Meta Llama 3.1     | `meta-llama/llama-3.1-8b-instruct`| Very cheap, weaker on niche topics |

Full list: <https://openrouter.ai/models>

If the AI seems "stupid", the most likely causes are: (1) the model is too small -- try
gpt-4o or claude-3.5-sonnet, (2) maxTokens is too low -- the answer gets cut off, (3) the
knowledge file isn't loaded -- check the server log for "Knowledge file" lines.

### Multiple AIs at once

Want `@Grok` and `@GPT` to be *different* models?

```toml
triggerNames = ["Grok", "GPT"]
nameModelMap = ["Grok=x-ai/grok-2-1212", "GPT=openai/gpt-4o"]
```

---

## 5. The knowledge file -- giving the AI TFG expertise

The mod can inject a text file into every AI request as a system message. This is how you
give the AI domain knowledge about TerraFirmaGreg that it wouldn't otherwise know.

A starter file (`tfg_knowledge.md`) ships with this repo. It covers:

- What TFG is and its two parent mods (TerraFirmaCraft + GregTech CEu Modern)
- TFC survival mechanics (stone age, metal ages, anvil, food, agriculture, geology)
- GregTech progression (voltage tiers, steam age, ore processing, circuits, machines)
- TFG integration notes and the progression flow from stone age to industrial

**Important:** The starter file is based on general knowledge of the parent mods. Some
recipes and mechanics may differ in TFG 0.12.7 specifically. You should:

1. Review it against your server and update anything that's wrong.
2. Add server-specific info: house rules, server mods, coordinates, custom recipes.
3. After editing, run **`/llmreload`** in-game to reload it without restarting.

The file is read once on first chat message and cached. Use `/llmreload` to refresh.

Where to find authoritative TFG docs to add:
- The TerraFirmaGreg CurseForge page (for changelogs and version-specific info)
- TerraFirmaCraft wiki: <https://terrafirmacraft.com/wiki>
- GregTech CEu wiki: <https://wiki.gt4u.eu>
- Your own server's JEI (Just Enough Items) -- look up recipes in-game

---

## 6. How players use it

Just mention a trigger name in normal chat:

```
@Grok what is the speed of light?
hey @AI, how do I make bronze in TerraFirmaGreg?
@grok: what's the fastest way to get steel?
```

The trigger is **case-insensitive** and can appear anywhere in the line. The AI replies
to the whole server, e.g.:

```
<AI> The speed of light in a vacuum is about 299,792,458 m/s.
```

Long answers are split into multiple chat lines automatically (configurable via
`splitThreshold`). Continuation lines start with `...`.

Because memory is **shared server-wide**, the AI remembers recent questions from
everyone (up to `historySize` messages).

### Operator commands

```
/llmreset      # clears the shared conversation memory (OP level 2)
/llmreload     # reloads the knowledge file from disk (OP level 2)
```

---

## 7. Design notes (the "how it works")

- **`ServerChatEvent`** fires on the main server thread for every chat line. We detect an
  `@Name` mention with a cached regex, but we **never cancel** the event, so the player's
  original message still shows normally.
- The **network call is async**. A background thread pool (`llmchat-api-*`) makes the HTTP
  request so the game tick is never blocked. When the reply arrives we hop back to the
  main thread with `server.execute(...)` before sending any chat packets (Minecraft
  networking is **not** thread-safe).
- **Ordered history:** the request is built on the worker thread, not the main thread.
  With `threadPoolSize=1` (default), each request sees all prior completed exchanges,
  keeping the shared conversation perfectly sequenced. Setting a higher pool size allows
  parallel requests but may jumble history order.
- **Rate limiting:** per-player cooldown prevents spam. Each player must wait
  `cooldownSeconds` between requests. Violations show a private "please wait" message.
- **Long replies** are split at word boundaries into multiple chat lines so they stay
  readable in the Minecraft chat UI.
- **Knowledge injection:** the knowledge file is loaded once, cached, and injected as a
  second system message on every request. `/llmreload` refreshes it from disk.
- **No hardcoded secrets**: the API key lives only in the config file.
- **Pure Java HTTP + Gson** (Gson is already bundled in Minecraft), so there are no extra
  runtime dependencies to shade in.

---

## 8. Troubleshooting

| Symptom | Fix |
|--------|-----|
| AI never responds | Check `apiKey` is set and the server was restarted. See `logs/latest.log` for `LlmChat` lines. |
| `HTTP 401` in chat | API key is wrong/expired. |
| `HTTP 402` | OpenRouter account out of credit. |
| `HTTP 404` on model | The `model` slug is wrong -- copy it exactly from openrouter.ai/models. |
| "took too long (timeout)" | Raise `timeoutSeconds`, or pick a faster model. |
| AI answers are short/stupid | Use a bigger model (gpt-4o, claude-3.5-sonnet). Increase `maxTokens`. Check the knowledge file is loaded. |
| AI doesn't know TFG recipes | Make sure `knowledgeFile` points to a real file. Run `/llmreload`. Add more detail to the knowledge file. |
| AI answers cut off mid-sentence | Increase `maxTokens` (1500+ recommended). |
| Answers out of order | Keep `threadPoolSize` at 1. Higher values process requests in parallel. |
| Player can spam the AI | Set `cooldownSeconds` to a non-zero value (default 10). |
| Build fails on Java version | The toolchain auto-downloads JDK 17; ensure you have internet on first build. |

---

## 9. File map

```
build.gradle                      ForgeGradle build + JDK17 toolchain
settings.gradle                   Foojay resolver (auto JDK download)
gradle.properties                 build memory/daemon settings
gradlew / gradlew.bat             Gradle wrapper launchers
LICENSE                           MIT license
tfg_knowledge.md                  Starter knowledge file (copy to config/)
src/main/resources/
  META-INF/mods.toml              Forge mod metadata
  pack.mcmeta                     resource/data pack version
src/main/java/com/example/llmchat/
  LlmChatMod.java                 entry point, config + command registration
  Config.java                     all TOML config options
  ChatHandler.java                detects @Name, rate limits, async call, splits + broadcasts reply
  OpenRouterClient.java           OpenAI-compatible HTTP client (Java HttpClient)
  ConversationHistory.java        shared, bounded, thread-safe memory
  ChatMessage.java                role/content record
```
