Press **F6** in-game to open a music player GUI. Drop your `.mp3` files into the
config folder, hit refresh, and play them straight from Minecraft's own sound
engine — works on PC Java **and** mobile Java launchers (PojavLauncher etc.).

## Why this works on mobile too ?

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

