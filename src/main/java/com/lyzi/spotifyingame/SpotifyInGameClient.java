package com.lyzi.spotifyingame;

import com.lyzi.spotifyingame.audio.MusicPlayer;
import com.lyzi.spotifyingame.gui.MusicPlayerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SpotifyInGameClient implements ClientModInitializer {

	private static KeyBinding openPlayerKey;

	@Override
	public void onInitializeClient() {
		openPlayerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.spotifyingame.open_player",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				"key.categories.spotifyingame"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openPlayerKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MusicPlayerScreen());
				}
			}
			MusicPlayer.get().tick();
		});
	}
}
