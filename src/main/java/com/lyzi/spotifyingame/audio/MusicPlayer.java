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

	public long getElapsedMillis() {
		if (currentSound == null) return 0;
		if (playing) {
			return trackElapsedBaseMillis + (System.currentTimeMillis() - segmentStartedAt);
		}
		return trackElapsedBaseMillis;
	}

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

	/**
	 * Call once per client tick. Handles three cases when playback is expected
	 * to be running but the engine says it isn't:
	 *  1. The mp3 genuinely finished decoding -> advance/stop per repeat setting.
	 *  2. It stopped for some OTHER reason (e.g. the server sent a resource/config
	 *     reload, which makes vanilla clear all active sounds) -> automatically
	 *     resume from the same position instead of leaving music silently dead.
	 */
	public void tick() {
		if (playing && currentSound != null) {
			long elapsed = System.currentTimeMillis() - segmentStartedAt;
			if (elapsed < MIN_PLAY_MILLIS) {
				return;
			}

			boolean streamDone = currentSound.isStreamFinished();
			boolean stillPlaying = MinecraftClient.getInstance().getSoundManager().isPlaying(currentSound);

			if (!stillPlaying) {
				if (streamDone) {
					if (ModConfig.get().repeat) {
						next();
					} else {
						playing = false;
						trackElapsedBaseMillis = 0;
					}
				} else {
					// Stopped unexpectedly (not by us, not because it finished) —
					// most likely the server triggered a reconfigure/resource
					// reload and vanilla cleared all sounds. Resume automatically.
					resumeAfterUnexpectedStop();
				}
			}
		}
	}

	private void resumeAfterUnexpectedStop() {
		trackElapsedBaseMillis += System.currentTimeMillis() - segmentStartedAt;
		ModConfig cfg = ModConfig.get();
		currentSound = new CustomMusicSound(tracks.get(currentIndex), cfg.volume, trackElapsedBaseMillis);
		MinecraftClient.getInstance().getSoundManager().play(currentSound);
		segmentStartedAt = System.currentTimeMillis();
	}
}
