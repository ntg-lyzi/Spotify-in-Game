package com.lyzi.spotifyingame;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyInGame implements ModInitializer {

	public static final String MOD_ID = "spotifyingame";
	public static final Logger LOGGER = LoggerFactory.getLogger("Spotify in Game");

	// The dummy streamed sound event used for all music playback.
	public static final Identifier MUSIC_SOUND_ID = Identifier.of(MOD_ID, "music");
	public static SoundEvent MUSIC_SOUND_EVENT;

	@Override
	public void onInitialize() {
		MUSIC_SOUND_EVENT = Registry.register(
				Registries.SOUND_EVENT,
				MUSIC_SOUND_ID,
				SoundEvent.of(MUSIC_SOUND_ID)
		);

		LOGGER.info("Spotify in Game loaded — press F6 in-game to open the player.");
	}
}
