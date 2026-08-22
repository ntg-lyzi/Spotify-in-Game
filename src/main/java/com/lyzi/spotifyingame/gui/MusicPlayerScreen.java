package com.lyzi.spotifyingame.gui;

import com.lyzi.spotifyingame.audio.MusicPlayer;
import com.lyzi.spotifyingame.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

public class MusicPlayerScreen extends Screen {

	// ---- Spotify-inspired dark palette ----
	private static final int COLOR_BG_DIM        = 0xC0000000;
	private static final int COLOR_PANEL         = 0xFF181818;
	private static final int COLOR_PANEL_HEADER  = 0xFF000000;
	private static final int COLOR_ROW           = 0xFF232323;
	private static final int COLOR_ROW_ALT       = 0xFF1E1E1E;
	private static final int COLOR_ROW_HOVER     = 0xFF2A2A2A;
	private static final int COLOR_ROW_PLAYING   = 0xFF1E3B29;
	private static final int COLOR_ACCENT        = 0xFF1DB954;
	private static final int COLOR_TEXT          = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM      = 0xFF9B9B9B;
	private static final int COLOR_BORDER        = 0xFF2E2E2E;

	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 240;
	private static final int ROW_HEIGHT = 18;

	private int panelX, panelY;
	private int scrollOffset = 0;

	private ButtonWidget playPauseButton;
	private ButtonWidget refreshButton;
	private ButtonWidget shuffleButton;
	private ButtonWidget repeatButton;

	public MusicPlayerScreen() {
		super(Text.literal("Spotify in Game"));
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_WIDTH) / 2;
		panelY = (this.height - PANEL_HEIGHT) / 2;

