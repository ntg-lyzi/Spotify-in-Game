package com.lyzi.spotifyingame.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import com.lyzi.spotifyingame.SpotifyInGame;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Config lives at: .minecraft/config/spotifyingame/config.json
 * Your MP3 files go in: .minecraft/config/spotifyingame/music/
 *
 * This is the same folder on PC and on mobile Java launchers (PojavLauncher etc.),
 * so you just drop mp3 files in there with any file manager and hit "Refresh" (or reopen
 * the GUI) in game.
 */
public class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(SpotifyInGame.MOD_ID);
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
	public static final Path MUSIC_DIR = CONFIG_DIR.resolve("music");

	public float volume = 1.0F;
	public boolean shuffle = false;
	public boolean repeat = true;
	public String lastPlayed = "";

	private static ModConfig instance;

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ModConfig load() {
		try {
			Files.createDirectories(MUSIC_DIR);
			if (Files.exists(CONFIG_FILE)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
					ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
					if (cfg != null) {
						return cfg;
					}
				}
			}
		} catch (IOException e) {
			SpotifyInGame.LOGGER.error("Failed to load Spotify in Game config, using defaults", e);
		}
		ModConfig fresh = new ModConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_DIR);
			try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			SpotifyInGame.LOGGER.error("Failed to save Spotify in Game config", e);
		}
	}

	/** Scans the music folder for .mp3 files, sorted alphabetically. */
	public static List<Path> scanTracks() {
		List<Path> tracks = new ArrayList<>();
		try {
			Files.createDirectories(MUSIC_DIR);
			try (Stream<Path> stream = Files.list(MUSIC_DIR)) {
				stream.filter(p -> p.toString().toLowerCase().endsWith(".mp3"))
						.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
						.forEach(tracks::add);
			}
		} catch (IOException e) {
			SpotifyInGame.LOGGER.error("Failed to scan music folder", e);
		}
		return tracks;
	}
}
