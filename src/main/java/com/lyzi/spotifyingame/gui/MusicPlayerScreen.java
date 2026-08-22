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
 * The F6 GUI. Only ButtonWidget/SliderWidget are used for interaction (no
 * mouseClicked/mouseScrolled/keyPressed overrides) so this stays stable across
 * Minecraft versions. All visuals are plain fills/gradients/text — cheap to draw,
 * no textures or per-frame allocations of note, so there's no meaningful FPS cost.
 */
public class MusicPlayerScreen extends Screen {

	private static final int COLOR_BG_DIM        = 0xC0000000;
	private static final int COLOR_PANEL         = 0xFF181818;
	private static final int COLOR_PANEL_HEADER  = 0xFF0C0C0C;
	private static final int COLOR_ROW_BG        = 0xFF101010;
	private static final int COLOR_ACCENT        = 0xFF1DB954;
	private static final int COLOR_ACCENT_SOFT   = 0xFF14803B;
	private static final int COLOR_TEXT          = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM      = 0xFF9B9B9B;
	private static final int COLOR_BORDER        = 0xFF2E2E2E;

	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 280;
	private static final int ROW_HEIGHT = 22;
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

		int controlsY = panelY + PANEL_HEIGHT - 50;
		int cx = panelX + 16;
		int btnH = 20;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> {
					MusicPlayer.get().previous();
					this.clearAndInit();
				})
				.dimensions(cx, controlsY, 50, btnH).build());
		cx += 54;

		playPauseButton = this.addDrawableChild(ButtonWidget.builder(playPauseLabel(), b -> {
			MusicPlayer.get().togglePlayPause();
			b.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 60, btnH).build());
		cx += 64;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> {
					MusicPlayer.get().next();
					this.clearAndInit();
				})
				.dimensions(cx, controlsY, 50, btnH).build());
		cx += 54;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> {
			MusicPlayer.get().stop();
			playPauseButton.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 50, btnH).build());

		int rightRowY = controlsY - 24;
		int rx = panelX + 16;
		this.addDrawableChild(ButtonWidget.builder(shuffleLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.shuffle = !cfg.shuffle;
			cfg.save();
			b.setMessage(shuffleLabel());
		}).dimensions(rx, rightRowY, 100, btnH).build());
		rx += 104;

		this.addDrawableChild(ButtonWidget.builder(repeatLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.repeat = !cfg.repeat;
			cfg.save();
			b.setMessage(repeatLabel());
		}).dimensions(rx, rightRowY, 100, btnH).build());
		rx += 104;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh List"), b -> {
			MusicPlayer.get().refresh();
			page = 0;
			this.clearAndInit();
		}).dimensions(rx, rightRowY, 100, btnH).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
				.dimensions(panelX + PANEL_WIDTH - 90, panelY + PANEL_HEIGHT + 8, 90, 20).build());

		this.addDrawableChild(new VolumeSlider(panelX + 16, panelY + PANEL_HEIGHT - 22,
				PANEL_WIDTH - 32, 16, ModConfig.get().volume));
	}

	private void buildTrackButtons() {
		trackButtons.clear();
		List<Path> tracks = MusicPlayer.get().getTracks();
		int totalPages = Math.max(1, (int) Math.ceil(tracks.size() / (double) ROWS_PER_PAGE));
		if (page >= totalPages) page = totalPages - 1;
		if (page < 0) page = 0;

		int listY = panelY + 56;
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
			String status = isCurrent ? (MusicPlayer.get().isPlaying() ? "[Playing] " : "[Paused] ") : "";

			ButtonWidget button = ButtonWidget.builder(Text.literal(status + name), b -> {
				MusicPlayer.get().playIndex(index);
				if (playPauseButton != null) playPauseButton.setMessage(playPauseLabel());
				this.clearAndInit();
			}).dimensions(listX, rowY, listW, ROW_HEIGHT - 2).build();

			this.addDrawableChild(button);
			trackButtons.add(button);
		}

		if (totalPages > 1) {
			int navY = listY + ROWS_PER_PAGE * ROW_HEIGHT + 4;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("< Page " + (page + 1) + "/" + totalPages), b -> {
				page = Math.max(0, page - 1);
				this.clearAndInit();
			}).dimensions(listX, navY, 130, 16).build());

			this.addDrawableChild(ButtonWidget.builder(Text.literal("Next Page >"), b -> {
				page = Math.min(totalPages - 1, page + 1);
				this.clearAndInit();
			}).dimensions(listX + listW - 90, navY, 90, 16).build());
		}
	}

	private Text playPauseLabel() {
		return Text.literal(MusicPlayer.get().isPlaying() ? "Pause" : "Play");
	}

	private Text shuffleLabel() {
		return Text.literal("Shuffle: " + (ModConfig.get().shuffle ? "On" : "Off"));
	}

	private Text repeatLabel() {
		return Text.literal("Repeat: " + (ModConfig.get().repeat ? "On" : "Off"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, COLOR_BG_DIM);

		context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, COLOR_BORDER);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);

		// Header with a slow animated hue-shift on the accent underline — cheap: 2 fills.
		context.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + 30, COLOR_PANEL_HEADER, 0xFF161616);
		context.drawText(this.textRenderer, Text.literal("Spotify in Game"), panelX + 12, panelY + 11, COLOR_ACCENT, true);
		int sweep = (int) ((System.currentTimeMillis() / 12) % (PANEL_WIDTH + 60)) - 60;
		context.fill(panelX + Math.max(0, sweep), panelY + 29, panelX + Math.min(PANEL_WIDTH, sweep + 60), panelY + 31, COLOR_ACCENT);

		String current = MusicPlayer.get().getCurrentTrackName();
		String nowPlayingText = current != null
				? (MusicPlayer.get().isPlaying() ? "Now Playing: " + current : "Paused: " + current)
				: "No track selected — pick one from the list below";
		context.drawText(this.textRenderer, Text.literal(nowPlayingText), panelX + 12, panelY + 38, COLOR_TEXT, false);

		if (MusicPlayer.get().isPlaying()) {
			drawEqualizer(context, panelX + PANEL_WIDTH - 34, panelY + 36);
		}

		int listX = panelX + 8;
		int listY = panelY + 56;
		int listW = PANEL_WIDTH - 16;
		int listH = ROWS_PER_PAGE * ROW_HEIGHT + 6;
		context.fill(listX, listY, listX + listW, listY + listH, COLOR_ROW_BG);

		if (MusicPlayer.get().getTracks().isEmpty()) {
			context.drawText(this.textRenderer, Text.literal("No .mp3 files found."), listX + 8, listY + 10, COLOR_TEXT_DIM, false);
			context.drawText(this.textRenderer, Text.literal("Drop songs into the music folder, then press Refresh List."), listX + 8, listY + 22, COLOR_TEXT_DIM, false);
		}

		highlightCurrentRow(context);

		super.render(context, mouseX, mouseY, delta);

		context.drawText(this.textRenderer, Text.literal("Music folder: config/spotifyingame/music"),
				panelX + 12, panelY + PANEL_HEIGHT + 34, COLOR_TEXT_DIM, false);
	}

	/** Small animated bar-equalizer next to "Now Playing" — 4 cheap fills, no allocations beyond primitives. */
	private void drawEqualizer(DrawContext context, int x, int y) {
		long t = System.currentTimeMillis();
		for (int bar = 0; bar < 4; bar++) {
			double phase = (t / 130.0) + bar * 1.4;
			int height = 3 + (int) (4 + 4 * Math.sin(phase));
			int bx = x + bar * 6;
			context.fill(bx, y + (10 - height), bx + 4, y + 10, COLOR_ACCENT);
		}
	}

	private void highlightCurrentRow(DrawContext context) {
		int current = MusicPlayer.get().getCurrentIndex();
		if (current < 0) return;

		int i = page * ROWS_PER_PAGE;
		long t = System.currentTimeMillis();
		int pulse = (int) (40 + 30 * Math.sin(t / 260.0));
		int glowColor = (pulse << 24) | 0x1DB954;

		for (ButtonWidget b : trackButtons) {
			if (i == current) {
				context.fill(b.getX() - 2, b.getY() - 2, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() + 2, glowColor);
				context.fill(b.getX() - 3, b.getY() - 1, b.getX() - 1, b.getY() + b.getHeight() + 1, COLOR_ACCENT_SOFT);
			}
			i++;
		}
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
