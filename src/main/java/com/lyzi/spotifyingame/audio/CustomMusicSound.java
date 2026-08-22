package com.lyzi.spotifyingame.audio;

import com.lyzi.spotifyingame.SpotifyInGame;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class CustomMusicSound extends AbstractSoundInstance implements FabricSoundInstance {

	private final Path mp3File;
	private volatile Mp3AudioStream stream;

	public CustomMusicSound(Path mp3File, float volume) {
		super(SpotifyInGame.MUSIC_SOUND_ID, SoundCategory.MASTER, SoundInstance.createRandom());
		this.mp3File = mp3File;
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
			Mp3AudioStream s = new Mp3AudioStream(in);
			this.stream = s;
			return CompletableFuture.completedFuture(s);
		} catch (IOException e) {
			SpotifyInGame.LOGGER.error("Could not open mp3 file: " + mp3File, e);
			CompletableFuture<AudioStream> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
	}

	/** True once the mp3 has been fully decoded AND every byte handed to the audio engine. */
	public boolean isStreamFinished() {
		return stream != null && stream.finished;
	}

	/** Live-updates playback volume without needing to restart the track. */
	public void setLiveVolume(float newVolume) {
		this.volume = newVolume;
	}
}
