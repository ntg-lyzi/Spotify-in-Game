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
 * those signatures have changed across recent Minecraft versions. Using only
 * ButtonWidget/SliderWidget (handled internally by Screen) keeps this stable.
 */
public class MusicPlayerScreen extends Screen {

	private static final int COLOR_BG_DIM        = 0xC0000000;
	private static final int COLOR_PANEL         = 0xFF181818;
	private static final int COLOR_PANEL_HEADER  = 0xFF0B0B0B;
	private static final int COLOR_ROW_ALT       = 0xFF1E1E1E;
	private static final int COLOR_ACCENT        = 0xFF1DB954;
	private static final int COLOR_ACCENT_DIM    = 0xFF14803B;
	private static final int COLOR_TEXT_DIM      = 0xFF9B9B9B;
	private static final int COLOR_BORDER        = 0xFF2E2E2E;

	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 268;
	private static final int ROW_HEIGHT = 20;
	private static final int ROWS_PER_PAGE = 6;
	private static final int CORNER = 4;

	private int panelX, panelY;
	private int page = 0;
	private long openedAt;

	private ButtonWidget playPauseButton;
	private final List<ButtonWidget> trackButtons = new ArrayList<>();

	public MusicPlayerScreen() {
		super(Text.literal("Spotify in Game"));
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_WIDTH) / 2;
		panelY = (this.height - PANEL_HEIGHT) / 2;
		openedAt = System.currentTimeMillis();

		buildTrackButtons();

