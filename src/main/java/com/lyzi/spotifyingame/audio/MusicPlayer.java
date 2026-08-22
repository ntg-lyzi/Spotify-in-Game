package com.lyzi.spotifyingame.audio;

import com.lyzi.spotifyingame.config.ModConfig;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayer {

	private static final MusicPlayer INSTANCE = new MusicPlayer();
	private static final long MIN_PLAY_MILLIS = 2500;

	private List<Path> tracks = new ArrayList<>();
	private int currentIndex = -1;
	private CustomMusicSound currentSound;
	private boolean playing = false;
	private long trackStartedAt = 0;
	private long pausedAtMillis = 0;
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
		return fileName.substring(0, fileName.length() - 4);
	}

	/** Starts a track from the beginning (used for picking a track, Next, Prev). */
	public void playIndex(int index) {
		if (index < 0 || index >= tracks.size()) {
			return;
		}
		stop();
		currentIndex = index;
		pausedAtMillis = 0;
		ModConfig cfg = ModConfig.get();
		currentSound = new CustomMusicSound(tracks.get(index), cfg.volume, 0);
		MinecraftClient.getInstance().getSoundManager().play(currentSound);
		playing = true;
		trackStartedAt = System.currentTimeMillis();
		cfg.lastPlayed = tracks.get(index).getFileName().toString();
		cfg.save();
	}

	/** Pauses in place, or resumes from the exact position it was paused at. */
	public void togglePlayPause() {
		if (currentSound == null) {
			if (!tracks.isEmpty()) {
				playIndex(0);
			}
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (playing) {
			pausedAtMillis = currentSound.getElapsedMillis();
			client.getSoundManager().stop(currentSound);
			playing = false;
		} else {
			ModConfig cfg = ModConfig.get();
			currentSound = new CustomMusicSound(tracks.get(currentIndex), cfg.volume, pausedAtMillis);
			client.getSoundManager().play(currentSound);
			playing = true;
			trackStartedAt = System.currentTimeMillis();
		}
	}

	public void stop() {
		if (currentSound != null) {
			MinecraftClient.getInstance().getSoundManager().stop(currentSound);
			currentSound = null;
		}
		playing = false;
		pausedAtMillis = 0;
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
			currentSound.setLiveVolume(volume);
		}
	}


	public long getElapsedMillis() {
		if (currentSound == null) return 0;
		return playing ? currentSound.getElapsedMillis() : pausedAtMillis;
	}

	public void tick() {
		if (playing && currentSound != null) {
			long elapsed = System.currentTimeMillis() - trackStartedAt;
			if (elapsed < MIN_PLAY_MILLIS) {
				return;
			}

			boolean streamDone = currentSound.isStreamFinished();
			boolean stillPlaying = MinecraftClient.getInstance().getSoundManager().isPlaying(currentSound);

			if (streamDone && !stillPlaying) {
				if (ModConfig.get().repeat) {
					next();
				} else {
					playing = false;
					pausedAtMillis = 0;
				}
			}
		}
	}
}
