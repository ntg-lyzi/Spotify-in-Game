package com.lyzi.spotifyingame;

import com.lyzi.spotifyingame.audio.MusicPlayer;
import com.lyzi.spotifyingame.gui.MusicPlayerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpotifyInGameClient implements ClientModInitializer {

	private static KeyBinding openPlayerKey;

	@Override
	public void onInitializeClient() {
		KeyBinding.Category category = KeyBinding.Category.create(
				Identifier.of(SpotifyInGame.MOD_ID, "main")
		);

		openPlayerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.spotifyingame.open_player",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openPlayerKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MusicPlayerScreen());
				} else if (client.currentScreen instanceof MusicPlayerScreen) {
					client.setScreen(null);
				}
			}
			MusicPlayer.get().tick();
		});
	}
}