		int controlsY = panelY + PANEL_HEIGHT - 46;
		int cx = panelX + 16;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), b -> MusicPlayer.get().previous())
				.dimensions(cx, controlsY, 26, 20).build());
		cx += 30;

		playPauseButton = this.addDrawableChild(ButtonWidget.builder(playPauseLabel(), b -> {
			MusicPlayer.get().togglePlayPause();
			b.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 26, 20).build());
		cx += 30;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), b -> MusicPlayer.get().next())
				.dimensions(cx, controlsY, 26, 20).build());
		cx += 30;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> {
			MusicPlayer.get().stop();
			playPauseButton.setMessage(playPauseLabel());
		}).dimensions(cx, controlsY, 26, 20).build());
		cx += 34;

		this.addDrawableChild(ButtonWidget.builder(shuffleLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.shuffle = !cfg.shuffle;
			cfg.save();
			b.setMessage(shuffleLabel());
		}).dimensions(cx, controlsY, 68, 20).build());
		cx += 72;

		this.addDrawableChild(ButtonWidget.builder(repeatLabel(), b -> {
			ModConfig cfg = ModConfig.get();
			cfg.repeat = !cfg.repeat;
			cfg.save();
			b.setMessage(repeatLabel());
		}).dimensions(cx, controlsY, 68, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("⟳"), b -> {
			MusicPlayer.get().refresh();
			page = 0;
			this.clearAndInit();
		}).dimensions(panelX + PANEL_WIDTH - 30, panelY + 6, 20, 16).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
				.dimensions(panelX + PANEL_WIDTH - 90, panelY + PANEL_HEIGHT + 8, 90, 20).build());

		this.addDrawableChild(new VolumeSlider(panelX + 16, panelY + PANEL_HEIGHT - 20,
				PANEL_WIDTH - 32, 14, ModConfig.get().volume));
	}

	private void buildTrackButtons() {
		trackButtons.clear();
		List<Path> tracks = MusicPlayer.get().getTracks();
		int totalPages = Math.max(1, (int) Math.ceil(tracks.size() / (double) ROWS_PER_PAGE));
		if (page >= totalPages) page = totalPages - 1;
		if (page < 0) page = 0;

		int listY = panelY + 52;
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
			String prefix = isCurrent ? (MusicPlayer.get().isPlaying() ? "▶ " : "⏸ ") : "    ";

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
			this.addDrawableChild(ButtonWidget.builder(Text.literal("◀ " + (page + 1) + "/" + totalPages), b -> {
				page = Math.max(0, page - 1);
				this.clearAndInit();
			}).dimensions(listX, navY, 100, 16).build());

			this.addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> {
				page = Math.min(totalPages - 1, page + 1);
				this.clearAndInit();
			}).dimensions(listX + listW - 40, navY, 40, 16).build());
		}
	}

	private Text playPauseLabel() {
		return Text.literal(MusicPlayer.get().isPlaying() ? "⏸" : "▶");
	}

	private Text shuffleLabel() {
		boolean on = ModConfig.get().shuffle;
		return Text.literal("🔀 " + (on ? "ON" : "OFF"));
	}

	private Text repeatLabel() {
		boolean on = ModConfig.get().repeat;
		return Text.literal("🔁 " + (on ? "ON" : "OFF"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, COLOR_BG_DIM);

		drawPanelFrame(context);
		drawHeader(context);
		drawNowPlayingRow(context);
		highlightCurrentRow(context);

		super.render(context, mouseX, mouseY, delta);

		context.drawText(this.textRenderer, Text.literal("Music folder: config/spotifyingame/music"),
				panelX + 12, panelY + PANEL_HEIGHT + 34, COLOR_TEXT_DIM, false);
	}

	private void drawPanelFrame(DrawContext context) {
		context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, COLOR_BORDER);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);
		// Soft corner clips for a rounded feel
		context.fill(panelX, panelY, panelX + CORNER, panelY + CORNER, COLOR_BG_DIM);
		context.fill(panelX + PANEL_WIDTH - CORNER, panelY, panelX + PANEL_WIDTH, panelY + CORNER, COLOR_BG_DIM);
	}

	private void drawHeader(DrawContext context) {
		context.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + 30, COLOR_PANEL_HEADER, 0xFF141414);
		context.fill(panelX, panelY + 30, panelX + PANEL_WIDTH, panelY + 31, COLOR_BORDER);
		context.drawText(this.textRenderer, Text.literal("♫ Spotify in Game"), panelX + 12, panelY + 11, COLOR_ACCENT, true);
	}

	private void drawNowPlayingRow(DrawContext context) {
		String current = MusicPlayer.get().getCurrentTrackName();
		String nowPlayingText = current != null
				? (MusicPlayer.get().isPlaying() ? "Now playing: " + current : "Paused: " + current)
				: "No track selected";
		context.drawText(this.textRenderer, Text.literal(nowPlayingText), panelX + 12, panelY + 38, COLOR_TEXT_DIM, false);

		if (MusicPlayer.get().isPlaying()) {
			drawEqualizer(context, panelX + PANEL_WIDTH - 40, panelY + 36);
		}

		int listX = panelX + 8;
		int listY = panelY + 52;
		int listW = PANEL_WIDTH - 16;
		int listH = PANEL_HEIGHT - 52 - 52;
		context.fill(listX, listY, listX + listW, listY + listH, 0xFF101010);

		if (MusicPlayer.get().getTracks().isEmpty()) {
			context.drawText(this.textRenderer, Text.literal("No .mp3 files found."), listX + 8, listY + 10, COLOR_TEXT_DIM, false);
			context.drawText(this.textRenderer, Text.literal("Drop songs into the music folder and hit ⟳."), listX + 8, listY + 22, COLOR_TEXT_DIM, false);
		}
	}

	/** A tiny animated bar-equalizer icon next to "Now playing" — purely cosmetic, safe: it only touches render(). */
	private void drawEqualizer(DrawContext context, int x, int y) {
		long t = System.currentTimeMillis();
		for (int bar = 0; bar < 4; bar++) {
			double phase = (t / 140.0) + bar * 1.3;
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
		int pulse = (int) (30 + 25 * Math.sin(t / 260.0));
		int glowColor = (pulse << 24) | (0x1D << 16) | (0xB9 << 8) | 0x54;

		for (ButtonWidget b : trackButtons) {
			if (i == current) {
				context.fill(b.getX() - 2, b.getY() - 1, b.getX() + b.getWidth() + 2, b.getY() + b.getHeight() + 1, glowColor);
				context.fill(b.getX() - 2, b.getY() - 1, b.getX() - 1, b.getY() + b.getHeight() + 1, COLOR_ACCENT_DIM);
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
			super(x, y, width, height, Text.literal("🔊 " + (int) (initialVolume * 100) + "%"), initialVolume);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Text.literal("🔊 " + (int) (this.value * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			MusicPlayer.get().setVolume((float) this.value);
		}
	}
}
