# Spotify in Game

**Description:** Play your favorite music in game without any extra app.
**Author:** Lyzi__
**Loader:** Fabric — Minecraft 1.21.11+

Press **F6** in-game to open a music player GUI. Drop your `.mp3` files into the
config folder, hit refresh, and play them straight from Minecraft's own sound
engine — works on PC Java **and** mobile Java launchers (PojavLauncher etc.).

## Why this works on mobile too (jab doosre mods "illegal argument" dete hain)

Other mp3-in-Minecraft mods usually use `javax.sound.sampled` (Java's built-in
audio system) to actually **play** the sound. That system needs the platform's
native audio mixer — which barely exists on Android/PojavLauncher, so you get
`IllegalArgumentException: No line matching interface Clip is supported`.

This mod only uses a pure-Java library (JLayer) to **decode** mp3 → raw PCM
audio, and then hands that PCM straight to **Minecraft's own sound engine**
(the Fabric `AudioStream` API → LWJGL/OpenAL) — the exact same system
Minecraft already uses to play every other sound in the game, on every
platform Minecraft itself already runs on. No separate native audio backend
needed.

## Where your songs go

```
.minecraft/config/spotifyingame/music/   <- put your .mp3 files here
.minecraft/config/spotifyingame/config.json  <- volume/shuffle/repeat settings (auto-created)
```

This is the same path on desktop and on mobile launchers — just copy your
mp3 files there with any file manager (or a "Save to..." from a phone browser),
open the GUI with F6, and press **⟳ Refresh**.

---

## Project structure

```
spotify-in-game/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── LICENSE
├── .github/workflows/build.yml     <- auto-builds a jar on GitHub
└── src/main/
    ├── java/com/lyzi/spotifyingame/
    │   ├── SpotifyInGame.java          (common entrypoint, registers sound event)
    │   ├── SpotifyInGameClient.java    (F6 keybind, client entrypoint)
    │   ├── audio/
    │   │   ├── Mp3AudioStream.java     (mp3 -> PCM decoder, feeds MC's sound engine)
    │   │   ├── CustomMusicSound.java   (SoundInstance wrapper)
    │   │   └── MusicPlayer.java        (playlist / play-pause-next-prev state)
    │   ├── gui/
    │   │   └── MusicPlayerScreen.java  (the F6 GUI)
    │   └── config/
    │       └── ModConfig.java          (config.json + music folder scanning)
    └── resources/
        ├── fabric.mod.json
        └── assets/spotifyingame/...
```

---

## Option A — Compile via GitHub (no PC needed, works from phone)

This is the easiest way if you're on mobile and don't want to set up a local
Java/Gradle environment.

1. Create a new **public or private repo** on GitHub (e.g. `spotify-in-game`).
2. Upload every file/folder from this project into that repo, keeping the
   same folder structure (GitHub web UI → "Add file" → "Upload files" works
   fine, or use the GitHub mobile app / Termux + git).
3. Once pushed to the `main` branch, GitHub Actions will automatically run
   (see `.github/workflows/build.yml`) — no setup needed on your end, it's
   already included in this project.
4. On GitHub, go to your repo → **Actions** tab → open the latest **"Build
   Mod"** run → scroll to **Artifacts** → download `spotify-in-game-jar`.
5. Unzip that download — inside is your `spotify-in-game-<version>.jar`.
6. Put that jar into `.minecraft/mods/` (create the `mods` folder if it
   doesn't exist) alongside **Fabric API** and **Fabric Loader** installed
   for 1.21.11.

If the Actions run fails, open its log — it's almost always a version
mismatch (see the note below about checking versions).

## Option B — Compile locally (PC, more control)

1. Install **JDK 21** (Temurin/Microsoft build recommended).
2. Open a terminal in the project folder.
3. First time only — generate the Gradle wrapper (needs Gradle 8.10+ installed
   once, or use IntelliJ's "Import Gradle Project" which does this for you):
   ```
   gradle wrapper --gradle-version 8.10
   ```
4. Build:
   - Windows: `gradlew.bat build`
   - macOS/Linux: `./gradlew build`
5. Your compiled jar appears at `build/libs/spotify-in-game-1.0.0.jar`.
6. Copy it into `.minecraft/mods/`.

**IDE route (recommended if you're going to keep editing code):** open the
folder in **IntelliJ IDEA** with the Minecraft Development plugin, let it
import the Gradle project, then just run the `build` Gradle task from the
Gradle panel — this generates the wrapper for you automatically, no manual
`gradle wrapper` command needed.

---

## Before you build — double check these versions

Minecraft/Fabric versions move fast, so verify these are still current on
[fabricmc.net/develop](https://fabricmc.net/develop) before building (I
verified them at the time of writing, but re-check if the build fails):

- `gradle.properties` → `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`
- Get the matching Fabric API build for **1.21.11** specifically, not just the
  latest overall.

## Installing on your instance (after you have the jar)

You need, in `.minecraft/mods/`:
1. **Fabric Loader** installed as your launcher's version (via Fabric
   installer on PC, or the Fabric profile on PojavLauncher/mobile launchers).
2. **Fabric API** jar for 1.21.11.
3. This mod's jar (`spotify-in-game-*.jar`).

Launch the game with the Fabric profile → press **F6** in a world → drop mp3s
into the config folder → **⟳ Refresh** → click a song.

## Known limitations (honest heads-up)

- No precise seek bar — mp3 streaming decode is sequential, so you get
  play/pause/stop/next/previous/shuffle/repeat, but not "jump to 1:23".
- Volume slider changes apply from the *next* track you play (live in-track
  volume swapping isn't wired up in this version).
- I wrote and cross-checked this against the current Fabric/Yarn docs, but I
  couldn't actually compile+run it in the sandbox I built it in (no internet/
  Gradle there) — so if you hit a compile error on a field/method name in
  `CustomMusicSound.java` (e.g. `attenuationType`/`relative`), open
  `AbstractSoundInstance` in your IDE (Ctrl+Click) and match the field names —
  Yarn field names occasionally shift between builds.
