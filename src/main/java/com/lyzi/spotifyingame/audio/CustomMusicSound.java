package com.lyzi.spotifyingame.audio;

import com.lyzi.spotifyingame.SpotifyInGame;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class CustomMusicSound extends AbstractSoundInstance implements FabricSoundInstance, TickableSoundInstance {

	private final Path mp3File;
	private final long startAtMillis;
	private volatile Mp3AudioStream stream;
	private boolean done = false;

	public CustomMusicSound(Path mp3File, float volume) {
		this(mp3File, volume, 0);
	}

	/** startAtMillis lets playback resume partway through the track instead of from the beginning. */
	public CustomMusicSound(Path mp3File, float volume, long startAtMillis) {
		super(SpotifyInGame.MUSIC_SOUND_ID, SoundCategory.MASTER, SoundInstance.createRandom());
		this.mp3File = mp3File;
		this.startAtMillis = startAtMillis;
		this.volume = volume;
		this.repeat = false;
		this.repeatDelay = 0;
		this.attenuationType = AttenuationType.NONE;
		this.relative = true;
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
		try {
			InputStream in = Files.newInputStream(mp3File);
			Mp3AudioStream s = new Mp3AudioStream(in, mp3File.getFileName().toString(), startAtMillis);
			this.stream = s;
			return CompletableFuture.completedFuture(s);
		} catch (IOException e) {
			SpotifyInGame.LOGGER.error("Could not open mp3 file: " + mp3File, e);
			CompletableFuture<AudioStream> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
	}

	public boolean isStreamFinished() {
		return stream != null && stream.finished;
	}

	/** Current playback position in this track, in milliseconds (includes any resume offset). */
	public long getElapsedMillis() {
		return stream != null ? stream.getElapsedMillis() : startAtMillis;
	}

	public void setLiveVolume(float newVolume) {
		this.volume = newVolume;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public void tick() {
		if (stream != null && stream.finished) {
			done = true;
		}
	}
}
