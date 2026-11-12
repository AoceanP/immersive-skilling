/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.immersiveskilling;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * WHAT: The sidebar panel: what's showing now (with Clear and Pause), a card per skill with a
 * switch for each effect and a Preview button, shared options, and feedback links.
 * WHY: Every setting at a glance, in plain words, with a way to try each effect.
 * PRE: Swing thread only. It reads the config and calls plugin methods that hop to the client
 * thread themselves; it never touches the game directly.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class ImmersiveSkillingPanel extends PluginPanel
{
	private static final String ISSUES = "https://github.com/AoceanP/immersive-skilling/issues/new";
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color SLOT = new Color(22, 22, 22);
	private static final Color TEXT = Color.WHITE;
	private static final Color MUTED = new Color(168, 168, 168);
	private static final Color SUBTLE = new Color(118, 118, 118);
	private static final Color GREEN = new Color(96, 220, 140);
	private static final Color ORANGE = ColorScheme.BRAND_ORANGE;

	private final ImmersiveSkillingPlugin plugin;
	private final ImmersiveSkillingConfig config;
	private final ConfigManager configManager;
	private final List<Runnable> refreshers = new ArrayList<>();

	private final JLabel statusTitle = bold("Nothing showing yet", TEXT);
	private final JLabel statusState = small("", GREEN);
	private final JLabel statusDetail = small("Start skilling, or try a Preview.", SUBTLE);
	private final Bar statusBar = new Bar();
	private final JLabel pauseButton;
	private final RoundPanel statusCard = new RoundPanel(new DynamicGridLayout(0, 1, 0, 4), CARD, 12);

	ImmersiveSkillingPanel(ImmersiveSkillingPlugin plugin, ImmersiveSkillingConfig config, ConfigManager configManager)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel stack = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stack.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Header: title, what it is, and the switch for everything.
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		JPanel titles = new JPanel(new GridLayout(2, 1));
		titles.setOpaque(false);
		titles.add(bold("Immersive Skilling", TEXT));
		titles.add(small("Visual only. Only you can see it.", SUBTLE));
		header.add(titles, BorderLayout.CENTER);
		header.add(toggle("enabled", config::enabled), BorderLayout.EAST);
		stack.add(header);

		// What's showing now.
		statusCard.setBorder(BorderFactory.createEmptyBorder(7, 8, 8, 8));
		JPanel statusTop = new JPanel(new BorderLayout(6, 0));
		statusTop.setOpaque(false);
		statusTop.add(statusTitle, BorderLayout.CENTER);
		statusState.setHorizontalAlignment(SwingConstants.RIGHT);
		statusTop.add(statusState, BorderLayout.EAST);
		statusCard.add(statusTop);
		statusCard.add(statusDetail);
		// In a holder, so the hidden bar takes no space (the grid keeps a row for hidden children).
		JPanel barHolder = new JPanel(new BorderLayout());
		barHolder.setOpaque(false);
		barHolder.add(statusBar, BorderLayout.CENTER);
		statusCard.add(barHolder);
		JPanel statusButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		statusButtons.setOpaque(false);
		statusButtons.add(button("Clear props", "Remove every pile and prop now", plugin::clearFromPanel));
		pauseButton = button("Pause", "Stop new effects for now", () -> plugin.setPaused(!plugin.isPaused()));
		statusButtons.add(pauseButton);
		statusCard.add(statusButtons);
		stack.add(statusCard);

		stack.add(skillCard("Woodcutting", "Log pile and flying wood chips", Activity.WOODCUTTING,
			row("Log pile", "logPile", config::logPile),
			row("Wood chips", "woodChips", config::woodChips),
			segmented("Pile size", "pileSize", ImmersiveSkillingConfig.PileSize.values(), config::pileSize)));
		stack.add(skillCard("Fishing", "A basket that fills as you fish", Activity.FISHING,
			row("Catch basket", "catchBasket", config::catchBasket),
			row("Splashes", "splashes", config::splashes)));
		stack.add(skillCard("Mining", "Ore chunks, dust and sparks", Activity.MINING,
			row("Ore chunks", "oreChunks", config::oreChunks),
			row("Dust and sparks", "dustSparks", config::dustSparks)));
		stack.add(metalCard());
		stack.add(skillCard("Fletching", "The finished item and shavings", Activity.FLETCHING,
			row("Finished item", "fletchedItem", config::fletchedItem),
			row("Shavings", "shavings", config::shavings)));
		stack.add(skillCard("Firemaking", "The log lands on the fire", Activity.FIREMAKING,
			row("Log into the fire", "tossedLog", config::tossedLog),
			row("Embers", "fireEmbers", config::fireEmbers)));
		stack.add(skillCard("Runecraft", "Runes burst out of the altar", Activity.RUNECRAFT,
			row("Rune burst", "runeBurst", config::runeBurst),
			row("Glints", "runeGlints", config::runeGlints)));
		stack.add(skillCard("Thieving", "Loot flies to you, with glints", Activity.THIEVING,
			row("Loot flight", "lootFlight", config::lootFlight),
			row("Glints", "lootGlints", config::lootGlints)));
		stack.add(skillCard("Herblore", "Potions bubble, herbs leaf", Activity.HERBLORE,
			row("Finished item", "herbItem", config::herbItem),
			row("Bubbles and leaves", "bubbles", config::bubbles)));
		stack.add(skillCard("Crafting", "Gems glint, the rest gives dust", Activity.CRAFTING,
			row("Finished item", "craftedItem", config::craftedItem),
			row("Glints and dust", "craftGlints", config::craftGlints)));
		stack.add(skillCard("Prayer", "Bones on the altar, rising light", Activity.PRAYER,
			row("Bone offering", "offeredBone", config::offeredBone),
			row("Rising light", "prayerLight", config::prayerLight)));
		stack.add(skillCard("Magic", "Alching: flash and coins", Activity.ALCHING,
			row("Green flash", "alchFlash", config::alchFlash),
			row("Coin shower", "alchCoins", config::alchCoins)));
		stack.add(skillCard("Farming", "Harvests pile up beside you", Activity.FARMING,
			row("Harvest pile", "harvestPile", config::harvestPile),
			row("Soil", "soilPuffs", config::soilPuffs)));
		stack.add(skillCard("Hunter", "Your catch flies to you", Activity.HUNTER,
			row("Catch flight", "huntCatch", config::huntCatch),
			row("Puffs", "huntPuffs", config::huntPuffs)));
		stack.add(skillCard("Agility", "Dust where you land, and XP", Activity.AGILITY,
			row("Landing dust", "landingDust", config::landingDust),
			row("XP pops", "agilityPops", config::agilityPops)));
		stack.add(skillCard("Construction", "The plank flies into place", Activity.CONSTRUCTION,
			row("Plank flight", "plankFlight", config::plankFlight),
			row("Dust and sparks", "buildDust", config::buildDust)));

		RoundPanel general = card();
		general.add(bold("General", TEXT));
		general.add(row("Item pops", "itemPops", config::itemPops));
		general.add(segmented("Effect amount", "amount", ImmersiveSkillingConfig.Amount.values(), config::amount));
		general.add(segmented("Clear piles when", "clearWhen", ImmersiveSkillingConfig.ClearWhen.values(), config::clearWhen));

		stack.add(general);

		JPanel feedback = new JPanel(new GridLayout(1, 2, 4, 0));
		feedback.setOpaque(false);
		feedback.add(button("Report bug", "Opens the plugin's GitHub page to report a bug",
			() -> LinkBrowser.browse(bugLink())));
		feedback.add(button("Suggest", "Opens the plugin's GitHub page to suggest a feature",
			() -> LinkBrowser.browse(ISSUES + "?labels=enhancement")));
		stack.add(feedback);

		// Seventeen cards don't fit any screen, so the panel scrolls; it always fills the sidebar's width.
		ScrollStack holder = new ScrollStack();
		holder.add(stack, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		refresh();
	}

	/**
	 * WHAT: Redraws every switch and selector from the config, and the status card from the plugin.
	 * WHY: Settings can change from RuneLite's own config page too; the panel follows them.
	 * PRE: Swing thread.
	 * POST: The panel shows the current state.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void refresh()
	{
		for (Runnable r : refreshers)
		{
			r.run();
		}
		String activity = plugin.getStatus();
		boolean paused = plugin.isPaused();
		boolean on = config.enabled();
		pauseButton.setText(paused ? "Resume" : "Pause");
		if (!on)
		{
			statusTitle.setText("Effects are off");
			statusState.setText("");
			statusDetail.setText("Turn them on with the switch above.");
			statusBar.setVisible(false);
			statusCard.outline(null);
		}
		else if (activity.isEmpty())
		{
			String hint = plugin.getHint();
			statusTitle.setText("Nothing showing yet");
			statusState.setText(paused ? "paused" : "");
			statusState.setForeground(ORANGE);
			// The tip is a sentence, so it wraps to the card's width instead of running off it.
			statusDetail.setText(hint.isEmpty() ? "Start skilling, or try a Preview."
				: "<html><body style='width:130px'>" + hint + "</body></html>");
			statusBar.setVisible(false);
			statusCard.outline(null);
		}
		else
		{
			statusTitle.setText(activity);
			statusState.setText(paused ? "paused" : "● showing");
			statusState.setForeground(paused ? ORANGE : GREEN);
			int count = plugin.getStatusCount();
			int cap = plugin.getStatusCap();
			statusDetail.setText(cap > 0 ? "Pile: " + count + " / " + cap : "Effects playing as you work");
			statusBar.setVisible(cap > 0);
			statusBar.setFill(cap > 0 ? count / (double) cap : 0);
			statusCard.outline(paused ? ORANGE : GREEN);
		}
		revalidate();
		repaint();
	}

	/**
	 * WHAT: The link for a new bug report, with the version and a few questions filled in.
	 * WHY: A report that says the version and where it happened can be fixed straight away.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the GitHub new-issue link
	 * THROWS: Nothing.
	 */
	static String bugLink()
	{
		String body = "Plugin version: " + ImmersiveSkillingPlugin.VERSION + "\n\n"
			+ "What happened:\n\n"
			+ "Which skill and where (for example fishing from a boat):\n";
		return ISSUES + "?labels=bug&body=" + encode(body);
	}

	/**
	 * WHAT: Escapes text for a link's query string.
	 * WHY: The body has spaces, new lines and colons; java.net's encoder is off limits here.
	 * PRE: text is not null.
	 * POST: Nothing changes.
	 * RETURN: the escaped text
	 * THROWS: Nothing.
	 */
	static String encode(String text)
	{
		StringBuilder out = new StringBuilder();
		for (char c : text.toCharArray())
		{
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-_.~".indexOf(c) >= 0)
			{
				out.append(c);
			}
			else
			{
				out.append(String.format("%%%02X", (int) c));
			}
		}
		return out.toString();
	}

	// ---- Building blocks ---------------------------------------------------------------------

	private RoundPanel skillCard(String title, String what, Activity activity, JComponent... rows)
	{
		JComponent[] body = Arrays.copyOf(rows, rows.length + 1);
		body[rows.length] = button("Preview at your feet", "Plays the effect where you stand, as if you'd just made one",
			() -> plugin.preview(activity));
		return fold(title, what, body);
	}

	private RoundPanel metalCard()
	{
		JPanel previews = new JPanel(new GridLayout(1, 2, 4, 0));
		previews.setOpaque(false);
		previews.add(button("Preview smelt", "Plays the furnace effect where you stand", () -> plugin.preview(Activity.SMELTING)));
		previews.add(button("Preview smith", "Plays the anvil effect where you stand", () -> plugin.preview(Activity.SMITHING)));
		return fold("Smithing", "Red-hot bars and items cool",
			row("Hot metal", "hotMetal", config::hotMetal),
			row("Sparks and embers", "forgeSparks", config::forgeSparks),
			previews);
	}

	/**
	 * WHAT: A skill card that folds: the title with how many of its effects are on, a line on
	 * what it shows, and Show or Hide to open its switches and Preview button.
	 * WHY: Seventeen open cards made the panel over two screens tall. Folded, every skill fits at a
	 * glance and nothing is lost: the switches are one click away.
	 * PRE: Swing thread. body holds the card's rows and buttons.
	 * POST: The card is built folded; a refresher keeps its count up to date.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	private RoundPanel fold(String title, String what, JComponent... body)
	{
		return foldWith(title, what, null, body);
	}

	/**
	 * WHAT: A folding card whose header shows a custom status instead of how many switches are on.
	 * WHY: Lets a card show its own status in place of the usual count.
	 * PRE: Swing thread. status may be null for the usual "2 of 2 on".
	 * POST: The card is built folded.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	private RoundPanel foldWith(String title, String what, Supplier<String> status, JComponent... body)
	{
		RoundPanel c = card();
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(bold(title, TEXT), BorderLayout.CENTER);
		JLabel count = small("", SUBTLE);
		head.add(count, BorderLayout.EAST);
		c.add(head);
		JPanel sub = new JPanel(new BorderLayout(6, 0));
		sub.setOpaque(false);
		sub.add(small(what, SUBTLE), BorderLayout.CENTER);
		JLabel showHide = small("Show", ORANGE);
		sub.add(showHide, BorderLayout.EAST);
		c.add(sub);

		// In a holder, so the folded body takes no space (the grid keeps a row for hidden children).
		JPanel inner = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		inner.setOpaque(false);
		inner.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		List<Toggle> toggles = new ArrayList<>();
		for (JComponent b : body)
		{
			inner.add(b);
			collectToggles(b, toggles);
		}
		inner.setVisible(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(inner, BorderLayout.CENTER);
		c.add(holder);

		MouseAdapter flip = new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				inner.setVisible(!inner.isVisible());
				showHide.setText(inner.isVisible() ? "Hide" : "Show");
				revalidate();
				repaint();
			}
		};
		for (JComponent clickable : new JComponent[]{head, sub})
		{
			clickable.addMouseListener(flip);
			clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			clickable.setToolTipText("Show or hide this skill's switches");
		}
		refreshers.add(() ->
		{
			int on = 0;
			for (Toggle t : toggles)
			{
				on += t.isOn() ? 1 : 0;
			}
			if (status != null)
			{
				count.setText(status.get());
				count.setForeground(ORANGE);
				return;
			}
			count.setText(on + " of " + toggles.size() + " on");
			count.setForeground(on == toggles.size() ? GREEN : on == 0 ? SUBTLE : MUTED);
		});
		return c;
	}

	private static void collectToggles(Component c, List<Toggle> into)
	{
		if (c instanceof Toggle)
		{
			into.add((Toggle) c);
		}
		else if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collectToggles(child, into);
			}
		}
	}

	private static RoundPanel card()
	{
		RoundPanel c = new RoundPanel(new DynamicGridLayout(0, 1, 0, 4), CARD, 12);
		c.setBorder(BorderFactory.createEmptyBorder(7, 8, 8, 8));
		return c;
	}

	private JPanel row(String label, String key, BooleanSupplier value)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setOpaque(false);
		r.add(small(label, MUTED), BorderLayout.CENTER);
		r.add(toggle(key, value), BorderLayout.EAST);
		return r;
	}

	private JComponent toggle(String key, BooleanSupplier value)
	{
		Toggle t = new Toggle();
		t.setToolTipText("Turn on or off");
		t.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				configManager.setConfiguration(ImmersiveSkillingConfig.GROUP, key, !value.getAsBoolean());
				refresh();
			}
		});
		refreshers.add(() -> t.setOn(value.getAsBoolean()));
		return t;
	}

	private <E extends Enum<E>> JPanel segmented(String label, String key, E[] options, Supplier<E> value)
	{
		JPanel wrap = new JPanel(new BorderLayout(0, 3));
		wrap.setOpaque(false);
		wrap.add(small(label, MUTED), BorderLayout.NORTH);
		RoundPanel bar = new RoundPanel(new GridLayout(1, options.length, 2, 0), SLOT, 10);
		bar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		for (E option : options)
		{
			RoundLabel cell = new RoundLabel(option.toString(), 8);
			cell.setFont(FontManager.getRunescapeSmallFont());
			cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			cell.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			cell.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					configManager.setConfiguration(ImmersiveSkillingConfig.GROUP, key, option.name());
					refresh();
				}
			});
			refreshers.add(() ->
			{
				boolean selected = value.get() == option;
				cell.setBackground(selected ? new Color(88, 62, 28) : SLOT);
				cell.setForeground(selected ? ORANGE : MUTED);
			});
			bar.add(cell);
		}
		wrap.add(bar, BorderLayout.CENTER);
		return wrap;
	}

	private static JLabel button(String text, String tooltip, Runnable onClick)
	{
		RoundLabel b = new RoundLabel(text, 8);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setForeground(TEXT);
		b.setBackground(SLOT);
		b.setToolTipText(tooltip);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				b.setBackground(new Color(44, 44, 44));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setBackground(SLOT);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (b.contains(e.getPoint()) && e.getButton() == MouseEvent.BUTTON1)
				{
					onClick.run();
				}
			}
		});
		return b;
	}

	private static JLabel bold(String text, Color colour)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(colour);
		return l;
	}

	private static JLabel small(String text, Color colour)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(colour);
		return l;
	}

	/**
	 * WHAT: A panel painted as a rounded rectangle, with an optional outline.
	 * WHY: The rounded card look.
	 * PRE: Swing thread.
	 * POST: Paints itself.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	/**
	 * WHAT: The panel inside the scroll pane, as wide as the sidebar and as tall as its cards.
	 * WHY: A plain panel in a scroll pane keeps its own width and can push past the sidebar.
	 * PRE: Swing thread.
	 * POST: Not applicable.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class ScrollStack extends JPanel implements Scrollable
	{
		ScrollStack()
		{
			super(new BorderLayout());
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return visible.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	static class RoundPanel extends JPanel
	{
		private final int radius;
		private Color outline;

		RoundPanel(LayoutManager layout, Color background, int radius)
		{
			super(layout);
			this.radius = radius;
			setBackground(background);
			setOpaque(false);
		}

		void outline(Color colour)
		{
			outline = colour;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			if (outline != null)
			{
				g2.setStroke(new BasicStroke(1.5f));
				g2.setColor(outline);
				g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, radius, radius));
			}
			g2.dispose();
		}
	}

	/**
	 * WHAT: A label on a rounded background, for buttons and selector cells.
	 * WHY: Matches the rounded cards.
	 * PRE: Swing thread.
	 * POST: Paints itself.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class RoundLabel extends JLabel
	{
		private final int radius;

		RoundLabel(String text, int radius)
		{
			super(text, SwingConstants.CENTER);
			this.radius = radius;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * WHAT: An on/off switch: green with the knob on the right when on, grey when off.
	 * WHY: Clearer than a checkbox at a glance.
	 * PRE: Swing thread.
	 * POST: Paints itself.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class Toggle extends JComponent
	{
		private boolean on;

		Toggle()
		{
			setPreferredSize(new Dimension(28, 16));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		void setOn(boolean on)
		{
			this.on = on;
			repaint();
		}

		boolean isOn()
		{
			return on;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int h = 14;
			int y = (getHeight() - h) / 2;
			int w = getWidth();
			g2.setColor(on ? GREEN : new Color(70, 70, 70));
			g2.fill(new RoundRectangle2D.Float(0, y, w, h, h, h));
			g2.setColor(Color.WHITE);
			g2.fill(new Ellipse2D.Float(on ? w - h + 2 : 2, y + 2, h - 4, h - 4));
			g2.dispose();
		}
	}

	/**
	 * WHAT: A thin progress bar for the pile.
	 * WHY: Shows how full the pile is at a glance.
	 * PRE: Swing thread.
	 * POST: Paints itself.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class Bar extends JComponent
	{
		private double fill;

		Bar()
		{
			setPreferredSize(new Dimension(0, 6));
		}

		void setFill(double fill)
		{
			this.fill = Motion.clamp(fill);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(SLOT);
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
			g2.setColor(new Color(160, 110, 60));
			g2.fill(new RoundRectangle2D.Float(0, 0, (float) (getWidth() * fill), getHeight(), 6, 6));
			g2.dispose();
		}
	}
}
