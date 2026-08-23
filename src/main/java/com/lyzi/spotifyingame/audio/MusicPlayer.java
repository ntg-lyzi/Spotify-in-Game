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

	// Wall-clock based position tracking. bufferedAtMillis is how much of the
	// track had elapsed (in real time) when the current playback segment began;
	// segmentStartedAt is the wall-clock time that segment began. This tracks
	// actual audible position, NOT how far ahead the engine has buffered —
	// using the buffered-bytes count instead caused resume to jump ahead of
	// where the user actually heard it pause.
	private long trackElapsedBaseMillis = 0;
	private long segmentStartedAt = 0;

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

	/** Actual audible elapsed position in the current track, in milliseconds. */
	public long getElapsedMillis() {
		if (currentSound == null) return 0;
		if (playing) {
			return trackElapsedBaseMillis + (System.currentTimeMillis() - segmentStartedAt);
		}
		return trackElapsedBaseMillis;
	}

	/** Starts a track from the beginning (used for picking a track, Next, Prev). */
	public void playIndex(int index) {
		if (index < 0 || index >= tracks.size()) {
			return;
		}
		stop();
		currentIndex = index;
		trackElapsedBaseMillis = 0;
		ModConfig cfg = ModConfig.get();
		currentSound = new CustomMusicSound(tracks.get(index), cfg.volume, 0);
		MinecraftClient.getInstance().getSoundManager().play(currentSound);
		playing = true;
		segmentStartedAt = System.currentTimeMillis();
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
			trackElapsedBaseMillis += System.currentTimeMillis() - segmentStartedAt;
			client.getSoundManager().stop(currentSound);
			playing = false;
		} else {
			ModConfig cfg = ModConfig.get();
			currentSound = new CustomMusicSound(tracks.get(currentIndex), cfg.volume, trackElapsedBaseMillis);
			client.getSoundManager().play(currentSound);
			playing = true;
			segmentStartedAt = System.currentTimeMillis();
		}
	}

	public void stop() {
		if (currentSound != null) {
			MinecraftClient.getInstance().getSoundManager().stop(currentSound);
			currentSound = null;
		}
		playing = false;
		trackElapsedBaseMillis = 0;
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

	public void tick() {
		if (playing && currentSound != null) {
			long elapsed = System.currentTimeMillis() - segmentStartedAt;
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
					trackElapsedBaseMillis = 0;
				}
			}
		}
	}
}
