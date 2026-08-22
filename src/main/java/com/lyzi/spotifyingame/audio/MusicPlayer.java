package com.lyzi.spotifyingame.audio;

import com.lyzi.spotifyingame.config.ModConfig;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayer {

	private static final MusicPlayer INSTANCE = new MusicPlayer();

	private List<Path> tracks = new ArrayList<>();
	private int currentIndex = -1;
	private CustomMusicSound currentSound;
	private boolean playing = false;
	private final Random random = new Random();

	private MusicPlayer() {
		refresh();
	}

	public static MusicPlayer get() {
		return INSTANCE;
	}

	public void refresh() {
		this.tracks = ModConfig.scanTracks();
	}

	public List<Path> getTracks() {
		return tracks;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public boolean isPlaying() {
		return playing;
	}

	public String getCurrentTrackName() {
		if (currentIndex < 0 || currentIndex >= tracks.size()) {
			return null;
		}
		String fileName = tracks.get(currentIndex).getFileName().toString();
		return fileName.substring(0, fileName.length() - 4); // strip ".mp3"
	}

	public void playIndex(int index) {
		if (index < 0 || index >= tracks.size()) {
			return;
		}
		stop();
		currentIndex = index;
		ModConfig cfg = ModConfig.get();
		currentSound = new CustomMusicSound(tracks.get(index), cfg.volume);
		MinecraftClient.getInstance().getSoundManager().play(currentSound);
		playing = true;
		cfg.lastPlayed = tracks.get(index).getFileName().toString();
		cfg.save();
	}

	public void togglePlayPause() {
		if (currentSound == null) {
			if (!tracks.isEmpty()) {
				playIndex(0);
			}
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (playing) {
			client.getSoundManager().stop(currentSound);
			playing = false;
		} else {
			client.getSoundManager().play(currentSound);
			playing = true;
		}
	}

	public void stop() {
		if (currentSound != null) {
			MinecraftClient.getInstance().getSoundManager().stop(currentSound);
			currentSound = null;
		}
		playing = false;
	}

	public void next() {
		if (tracks.isEmpty()) return;
		int nextIndex;
		if (ModConfig.get().shuffle) {
			nextIndex = random.nextInt(tracks.size());
		} else {
			nextIndex = (currentIndex + 1) % tracks.size();
		}
		playIndex(nextIndex);
	}

	public void previous() {
		if (tracks.isEmpty()) return;
		int prevIndex = currentIndex <= 0 ? tracks.size() - 1 : currentIndex - 1;
		playIndex(prevIndex);
	}

	public void setVolume(float volume) {
		ModConfig cfg = ModConfig.get();
		cfg.volume = volume;
		cfg.save();
		if (currentSound != null) {
			// Volume changes apply on next track start; live volume updates require
			// re-triggering playback since AbstractSoundInstance volume isn't hot-swappable.
		}
	}

	/** Call once per client tick to auto-advance when a track finishes. */
	public void tick() {
		if (playing && currentSound != null) {
			boolean stillPlaying = MinecraftClient.getInstance().getSoundManager().isPlaying(currentSound);
			if (!stillPlaying) {
				if (ModConfig.get().repeat) {
					next();
				} else {
					playing = false;
				}
			}
		}
	}
}
