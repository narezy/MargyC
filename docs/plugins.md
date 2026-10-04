# Writing MargyC mods

A MargyC mod is a small Java library that runs inside the Claude app. It can add hidden context to
every message, change UI strings and colors, react to screens opening, and do anything else Java and
reflection allow inside the app process.

> **Mods run with the full rights of the app**: they see your chats and your Claude session.
> Install only mods you trust, and only from people you trust.

## What a mod looks like

A mod is a `.mcmod` file — a zip with these files at its root:

| File | |
|---|---|
| `manifest.json` | name, author, entry class, settings |
| `classes.dex` | compiled code (`classes2.dex`, … if it does not fit in one) |
| `icon.png` | optional, square, shown in the list of mods |

Install it in the app: **Mods → Custom mods → Install mod** (*Моды → Свои моды → Установить мод*; the
Mods screen is in Russian), pick the file, restart Claude.
Mods load once per app start; turning a mod on or off, changing its settings or removing it applies
after a restart.

## manifest.json

```json
{
  "id": "hello",
  "name": "Hello, MargyC",
  "description": "What the mod does, shown in the app.",
  "author": "you",
  "version": "1.0",
  "api": 1,
  "entry": "com.example.hello.HelloMod",
  "settings": []
}
```

| Field | Required | |
|---|---|---|
| `id` | yes | unique id: lowercase latin letters, digits, `.`, `_`, `-` (2–64 chars). Installing a mod with the same id replaces it |
| `name` | yes | shown in the app |
| `entry` | yes | full name of the class that implements `MargyCPlugin` |
| `description` | no | shown in the app |
| `author` | no | |
| `version` | no | any string |
| `api` | no | the API version the mod needs, `1` by default. MargyC refuses mods for a newer API |
| `settings` | no | settings the app shows for the mod, see below |

### Settings

MargyC draws a settings screen for the mod from this list (tap the mod in the list). Values are stored
in the mod's `prefs()` under `key`; read them with `getBoolean` / `getString` / `getInt`, which fall
back to `default`.

```json
"settings": [
  {"key": "pirate", "type": "toggle", "title": "Pirate mode", "description": "Answer like a pirate.", "default": false},
  {"key": "nickname", "type": "text", "title": "Nickname", "hint": "Captain", "default": ""},
  {"key": "story", "type": "multiline", "title": "Backstory", "default": ""},
  {"key": "count", "type": "number", "title": "How many", "default": 3},
  {"key": "mood", "type": "choice", "title": "Mood", "options": ["calm", "chaotic"], "default": "calm"}
]
```

| type | value | read with |
|---|---|---|
| `toggle` | switch | `getBoolean` |
| `text`, `multiline` | text field | `getString` |
| `number` | integer field | `getInt` |
| `choice` | one of `options` | `getString` |

## The code

The entry class implements `cat.narezany.mods.api.MargyCPlugin` and has a public no-arg constructor:

```java
package com.example.hello;

import cat.narezany.mods.api.MargyCPlugin;
import cat.narezany.mods.api.PluginContext;

public final class HelloMod implements MargyCPlugin {
    @Override
    public void onCreate(PluginContext ctx) {
        ctx.addPromptContext(() -> ctx.getBoolean("pirate") ? "Answer like a friendly pirate, arr." : null);
    }
}
```

`onCreate` runs once per app start, from `Application.onCreate`, on the main thread. Keep it fast;
start your own thread for long work. If it throws, MargyC shows the error under the mod in the list
and in the journal, and the other mods still load.

### PluginContext (API 1)

| Method | |
|---|---|
| `apiVersion()` | API version, `1` |
| `margycVersion()` | MargyC version, e.g. `"1.1"` |
| `app()` | the `Application` |
| `id()`, `dir()` | the mod id and its folder (read only) |
| `prefs()` | the mod's own `SharedPreferences`; settings live here too |
| `getBoolean/getString/getInt(key)` | a setting value or its `default` |
| `log(message)` | a line in the MargyC journal (*Моды → Мемные модели → Журнал*) |
| `addPromptContext(Supplier<String>)` | hidden context added to every message you send: Claude sees it, the chat does not. Called on every send; return `null` or `""` to add nothing |
| `addTextFilter(UnaryOperator<String>)` | changes plain UI strings (Compose `Text(String)`; chat messages are not included). Called very often: be fast, return the same string when there is nothing to change |
| `addColorFilter(IntUnaryOperator)` | changes app colors (ARGB) as they are created, after the MargyC accent. Also hot. Colors are read at start, so changes show after a restart |
| `addActivityCallbacks(callbacks)` | `ActivityLifecycleCallbacks` for app screens (Claude's and MargyC's: skip classes from `cat.narezany.mods.`) |
| `currentActivity()` | the activity in the foreground or `null` |

The mod's class loader has the app's class loader as its parent, so the mod can also reach Claude's
own classes and MargyC's internals by reflection. Claude's classes are obfuscated and their names
change with every Claude update: find what you need by strings and signatures, not by names, and
fail quietly when it is not there.

## Building

You need Python 3.8+ and JDK 17+. From the MargyC repo:

```sh
python3 tools/build_mod.py path/to/my-mod        # -> <id>.mcmod
```

The folder layout:

```
my-mod/
  manifest.json
  icon.png            optional
  src/com/you/mymod/MyMod.java
  libs/*.jar          optional, packed into the dex
```

The script compiles against `android.jar` (API 35) and the MargyC API, then runs `d8` with
`--min-api 29`. The API classes are not packed into the mod: they come from MargyC at run time.

A complete example is in [`examples/hello`](../examples/hello): settings, hidden prompt context and a
greeting toast.

```sh
python3 tools/build_mod.py examples/hello
```

### Building by hand

If you prefer your own build: compile your classes with `javac --release 8` against `android.jar` and
the sources in [`src/cat/narezany/mods/api`](../src/cat/narezany/mods/api), dex them with `d8`
(min API 29) without the API classes, and zip `manifest.json` + `classes.dex` (+ `icon.png`).

## Tips

- Android 14+ loads only read-only dex files; MargyC marks them so on install.
- Everything a mod changes is undone by turning it off and restarting.
- Crashes in a mod crash Claude. MargyC's crash screen shows the stack trace on the next start; turn
  the mod off there or in *Моды → Свои моды*.
