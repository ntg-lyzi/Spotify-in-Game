package com.lyzi.spotifyingame.gui;

import com.lyzi.spotifyingame.audio.MusicPlayer;
import com.lyzi.spotifyingame.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The F6 GUI. Deliberately avoids overriding mouseClicked/mouseScrolled/keyPressed —
 * those signatures have changed across recent Minecraft versions (Click/KeyInput
 * records). Using only ButtonWidget/SliderWidget keeps this screen stable across
 * Minecraft versions since those widgets are handled internally by Screen itself.
 */
public class MusicPlayerScreen extends Screen {

	private static final int COLOR_BG_DIM        = 0xC0000000;
	private static final int COLOR_PANEL         = 0xFF181818;
	private static final int COLOR_PANEL_HEADER  = 0xFF000000;
	private static final int COLOR_ROW           = 0xFF232323;
	private static final int COLOR_ROW_ALT       = 0xFF1E1E1E;
	private static final int COLOR_ROW_PLAYING   = 0xFF1E3B29;
	private static final int COLOR_ACCENT        = 0xFF1DB954;
	private static final int COLOR_TEXT_DIM      = 0xFF9B9B9B;
	private static final int COLOR_BORDER        = 0xFF2E2E2E;

	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 260;
	private static final int ROW_HEIGHT = 20;
	private static final int ROWS_PER_PAGE = 6;

	private int panelX, panelY;
	private int page = 0;

	private ButtonWidget playPauseButton;
	private final List<ButtonWidget> trackButtons = new ArrayList<>();

	public MusicPlayerScreen() {
		super(Text.literal("Spotify in Game"));
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_WIDTH) / 2;
		panelY = (this.height - PANEL_HEIGHT) / 2;

		buildTrackButtons();