		int controlsY = panelY + PANEL_HEIGHT - 46;
		int cx = panelX + 16;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), b -> MusicPlayer.get().previous())
				.dimensions(cx, controlsY, 24, 20).build());
		cx += 28;

		playPauseButton = this.addDrawableChild(ButtonWidget.builder(playPauseLabel(), b -> {
			MusicPlayer.get().togglePlayPause();
			b.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 24, 20).build());
		cx += 28;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), b -> MusicPlayer.get().next())
				.dimensions(cx, controlsY, 24, 20).build());
		cx += 28;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> {
			MusicPlayer.get().stop();
			playPauseButton.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 24, 20).build());
		cx += 32;

		shuffleButton = this.addDrawableChild(ButtonWidget.builder(shuffleLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.shuffle = !cfg.shuffle;
			cfg.save();
			b.setMessage(shuffleLabel());
		}).dimensions(cx, controlsY, 26, 20).build());
		cx += 30;

		repeatButton = this.addDrawableChild(ButtonWidget.builder(repeatLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.repeat = !cfg.repeat;
			cfg.save();
			b.setMessage(repeatLabel());
		}).dimensions(cx, controlsY, 26, 20).build());

		refreshButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("⟳ Refresh"), b -> {
			MusicPlayer.get().refresh();
		}).dimensions(panelX + PANEL_WIDTH - 90, panelY + 8, 74, 16).build());

		this.addDrawableChild(new VolumeSlider(panelX + 16, panelY + PANEL_HEIGHT - 20,
				PANEL_WIDTH - 32, 14, ModConfig.get().volume));
	}

	private Text playPauseLabel() {
		return Text.literal(MusicPlayer.get().isPlaying() ? "⏸" : "▶");
	}

	private Text shuffleLabel() {
		boolean on = ModConfig.get().shuffle;
		return Text.literal("🔀").formatted(on ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GRAY);
	}

	private Text repeatLabel() {
		boolean on = ModConfig.get().repeat;
		return Text.literal("🔁").formatted(on ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GRAY);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Dim the world behind the GUI instead of the default blur, keeps it lightweight.
		context.fill(0, 0, this.width, this.height, COLOR_BG_DIM);

		// Panel + border
		context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, COLOR_BORDER);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);

		// Header bar with subtle gradient
		context.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + 28, COLOR_PANEL_HEADER, 0xFF111111);
		context.drawText(this.textRenderer, Text.literal("🎵 Spotify in Game"), panelX + 12, panelY + 10, COLOR_ACCENT, true);

		// Now playing strip
		String current = MusicPlayer.get().getCurrentTrackName();
		String nowPlayingText = current != null
				? (MusicPlayer.get().isPlaying() ? "Now playing: " + current : "Paused: " + current)
				: "No track selected";
		context.drawText(this.textRenderer, Text.literal(nowPlayingText), panelX + 12, panelY + 34, COLOR_TEXT_DIM, false);

		renderTrackList(context, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);

		context.drawText(this.textRenderer, Text.literal("Music folder: config/spotifyingame/music"),
				panelX + 12, panelY + PANEL_HEIGHT + 6, COLOR_TEXT_DIM, false);
	}

	private void renderTrackList(DrawContext context, int mouseX, int mouseY) {
		int listX = panelX + 8;
		int listY = panelY + 48;
		int listW = PANEL_WIDTH - 16;
		int listH = PANEL_HEIGHT - 48 - 52;

		context.fill(listX, listY, listX + listW, listY + listH, 0xFF101010);

		context.enableScissor(listX, listY, listX + listW, listY + listH);

		List<Path> tracks = MusicPlayer.get().getTracks();
		if (tracks.isEmpty()) {
			context.drawText(this.textRenderer, Text.literal("No .mp3 files found."), listX + 8, listY + 8, COLOR_TEXT_DIM, false);
			context.drawText(this.textRenderer, Text.literal("Drop songs into the music folder and hit Refresh."), listX + 8, listY + 20, COLOR_TEXT_DIM, false);
		} else {
			int rowY = listY - scrollOffset;
			for (int i = 0; i < tracks.size(); i++) {
				int rY = rowY + i * ROW_HEIGHT;
				if (rY + ROW_HEIGHT < listY || rY > listY + listH) {
					continue;
				}

				boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rY && mouseY <= rY + ROW_HEIGHT;
				boolean isCurrent = i == MusicPlayer.get().getCurrentIndex();

				int rowColor = isCurrent ? COLOR_ROW_PLAYING : (hovered ? COLOR_ROW_HOVER : (i % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT));
				context.fill(listX, rY, listX + listW, rY + ROW_HEIGHT, rowColor);

				String name = tracks.get(i).getFileName().toString();
				if (name.toLowerCase().endsWith(".mp3")) {
					name = name.substring(0, name.length() - 4);
				}
				String prefix = isCurrent ? (MusicPlayer.get().isPlaying() ? "▶ " : "⏸ ") : "   ";
				int textColor = isCurrent ? COLOR_ACCENT : COLOR_TEXT;
				context.drawText(this.textRenderer, Text.literal(prefix + name), listX + 6, rY + 5, textColor, false);
			}
		}

		context.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int listX = panelX + 8;
		int listY = panelY + 48;
		int listW = PANEL_WIDTH - 16;
		int listH = PANEL_HEIGHT - 48 - 52;

		if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
			List<Path> tracks = MusicPlayer.get().getTracks();
			int relativeY = (int) (mouseY - listY) + scrollOffset;
			int index = relativeY / ROW_HEIGHT;
			if (index >= 0 && index < tracks.size()) {
				MusicPlayer.get().playIndex(index);
				if (playPauseButton != null) {
					playPauseButton.setMessage(playPauseLabel());
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int listH = PANEL_HEIGHT - 48 - 52;
		int contentH = MusicPlayer.get().getTracks().size() * ROW_HEIGHT;
		int maxScroll = Math.max(0, contentH - listH);

		scrollOffset -= (int) (verticalAmount * ROW_HEIGHT);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_F6) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Small volume slider, 0-100%. */
	private static class VolumeSlider extends SliderWidget {
		VolumeSlider(int x, int y, int width, int height, float initialVolume) {
			super(x, y, width, height, Text.literal("Volume: " + (int) (initialVolume * 100) + "%"), initialVolume);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Text.literal("Volume: " + (int) (this.value * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			MusicPlayer.get().setVolume((float) this.value);
		}
	}
}
