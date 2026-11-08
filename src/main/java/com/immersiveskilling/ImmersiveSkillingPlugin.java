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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * WHAT: Immersive Skilling: purely visual effects while you skill. Logs pile up beside you, your
 * catch flies into a basket, ore bounces out of the rock, bars come out of the furnace red-hot,
 * smithed items cool on the anvil, and the item you fletch appears in front of you.
 * WHY: Makes skilling feel more alive. Everything is client-side and seen only by you; nothing
 * changes how the game plays and nothing shows information you don't already have.
 * PRE: RuneLite loads it as a plugin. Game events and rendering run on the client thread.
 * POST: Effects are cleared when the plugin stops.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
@PluginDescriptor(
	name = "Immersive Skilling",
	description = "Visual only: log piles, catch baskets, ore chunks, red-hot bars and items, sparks and pops while you skill",
	tags = {"immersive", "visual", "cosmetic", "woodcutting", "fishing", "mining", "smithing", "smelting", "fletching", "skilling"}
)
public class ImmersiveSkillingPlugin extends Plugin
{
	/** XP and the item it gave can arrive a tick apart; they still count as one action. */
	/** This release, for bug reports. A test keeps it equal to runelite-plugin.properties. */
	static final String VERSION = "1.1.0";
	static final int MATCH_TICKS = 1;
	/** Unmatched items are forgotten after this many ticks (a bank withdrawal never matches). */
	static final int PENDING_TICKS = 2;
	/** Piles clear when you're this far away (in tiles) and "Walk away" is chosen. */
	static final int WALK_AWAY_TILES = 3;
	/** Piles always clear when you're this far away (in tiles). */
	static final int FAR_TILES = 20;
	/** Ambient sparks and chips keep going this long (seconds) after the last item. */
	static final double AMBIENT_SECONDS = 6;
	private static final int TILE = 128;
	/** Skills whose items can go straight into a log basket, fish barrel or bag. */
	private static final List<Skill> BAG_SKILLS = Arrays.asList(Skill.WOODCUTTING, Skill.FISHING, Skill.MINING);

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ImmersiveSkillingConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private EffectsOverlay overlay;
	@Inject
	private ClientToolbar clientToolbar;

	private Props props;
	private ImmersiveSkillingPanel panel;
	private NavigationButton navButton;

	// Inventory and XP last seen, to spot what was gained this tick.
	private Map<Integer, Integer> lastInventory;
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> xpTick = new EnumMap<>(Skill.class);
	private final List<Gain> pending = new ArrayList<>();
	// The XP drop each skill's last item was matched to, and the last one checked for a bag.
	private final Map<Skill, Integer> matchedXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> checkedXp = new EnumMap<>(Skill.class);
	// The last item each gathering skill gave you, for when the next one goes into a bag instead.
	private final Map<Skill, Integer> lastItem = new EnumMap<>(Skill.class);

	// The current pile (or basket) beside you.
	private Activity pileActivity;
	private double pileX;
	private double pileY;
	private int pilePlane;
	private int pileView = -1;
	private int pileCount;

	// The last activity, for ambient effects while you keep working.
	private Activity lastActivity;
	private double lastActivityTime;
	private double targetX;
	private double targetY;
	private LocalPoint lastStandPoint;

	private volatile boolean paused;
	private volatile String status = "";
	private volatile String hint = "";
	private volatile int statusCount;
	private volatile int statusCap;

	/** An item that arrived in the inventory, with what left it in the same change. */
	static final class Gain
	{
		final int itemId;
		final int quantity;
		final int tick;
		final List<Integer> lost;