		int controlsY = panelY + PANEL_HEIGHT - 46;
		int cx = panelX + 16;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("<<"), b -> MusicPlayer.get().previous())
				.dimensions(cx, controlsY, 28, 20).build());
		cx += 32;

		playPauseButton = this.addDrawableChild(ButtonWidget.builder(playPauseLabel(), b -> {
			MusicPlayer.get().togglePlayPause();
			b.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 28, 20).build());
		cx += 32;

		this.addDrawableChild(ButtonWidget.builder(Text.literal(">>"), b -> MusicPlayer.get().next())
				.dimensions(cx, controlsY, 28, 20).build());
		cx += 32;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> {
			MusicPlayer.get().stop();
			playPauseButton.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 44, 20).build());
		cx += 48;

		this.addDrawableChild(ButtonWidget.builder(shuffleLabel(), b -> {
		ModConfig cfg = ModConfig.get();
			cfg.shuffle = !cfg.shuffle;
			cfg.save();
			b.setMessage(shuffleLabel());
		}).dimensions(cx, controlsY, 60, 20).build());
		cx += 64;

		this.addDrawableChild(ButtonWidget.builder(repeatLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.repeat = !cfg.repeat;
			cfg.save();
			b.setMessage(repeatLabel());
		}).dimensions(cx, controlsY, 60, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> {
			MusicPlayer.get().refresh();
			page = 0;
			this.clearAndInit();
		}).dimensions(panelX + PANEL_WIDTH - 90, panelY + 8, 74, 16).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
				.dimensions(panelX + PANEL_WIDTH - 90, panelY + PANEL_HEIGHT + 6, 90, 20).build());

		this.addDrawableChild(new VolumeSlider(panelX + 16, panelY + PANEL_HEIGHT - 20,
				PANEL_WIDTH - 32, 14, ModConfig.get().volume));
	}

	private void buildTrackButtons() {
		trackButtons.clear();
		List<Path> tracks = MusicPlayer.get().getTracks();
		int totalPages = Math.max(1, (int) Math.ceil(tracks.size() / (double) ROWS_PER_PAGE));
		if (page >= totalPages) page = totalPages - 1;
		if (page < 0) page = 0;

		int listY = panelY + 48;
		int listX = panelX + 8;
		int listW = PANEL_WIDTH - 16;

		int start = page * ROWS_PER_PAGE;
		int end = Math.min(tracks.size(), start + ROWS_PER_PAGE);

		for (int i = start; i < end; i++) {
			final int index = i;
			int rowY = listY + (i - start) * ROW_HEIGHT;
			String name = tracks.get(i).getFileName().toString();
			if (name.toLowerCase().endsWith(".mp3")) {
				name = name.substring(0, name.length() - 4);
			}
			boolean isCurrent = i == MusicPlayer.get().getCurrentIndex();
			String prefix = isCurrent ? (MusicPlayer.get().isPlaying() ? "> " : "|| ") : "";

			ButtonWidget button = ButtonWidget.builder(Text.literal(prefix + name), b -> {
				MusicPlayer.get().playIndex(index);
				if (playPauseButton != null) playPauseButton.setMessage(playPauseLabel());
				this.clearAndInit();
			}).dimensions(listX, rowY, listW, ROW_HEIGHT - 2).build();

			this.addDrawableChild(button);
			trackButtons.add(button);
		}

		if (totalPages > 1) {
			int navY = listY + ROWS_PER_PAGE * ROW_HEIGHT + 4;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("< Page"), b -> {
				page = Math.max(0, page - 1);
				this.clearAndInit();
			}).dimensions(listX, navY, 80, 16).build());

			this.addDrawableChild(ButtonWidget.builder(Text.literal("Page >"), b -> {
				page = Math.min(totalPages - 1, page + 1);
				this.clearAndInit();
			}).dimensions(listX + listW - 80, navY, 80, 16).build());
		}
	}

	private Text playPauseLabel() {
		return Text.literal(MusicPlayer.get().isPlaying() ? "||" : ">");
	}

	private Text shuffleLabel() {
		boolean on = ModConfig.get().shuffle;
		return Text.literal(on ? "Shuffle:ON" : "Shuffle:OFF");
	}

	private Text repeatLabel() {
		boolean on = ModConfig.get().repeat;
		return Text.literal(on ? "Repeat:ON" : "Repeat:OFF");
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, COLOR_BG_DIM);

		context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, COLOR_BORDER);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);

		context.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + 28, COLOR_PANEL_HEADER, 0xFF111111);
		context.drawText(this.textRenderer, Text.literal("Spotify in Game"), panelX + 12, panelY + 10, COLOR_ACCENT, true);

		String current = MusicPlayer.get().getCurrentTrackName();
		String nowPlayingText = current != null
				? (MusicPlayer.get().isPlaying() ? "Now playing: " + current : "Paused: " + current)
				: "No track selected";
		context.drawText(this.textRenderer, Text.literal(nowPlayingText), panelX + 12, panelY + 34, COLOR_TEXT_DIM, false);

		int listY = panelY + 48;
		int listX = panelX + 8;
		int listW = PANEL_WIDTH - 16;
		int listH = PANEL_HEIGHT - 48 - 52;
		context.fill(listX, listY, listX + listW, listY + listH, 0xFF101010);

		if (MusicPlayer.get().getTracks().isEmpty()) {
			context.drawText(this.textRenderer, Text.literal("No .mp3 files found."), listX + 8, listY + 8, COLOR_TEXT_DIM, false);
			context.drawText(this.textRenderer, Text.literal("Drop songs into the music folder and hit Refresh."), listX + 8, listY + 20, COLOR_TEXT_DIM, false);
		} else {
			int i = page * ROWS_PER_PAGE;
			for (ButtonWidget b : trackButtons) {
				boolean isCurrent = i == MusicPlayer.get().getCurrentIndex();
				if (isCurrent) {
					context.fill(b.getX() - 2, b.getY() - 1, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() + 1, COLOR_ROW_PLAYING);
				}
				i++;
			}
		}

		super.render(context, mouseX, mouseY, delta);

		context.drawText(this.textRenderer, Text.literal("Music folder: config/spotifyingame/music"),
				panelX + 12, panelY + PANEL_HEIGHT + 32, COLOR_TEXT_DIM, false);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

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