		Gain(int itemId, int quantity, int tick, List<Integer> lost)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
			this.lost = lost;
		}
	}

	@Provides
	ImmersiveSkillingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ImmersiveSkillingConfig.class);
	}

	@Override
	protected void startUp()
	{
		props = new Props(client);
		overlay.clear();
		overlayManager.add(overlay);
		panel = new ImmersiveSkillingPanel(this, config, configManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Immersive Skilling")
			.icon(icon)
			.priority(9)
			.panel(panel)
			.build();
		if (config.showPanel())
		{
			clientToolbar.addNavigation(navButton);
		}
		resetState();
		lastItem.clear();
		lastItem.put(Skill.WOODCUTTING, config.lastWoodcutting());
		lastItem.put(Skill.FISHING, config.lastFishing());
		lastItem.put(Skill.MINING, config.lastMining());
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		Props toClear = props;
		clientThread.invokeLater(() ->
		{
			if (toClear != null)
			{
				toClear.shutDown();
			}
			overlay.clear();
		});
		pending.clear();
	}

	private void resetState()
	{
		lastInventory = null;
		lastXp.clear();
		xpTick.clear();
		matchedXp.clear();
		checkedXp.clear();
		pending.clear();
		pileActivity = null;
		pileCount = 0;
		lastActivity = null;
		status = "";
		hint = "";
		statusCount = 0;
		statusCap = 0;
	}

	// ---- Events ----------------------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			return;
		}
		// Logging out, hopping or loading a new area: local positions are no longer valid.
		clearAll();
		if (state == GameState.LOADING)
		{
			// Only the area changed, not your XP or inventory. Forgetting them here made the first
			// item after every map load (crossing a region, sailing on) play nothing.
			return;
		}
		resetState();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer before = lastXp.put(skill, xp);
		if (before != null && xp > before)
		{
			xpTick.put(skill, client.getTickCount());
			match();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		Map<Integer, Integer> before = lastInventory;
		lastInventory = now;
		if (before == null)
		{
			return;
		}
		List<Integer> lost = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : before.entrySet())
		{
			if (now.getOrDefault(e.getKey(), 0) < e.getValue())
			{
				lost.add(e.getKey());
			}
		}
		int tick = client.getTickCount();
		for (Map.Entry<Integer, Integer> e : now.entrySet())
		{
			int gained = e.getValue() - before.getOrDefault(e.getKey(), 0);
			if (gained > 0)
			{
				pending.add(new Gain(e.getKey(), gained, tick, lost));
			}
		}
		match();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		pending.removeIf(g -> tick - g.tick > PENDING_TICKS);
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return;
		}
		WorldView wv = viewOf(player);
		overlay.setView(wv.getId(), wv.getPlane());
		LocalPoint at = player.getLocalLocation();
		if (pileActivity != null)
		{
			double dist = Math.hypot(at.getX() - pileX, at.getY() - pileY) / TILE;
			boolean walkedAway = config.clearWhen() == ImmersiveSkillingConfig.ClearWhen.WALK_AWAY && dist > WALK_AWAY_TILES;
			// Boarding or leaving a boat moves you to another worldview, so the pile can't follow.
			if (walkedAway || dist > FAR_TILES || wv.getPlane() != pilePlane || wv.getId() != pileView)
			{
				clearPile();
			}
		}
		bagFallback(player, tick);
		ambient(player, tick);
	}

	/**
	 * WHAT: Plays the last item again when a gathering skill gave XP but nothing arrived.
	 * WHY: With a log basket, fish barrel or bag open, the item goes straight in, so there's no
	 * inventory change to match. Without this those players saw no effects at all.
	 * PRE: Client thread; called every tick, after the item for an XP drop would have arrived.
	 * POST: May play one effect per gathering skill.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void bagFallback(Player player, int tick)
	{
		for (Skill skill : BAG_SKILLS)
		{
			Integer xp = xpTick.get(skill);
			if (xp == null || tick - xp <= MATCH_TICKS || xp.equals(checkedXp.get(skill)))
			{
				continue;
			}
			checkedXp.put(skill, xp);
			Integer itemId = lastItem.get(skill);
			if (xp.equals(matchedXp.get(skill)) || player.getAnimation() == -1)
			{
				continue;
			}
			if (itemId == null || itemId <= 0)
			{
				// Nothing to replay yet: say why in the panel rather than showing nothing at all.
				if (carriesBag(skill))
				{
					setHint(bagHint(skill));
				}
				continue;
			}
			Activity activity = skill == Skill.WOODCUTTING ? Activity.WOODCUTTING
				: skill == Skill.FISHING ? Activity.FISHING : Activity.MINING;
			play(activity, itemId, client.getItemDefinition(itemId).getName(), Collections.emptyList(), null, 1);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (props != null)
		{
			props.update(seconds());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && config.clearWhen() != ImmersiveSkillingConfig.ClearWhen.LOGOUT)
		{
			clearPile();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ImmersiveSkillingConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("showPanel".equals(event.getKey()) && navButton != null)
		{
			if (config.showPanel())
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
		}
		if (!config.enabled() || "logPile".equals(event.getKey()) || "catchBasket".equals(event.getKey())
			|| "oreChunks".equals(event.getKey()) || "pileSize".equals(event.getKey()))
		{
			clientThread.invokeLater(this::clearPile);
		}
		if (!config.enabled())
		{
			clientThread.invokeLater(this::clearAll);
		}
		refreshPanel();
	}

	// ---- Matching XP to items ---------------------------------------------------------------

	/**
	 * WHAT: Pairs each new item with the skill that gave XP in the same tick (or one apart),
	 * and plays the effect.
	 * WHY: An item alone could be a bank withdrawal; XP alone could be from anything. Together
	 * they say exactly what you just did.
	 * PRE: Client thread.
	 * POST: Matched items are played and removed from the pending list.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void match()
	{
		if (pending.isEmpty() || xpTick.isEmpty())
		{
			return;
		}
		Iterator<Gain> it = pending.iterator();
		while (it.hasNext())
		{
			Gain gain = it.next();
			Skill skill = skillNear(gain.tick);
			if (skill == null)
			{
				continue;
			}
			String name = client.getItemDefinition(gain.itemId).getName();
			LocalPoint furnace = null;
			LocalPoint anvil = null;
			if (skill == Skill.SMITHING)
			{
				furnace = findObject("furnace");
				anvil = findObject("anvil");
			}
			Activity activity = Activity.classify(skill, name, furnace != null, anvil != null);
			if (activity == null)
			{
				continue;
			}
			it.remove();
			matchedXp.put(skill, xpTick.get(skill));
			if (BAG_SKILLS.contains(skill))
			{
				rememberItem(skill, gain.itemId);
			}
			play(activity, gain.itemId, name, gain.lost, station(activity, furnace, anvil), gain.quantity);
		}
	}

	/**
	 * WHAT: Where an effect comes from: the furnace or the anvil.
	 * WHY: Bars come out of the furnace you use; items take shape on the anvil.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: the place, or null to use the tile in front of you
	 * THROWS: Nothing.
	 */
	private LocalPoint station(Activity activity, LocalPoint furnace, LocalPoint anvil)
	{
		switch (activity)
		{
			case SMELTING:
				return furnace;
			case SMITHING:
				return anvil;
			default:
				return null;
		}
	}

	/**
	 * WHAT: Remembers the last item a gathering skill gave you, in the settings.
	 * WHY: So a basket or barrel gets effects even right after logging in.
	 * PRE: Client thread.
	 * POST: The item is saved when it changed.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void rememberItem(Skill skill, int itemId)
	{
		Integer before = lastItem.put(skill, itemId);
		if (before == null || before != itemId)
		{
			String key = skill == Skill.WOODCUTTING ? "lastWoodcutting" : skill == Skill.FISHING ? "lastFishing" : "lastMining";
			configManager.setConfiguration(ImmersiveSkillingConfig.GROUP, key, itemId);
		}
	}

	private Skill skillNear(int tick)
	{
		Skill best = null;
		int bestGap = Integer.MAX_VALUE;
		for (Map.Entry<Skill, Integer> e : xpTick.entrySet())
		{
			int gap = Math.abs(e.getValue() - tick);
			if (gap <= MATCH_TICKS && gap < bestGap && isShown(e.getKey()))
			{
				best = e.getKey();
				bestGap = gap;
			}
		}
		return best;
	}

	static boolean isShown(Skill skill)
	{
		return skill == Skill.WOODCUTTING || skill == Skill.FISHING || skill == Skill.MINING || skill == Skill.SMITHING
			|| skill == Skill.FLETCHING;
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container == null)
		{
			return counts;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * WHAT: The nearest object within 3 tiles whose name contains one of the given words, e.g. "anvil".
	 * WHY: Hot bars appear at the furnace you're using and items on the anvil you're using.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: its location, or null if there isn't one nearby
	 * THROWS: Nothing.
	 */
	private LocalPoint findObject(String... words)
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return null;
		}
		WorldView wv = viewOf(player);
		Scene scene = wv.getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = wv.getPlane();
		int px = player.getLocalLocation().getSceneX();
		int py = player.getLocalLocation().getSceneY();
		LocalPoint best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int x = px - 3; x <= px + 3; x++)
		{
			for (int y = py - 3; y <= py + 3; y++)
			{
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length || tiles[plane][x][y] == null)
				{
					continue;
				}
				for (GameObject object : tiles[plane][x][y].getGameObjects())
				{
					if (object == null)
					{
						continue;
					}
					ObjectComposition def = client.getObjectDefinition(object.getId());
					if (def != null && def.getImpostorIds() != null)
					{
						def = def.getImpostor();
					}
					String name = def == null ? null : def.getName();
					if (name != null && containsAny(name.toLowerCase(java.util.Locale.ROOT), words))
					{
						int dist = Math.abs(x - px) + Math.abs(y - py);
						if (dist < bestDist)
						{
							bestDist = dist;
							best = object.getLocalLocation();
						}
					}
				}
			}
		}
		return best;
	}

	/**
	 * WHAT: The worldview you're standing on: the main world, or your Sailing boat.
	 * WHY: On a boat your position is on the boat's own worldview. Effects placed in the main
	 * world from that position landed somewhere out at sea, so boat fishing showed nothing.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: the player's worldview, or the main one if the player has none
	 * THROWS: Nothing.
	 */
	private WorldView viewOf(Player player)
	{
		WorldView wv = player == null ? null : player.getWorldView();
		return wv != null ? wv : client.getTopLevelWorldView();
	}

	private static boolean containsAny(String name, String[] words)
	{
		for (String word : words)
		{
			if (name.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	// ---- Effects ----------------------------------------------------------------------------

	/**
	 * WHAT: Plays the effect for one item made by an activity.
	 * WHY: The heart of the plugin: decides which props fly where and which particles burst.
	 * PRE: Client thread. station is the furnace or anvil, or null.
	 * quantity is how many arrived, for the pop.
	 * POST: Props and particles are added; the panel status is updated.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void play(Activity activity, int itemId, String itemName, List<Integer> lost, LocalPoint station, int quantity)
	{
		if (!config.enabled() || paused || props == null)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null)
		{
			return;
		}
		WorldView wv = viewOf(player);
		int plane = wv.getPlane();
		overlay.setView(wv.getId(), plane);
		props.setView(wv.getId());
		LocalPoint at = player.getLocalLocation();
		double angle = player.getCurrentOrientation() * Math.PI * 2 / 2048;
		double fx = -Math.sin(angle);
		double fy = -Math.cos(angle);
		double tx = at.getX() + fx * TILE;
		double ty = at.getY() + fy * TILE;
		double now = seconds();
		double amount = config.amount().getScale();
		Particles particles = overlay.getParticles();

		setHint("");
		lastActivity = activity;
		lastActivityTime = now;
		targetX = station != null ? station.getX() : tx;
		targetY = station != null ? station.getY() : ty;
		lastStandPoint = at;
		// Where a gathered resource really comes from: the trunk, the rock face, the fishing spot,
		// the patch. {x, y} on the side facing you, then the object's middle.
		double[] src = source(activity, at, tx, ty);
		if (activity.isGathering())
		{
			targetX = src[0];
			targetY = src[1];
		}
		String popText = popText(activity, quantity, itemName);

		switch (activity)
		{
			case WOODCUTTING:
				if (config.woodChips())
				{
					// Chips fly off the trunk where your axe bites; leaves fall from the canopy.
					particles.burst(Particles.Kind.CHIP, src[0], src[1], 70, 10, amount);
					particles.burst(Particles.Kind.FALLING_LEAF, src[2], src[3], 230, 3, amount);
				}
				if (config.logPile())
				{
					// The log drops from the trunk and tumbles onto the pile.
					addToPile(activity, itemId, at, fx, fy, plane, src[0], src[1], 90, 60, now);
				}
				break;
			case FISHING:
				if (config.splashes())
				{
					// The catch leaps out of the fishing spot itself, with a splash and rings.
					particles.burst(Particles.Kind.DROPLET, src[0], src[1], 0, 12, amount);
					particles.single(Particles.Kind.RIPPLE, src[0], src[1], 0, 0xDCF0FF, 70, 1.0);
					particles.single(Particles.Kind.RIPPLE, src[0], src[1], 0, 0xDCF0FF, 120, 1.4);
				}
				if (config.catchBasket())
				{
					addToPile(activity, itemId, at, fx, fy, plane, src[0], src[1], 10, 130, now);
				}
				break;
			case MINING:
				if (config.dustSparks())
				{
					// The rock face takes the hit: dust, sparks and chips fly from it.
					particles.burst(Particles.Kind.DUST, src[0], src[1], 50, 6, amount);
					particles.burst(Particles.Kind.SPARK, src[0], src[1], 60, 10, amount);
					particles.burst(Particles.Kind.STONE, src[0], src[1], 55, 6, amount);
				}
				if (config.oreChunks())
				{
					// The ore breaks off the face and bounces onto the heap.
					addToPile(activity, itemId, at, fx, fy, plane, src[0], src[1], 45, 60, now);
				}
				break;
			case SMELTING:
				smelt(itemId, lost, station, at, plane, now, amount);
				break;
			case SMITHING:
			{
				// The bars go from your pack onto the anvil, then the item takes shape on it.
				double d = ingredients(lost, at, station != null ? station.getX() : tx, station != null ? station.getY() : ty,
					station != null ? 75 : 45, plane, now);
				smith(itemId, station, tx, ty, plane, now + d, amount);
			}
				break;
			default:
				fletch(itemId, at, fx, fy, plane,
					now + ingredients(lost, at, at.getX() + fx * 80, at.getY() + fy * 80, 60, plane, now), amount);
				break;
		}
		if (config.itemPops())
		{
			double popX = pileActivity == activity ? pileX : targetX;
			double popY = pileActivity == activity ? pileY : targetY;
			overlay.addPop(new EffectsOverlay.Pop(itemId, popText, popX, popY, plane, now));
		}
		updateStatus(activity);
	}

	private void addToPile(Activity activity, int itemId, LocalPoint at, double fx, double fy, int plane,
		double fromX, double fromY, double fromZ, double peak, double now)
	{
		int cap = config.pileSize().getCount();
		if (pileActivity != activity || pileView != props.getView())
		{
			clearPile();
			// Beside you and a little behind: the pile never blocks your view of the work.
			pileX = at.getX() - fx * 90 - fy * 90;
			pileY = at.getY() - fy * 90 + fx * 90;
			pilePlane = plane;
			pileView = props.getView();
			pileActivity = activity;
			pileCount = 0;
			if (activity == Activity.FISHING)
			{
				Model basket = props.itemModel(ItemID.BASKET_EMPTY, 200, 0);
				Props.Prop b = props.launch(basket, Props.Group.BASKET, plane, pileX, pileY, 0, pileX, pileY, 0, 0, 0.01, false, 0,
					now);
				if (b != null)
				{
					// The basket pops into place.
					Props.resize(b, ItemID.BASKET_EMPTY, 200, 0.25, 0);
				}
			}
		}
		if (pileCount >= cap)
		{
			return;
		}
		int[] slot = PileLayout.slot(pileCount);
		boolean basket = activity == Activity.FISHING;
		// In the basket, catches sit higher and closer together.
		double toX = pileX + (basket ? slot[0] / 2.0 : slot[0]);
		double toY = pileY + (basket ? slot[1] / 2.0 : slot[1]);
		double toZ = basket ? 22 + slot[2] : slot[2];
		Model model = props.itemModel(itemId, 128, 0);
		Props.Prop p = props.launch(model, Props.Group.PILE, plane, fromX, fromY, fromZ, toX, toY, toZ, peak, 0.55, true,
			slot[3], now);
		if (p != null)
		{
			// Sized, so it can shrink away item by item when the pile clears.
			Props.resize(p, itemId, 128, 0, 0);
			pileCount++;
		}
	}

	/**
	 * WHAT: What you used flies from you into what you're making: bars onto the anvil, the herb
	 * and vial into the potion, logs under the knife, the uncut gem under the chisel.
	 * WHY: The product appearing out of nothing skipped half the story; now you see the
	 * ingredients go in, then the product come out.
	 * PRE: Client thread. lost holds what left your inventory; (x, y, z) is where the product
	 * appears, z above the ground.
	 * POST: Up to two small props are added.
	 * RETURN: how long until they arrive, in seconds (0 when nothing went in), so the product can
	 * appear just as they do
	 * THROWS: Nothing.
	 */
	private double ingredients(List<Integer> lost, LocalPoint at, double x, double y, double z, int plane, double now)
	{
		int shown = 0;
		for (int id : lost)
		{
			if (shown >= 2)
			{
				break;
			}
			Props.Prop p = props.launch(props.itemModel(id, 90, 0), Props.Group.FLOATING, plane, at.getX(), at.getY(), 70, x, y, z,
				30, 0.28, false, 0, now + shown * 0.06);
			if (p != null)
			{
				p.spinPerSecond = 500;
				p.expires = now + 0.28 + shown * 0.06;
				Props.resize(p, id, 90, 0.08, 0.1);
				shown++;
			}
		}
		return shown == 0 ? 0 : 0.3 + (shown - 1) * 0.06;
	}

	/**
	 * WHAT: Where a gathered resource comes from: the edge of the tree, rock or patch you're
	 * working (on your side), or the fishing spot you're fishing.
	 * WHY: Everything used to start from the tile in front of you; a big yew's trunk or a
	 * fishing spot two tiles out are somewhere else.
	 * PRE: Client thread. (tx, ty) is the tile in front of you, used if nothing is found.
	 * POST: Nothing changes.
	 * RETURN: {x, y, middleX, middleY} in local units
	 * THROWS: Nothing.
	 */
	private double[] source(Activity activity, LocalPoint at, double tx, double ty)
	{
		String[] actions;
		switch (activity)
		{
			case WOODCUTTING:
				actions = new String[]{"chop", "cut"};
				break;
			case MINING:
				actions = new String[]{"mine"};
				break;
			case FISHING:
			{
				Player player = client.getLocalPlayer();
				Actor spot = player == null ? null : player.getInteracting();
				WorldView spotView = spot == null ? null : spot.getWorldView();
				if (spotView != null && spotView.getId() == viewOf(player).getId() && spot.getLocalLocation() != null)
				{
					LocalPoint s = spot.getLocalLocation();
					return new double[]{s.getX(), s.getY(), s.getX(), s.getY()};
				}
				return new double[]{tx, ty, tx, ty};
			}
			default:
				return new double[]{tx, ty, tx, ty};
		}
		GameObject object = workObject(at, actions);
		if (object == null || object.getLocalLocation() == null)
		{
			return new double[]{tx, ty, tx, ty};
		}
		double cx = object.getLocalLocation().getX();
		double cy = object.getLocalLocation().getY();
		double[] e = edge(cx, cy, Math.max(object.sizeX(), object.sizeY()), at.getX(), at.getY());
		return new double[]{e[0], e[1], cx, cy};
	}

	/**
	 * WHAT: The object within two tiles that has one of the given right-click actions, e.g. the
	 * tree you can "Chop down", nearest first and in front of you when two are as near.
	 * WHY: Matching by action finds every tree, rock and patch the game has, without a list.
	 * PRE: Client thread. actions are lower case starts of the actions, e.g. "chop".
	 * POST: Nothing changes.
	 * RETURN: the object, or null
	 * THROWS: Nothing.
	 */
	private GameObject workObject(LocalPoint at, String... actions)
	{
		Player player = client.getLocalPlayer();
		if (player == null || at == null)
		{
			return null;
		}
		WorldView wv = viewOf(player);
		Tile[][][] tiles = wv.getScene().getTiles();
		int plane = wv.getPlane();
		int px = at.getSceneX();
		int py = at.getSceneY();
		double angle = player.getCurrentOrientation() * Math.PI * 2 / 2048;
		double fx = -Math.sin(angle);
		double fy = -Math.cos(angle);
		GameObject best = null;
		double bestScore = Double.MAX_VALUE;
		for (int x = px - 2; x <= px + 2; x++)
		{
			for (int y = py - 2; y <= py + 2; y++)
			{
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length || tiles[plane][x][y] == null)
				{
					continue;
				}
				for (GameObject object : tiles[plane][x][y].getGameObjects())
				{
					if (object == null || object.getLocalLocation() == null)
					{
						continue;
					}
					ObjectComposition def = client.getObjectDefinition(object.getId());
					if (def != null && def.getImpostorIds() != null)
					{
						def = def.getImpostor();
					}
					if (def == null || !hasAction(def.getActions(), actions))
					{
						continue;
					}
					double dx = object.getLocalLocation().getX() - at.getX();
					double dy = object.getLocalLocation().getY() - at.getY();
					double d = Math.hypot(dx, dy);
					// Nearest wins; one in front of you beats one behind.
					double score = d - (d > 0 ? (dx * fx + dy * fy) / d * 48 : 0);
					if (score < bestScore)
					{
						bestScore = score;
						best = object;
					}
				}
			}
		}
		return best;
	}

	/**
	 * WHAT: Whether any of an object's right-click actions starts with one of the wanted words.
	 * WHY: Trees say "Chop down", rocks "Mine", patches "Harvest" or "Pick"; the exact wording varies.
	 * PRE: actions may be null or hold nulls; wanted are lower case.
	 * POST: Nothing changes.
	 * RETURN: true if one matches
	 * THROWS: Nothing.
	 */
	static boolean hasAction(String[] actions, String... wanted)
	{
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a == null)
			{
				continue;
			}
			String lower = a.toLowerCase(java.util.Locale.ROOT);
			for (String w : wanted)
			{
				if (lower.startsWith(w))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * WHAT: The point on an object's edge facing you, from its middle and its size in tiles.
	 * WHY: A log comes off the trunk on your side, ore off the face you're hitting, a bar out of
	 * the furnace's mouth, not out of the middle of the object.
	 * PRE: (cx, cy) is the object's middle and (px, py) is you, in local units; size >= 1.
	 * POST: Nothing changes.
	 * RETURN: {x, y}
	 * THROWS: Nothing.
	 */
	static double[] edge(double cx, double cy, int size, double px, double py)
	{
		double dx = px - cx;
		double dy = py - cy;
		double d = Math.hypot(dx, dy);
		if (d < 1)
		{
			return new double[]{cx, cy};
		}
		double reach = Math.min(d, Math.max(1, size) * TILE / 2.0 - 16);
		return new double[]{cx + dx / d * reach, cy + dy / d * reach};
	}

	private Model[] heatModels(int itemId)
	{
		Model[] models = new Model[Props.HEAT_STEPS + 1];
		for (int i = 0; i <= Props.HEAT_STEPS; i++)
		{
			models[i] = props.itemModel(itemId, 128, i);
		}
		return models;
	}

	private void smelt(int barId, List<Integer> lost, LocalPoint furnace, LocalPoint at, int plane, double now, double amount)
	{
		double fx = furnace != null ? furnace.getX() : targetX;
		double fy = furnace != null ? furnace.getY() : targetY;
		if (furnace != null)
		{
			// The furnace's mouth, on the side facing you, not its middle.
			double[] mouth = edge(fx, fy, 2, at.getX(), at.getY());
			fx = mouth[0];
			fy = mouth[1];
		}
		// Ore and coal fly from you into the furnace.
		int thrown = 0;
		for (int oreId : lost)
		{
			if (thrown++ >= 2)
			{
				break;
			}
			Props.Prop ore = props.launch(props.itemModel(oreId, 100, 0), Props.Group.FLOATING, plane, at.getX(), at.getY(), 80,
				fx, fy, 60, 60, 0.35, false, 0, now + thrown * 0.06);
			if (ore != null)
			{
				ore.expires = now + 0.45;
				Props.resize(ore, oreId, 100, 0, 0.1);
			}
		}
		if (config.forgeSparks())
		{
			overlay.getParticles().burst(Particles.Kind.EMBER, fx, fy, 70, 8, amount);
			overlay.getParticles().single(Particles.Kind.GLOW, fx, fy, 60, 0xFF8C28, 90, 0.7);
		}
		if (!config.hotMetal())
		{
			return;
		}
		// The bar slides out of the furnace toward you, red-hot, then cools on the ground.
		double outX = fx + (at.getX() - fx) * 0.45;
		double outY = fy + (at.getY() - fy) * 0.45;
		Model[] models = heatModels(barId);
		Props.Prop bar = props.launch(models[Props.HEAT_STEPS], Props.Group.FLOATING, plane, fx, fy, 40, outX, outY, 0, 25, 0.5,
			true, 0, now + 0.35);
		if (bar != null)
		{
			// It cools where it landed, then you pick it up.
			Props.cool(bar, models, now + 0.9, 1.8);
			Props.then(bar, now + 2.8, at.getX(), at.getY(), 80, 40, 0.45);
			bar.expires = now + 3.25;
			Props.resize(bar, barId, 128, 0, 0.2);
		}
	}

	private void smith(int itemId, LocalPoint anvil, double tx, double ty, int plane, double now, double amount)
	{
		double ax = anvil != null ? anvil.getX() : tx;
		double ay = anvil != null ? anvil.getY() : ty;
		double top = anvil != null ? 70 : 40;
		if (config.forgeSparks())
		{
			overlay.getParticles().burst(Particles.Kind.SPARK, ax, ay, top + 5, 16, amount);
			// The flash of the hammer striking hot metal.
			overlay.getParticles().single(Particles.Kind.GLOW, ax, ay, top + 10, 0xFFD27A, 60, 0.3);
		}
		if (!config.hotMetal())
		{
			return;
		}
		Model[] models = heatModels(itemId);
		Props.Prop item = props.launch(models[Props.HEAT_STEPS], Props.Group.FLOATING, plane, ax, ay, top + 20, ax, ay, top, 0,
			0.25, true, 0, now);
		if (item != null)
		{
			// It cools on the anvil, then you pick it up: it flies into your pack. Done before the
			// next one is hammered, so the anvil never holds two.
			LocalPoint me = client.getLocalPlayer().getLocalLocation();
			Props.cool(item, models, now + 0.4, 1.9);
			Props.then(item, now + 2.4, me.getX(), me.getY(), 80, 45, 0.45);
			item.expires = now + 2.85;
			Props.resize(item, itemId, 128, 0, 0.2);
		}
	}

	private void fletch(int itemId, LocalPoint at, double fx, double fy, int plane, double now, double amount)
	{
		double x = at.getX() + fx * 80;
		double y = at.getY() + fy * 80;
		if (config.shavings())
		{
			overlay.getParticles().burst(Particles.Kind.SHAVING, x, y, 70, 8, amount);
		}
		if (!config.fletchedItem())
		{
			return;
		}
		Props.Prop item = props.launch(props.itemModel(itemId, 128, 0), Props.Group.FLOATING, plane, x, y, 60, x, y, 95, 0, 0.6,
			false, 0, now);
		if (item != null)
		{
			item.spinPerSecond = 1400;
			item.spinSettle = 0.5;
			// Then into your pack: it flies to you and shrinks away.
			Props.then(item, now + 1.15, at.getX(), at.getY(), 80, 30, 0.4);
			item.expires = now + 1.55;
			Props.resize(item, itemId, 128, 0.2, 0.2);
		}
	}

	/**
	 * WHAT: The pop's text, e.g. "+15 Arrow shaft".
	 * WHY: It used to say "+1" even when 15 arrived at once.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the text
	 * THROWS: Nothing.
	 */
	static String popText(Activity activity, int quantity, String itemName)
	{
		return "+" + Math.max(1, quantity) + " " + itemName;
	}

	private static final String[] SHINY = {"opal", "jade", "topaz", "sapphire", "emerald", "ruby", "diamond", "dragonstone",
		"onyx", "zenyte", "gold", "silver", "glass", "orb", "ring", "amulet", "necklace", "bracelet", "tiara"};

	/**
	 * WHAT: Small effects while you keep working: chips, sparks, splashes, embers, shavings.
	 * WHY: Between items the scene would otherwise go still; these keep it alive while your
	 * character is still animating at the same spot.
	 * PRE: Client thread; called every tick.
	 * POST: May add a few particles every other tick.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void ambient(Player player, int tick)
	{
		if (lastActivity == null || paused || !config.enabled() || tick % 2 != 0)
		{
			return;
		}
		double now = seconds();
		if (now - lastActivityTime > AMBIENT_SECONDS || player.getAnimation() == -1
			|| lastStandPoint == null || player.getLocalLocation().distanceTo(lastStandPoint) > TILE)
		{
			return;
		}
		double amount = config.amount().getScale() * 0.5;
		Particles particles = overlay.getParticles();
		switch (lastActivity)
		{
			case WOODCUTTING:
				if (config.woodChips())
				{
					particles.burst(Particles.Kind.CHIP, targetX, targetY, 70, 5, amount);
					if (tick % 6 == 0)
					{
						particles.burst(Particles.Kind.FALLING_LEAF, targetX, targetY, 230, 1, 1);
					}
				}
				break;
			case FISHING:
				if (config.splashes())
				{
					particles.burst(Particles.Kind.DROPLET, targetX, targetY, 0, 4, amount);
					if (tick % 4 == 0)
					{
						particles.single(Particles.Kind.RIPPLE, targetX, targetY, 0, 0xDCF0FF, 60, 1.2);
					}
				}
				break;
			case MINING:
				if (config.dustSparks())
				{
					particles.burst(Particles.Kind.SPARK, targetX, targetY, 60, 6, amount);
					particles.burst(Particles.Kind.DUST, targetX, targetY, 50, 2, amount);
					particles.burst(Particles.Kind.STONE, targetX, targetY, 55, 2, amount);
				}
				break;
			case SMELTING:
				if (config.forgeSparks())
				{
					particles.burst(Particles.Kind.EMBER, targetX, targetY, 70, 4, amount);
				}
				break;
			case SMITHING:
				if (config.forgeSparks())
				{
					particles.burst(Particles.Kind.SPARK, targetX, targetY, 75, 8, amount);
				}
				break;
			case FLETCHING:
				if (config.shavings())
				{
					LocalPoint at = player.getLocalLocation();
					particles.burst(Particles.Kind.SHAVING, at.getX(), at.getY(), 70, 2, amount);
				}
				break;
			default:
				// The other skills play once per item.
				break;
		}
	}

	// ---- Panel actions (called on the Swing thread) ----------------------------------------

	/**
	 * WHAT: Plays an activity's effect at your feet, as if you'd just made one item.
	 * WHY: The panel's Preview button lets you try each effect without training.
	 * PRE: Any thread; the work is done on the client thread.
	 * POST: An effect plays if you're logged in.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void preview(Activity activity)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			switch (activity)
			{
				case WOODCUTTING:
					play(activity, ItemID.YEW_LOGS, "Yew logs", Collections.emptyList(), null, 1);
					break;
				case FISHING:
					play(activity, ItemID.RAW_LOBSTER, "Raw lobster", Collections.emptyList(), null, 1);
					break;
				case MINING:
					play(activity, ItemID.IRON_ORE, "Iron ore", Collections.emptyList(), null, 1);
					break;
				case SMELTING:
					play(activity, ItemID.STEEL_BAR, "Steel bar", Arrays.asList(ItemID.IRON_ORE, ItemID.COAL), null, 1);
					break;
				case SMITHING:
					play(activity, ItemID.STEEL_PLATEBODY, "Steel platebody", Collections.emptyList(), null, 1);
					break;
				default:
					play(activity, ItemID.UNSTRUNG_YEW_LONGBOW, "Yew longbow (u)", Collections.emptyList(), null, 1);
					break;
			}
		});
	}

	void clearFromPanel()
	{
		clientThread.invokeLater(this::clearAll);
	}

	void setPaused(boolean paused)
	{
		this.paused = paused;
		refreshPanel();
	}

	boolean isPaused()
	{
		return paused;
	}

	String getStatus()
	{
		return status;
	}

	int getStatusCount()
	{
		return statusCount;
	}

	/**
	 * WHAT: A tip for the panel when effects can't play yet, or "" when there's nothing to say.
	 * WHY: With a basket or barrel from the first catch, nothing plays until one item arrives
	 * normally; without a tip that looks like the plugin is broken.
	 * PRE: Any thread.
	 * POST: Nothing changes.
	 * RETURN: the tip, never null
	 * THROWS: Nothing.
	 */
	String getHint()
	{
		return hint;
	}

	private void setHint(String text)
	{
		if (!hint.equals(text))
		{
			hint = text;
			refreshPanel();
		}
	}

	/**
	 * WHAT: Whether your inventory holds the basket, barrel or bag for a gathering skill.
	 * WHY: Only then is a missing item explained by a bag.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: true if one is in the last inventory seen
	 * THROWS: Nothing.
	 */
	private boolean carriesBag(Skill skill)
	{
		if (lastInventory == null)
		{
			return false;
		}
		for (int id : lastInventory.keySet())
		{
			if (isBag(skill, client.getItemDefinition(id).getName()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * WHAT: Whether an item is the basket, barrel or bag a gathering skill's items go into.
	 * WHY: The bag tip should only show when one is really there, not for XP from other things
	 * such as Forestry events.
	 * PRE: name may be null.
	 * POST: Nothing changes.
	 * RETURN: true for a log or forestry basket, fish barrel or sack, coal or gem bag
	 * THROWS: Nothing.
	 */
	static boolean isBag(Skill skill, String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(java.util.Locale.ROOT);
		switch (skill)
		{
			case WOODCUTTING:
				return n.contains("basket");
			case FISHING:
				return n.contains("fish barrel") || n.contains("fish sack");
			case MINING:
				return n.contains("coal bag") || n.contains("gem bag");
			default:
				return false;
		}
	}

	/**
	 * WHAT: The tip for a gathering skill whose items all went into a bag so far.
	 * WHY: Effects replay the last item; until there is one, the player needs to know why.
	 * PRE: skill is Woodcutting, Fishing or Mining.
	 * POST: Nothing changes.
	 * RETURN: the tip
	 * THROWS: Nothing.
	 */
	static String bagHint(Skill skill)
	{
		switch (skill)
		{
			case WOODCUTTING:
				return "Your logs are going into a basket. Chop one without it and effects will follow.";
			case FISHING:
				return "Your catches are going into a barrel. Catch one without it and effects will follow.";
			default:
				return "Your ore is going into a bag. Mine one without it and effects will follow.";
		}
	}

	int getStatusCap()
	{
		return statusCap;
	}

	// ---- Helpers ----------------------------------------------------------------------------

	private void clearPile()
	{
		if (props != null)
		{
			// Shrinks away item by item; logging out and map loads still clear at once (clearAll).
			props.dissolve(Props.Group.PILE, seconds());
			props.dissolve(Props.Group.BASKET, seconds());
		}
		pileActivity = null;
		pileCount = 0;
		statusCount = 0;
		refreshPanel();
	}

	private void clearAll()
	{
		if (props != null)
		{
			props.clear(null);
		}
		overlay.clear();
		pileActivity = null;
		pileCount = 0;
		lastActivity = null;
		status = "";
		statusCount = 0;
		refreshPanel();
	}

	private void updateStatus(Activity activity)
	{
		status = activity.getDisplayName();
		boolean piles = activity.isGathering();
		statusCount = piles ? pileCount : 0;
		statusCap = piles ? config.pileSize().getCount() : 0;
		refreshPanel();
	}

	private void refreshPanel()
	{
		ImmersiveSkillingPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	private static double seconds()
	{
		return System.nanoTime() / 1e9;
	}
}
