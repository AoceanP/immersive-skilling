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
	description = "Visual only: log piles, catch baskets, ore chunks, red-hot bars and items, sparks and +1 pops while you skill",
	tags = {"immersive", "visual", "cosmetic", "woodcutting", "fishing", "mining", "smithing", "smelting", "fletching", "skilling"}
)
public class ImmersiveSkillingPlugin extends Plugin
{
	/** XP and the item it gave can arrive a tick apart; they still count as one action. */
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
		pending.clear();
		matchedXp.clear();
		checkedXp.clear();
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
			if (walkedAway || dist > FAR_TILES || wv.getPlane() != pilePlane || wv.getId() != pileView)
			{
				clearPile();
			}
		}
		ambient(player, tick);
		bagFallback(player, tick);
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
			play(activity, itemId, client.getItemDefinition(itemId).getName(), Collections.emptyList(), null);
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
			play(activity, gain.itemId, name, gain.lost, activity == Activity.SMELTING ? furnace : anvil);
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

	/**
	 * WHAT: The worldview you're standing in: a Sailing boat's deck, or the main world.
	 * WHY: On a boat your local position is on the boat's own worldview; props and effects
	 * placed in the main world showed up in the wrong spot or not at all.
	 * PRE: Client thread. player may be null.
	 * POST: Nothing changes.
	 * RETURN: the worldview, never null while logged in
	 * THROWS: Nothing.
	 */
	private WorldView viewOf(Player player)
	{
		WorldView wv = player == null ? null : player.getWorldView();
		return wv != null ? wv : client.getTopLevelWorldView();
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
	 * WHAT: The nearest object within 3 tiles whose name contains the given word, e.g. "anvil".
	 * WHY: Hot bars appear at the furnace you're using and items on the anvil you're using.
	 * PRE: Client thread.
	 * POST: Nothing changes.
	 * RETURN: its location, or null if there isn't one nearby
	 * THROWS: Nothing.
	 */
	private LocalPoint findObject(String word)
	{
		Player player = client.getLocalPlayer();
		WorldView wv = viewOf(player);
		if (player == null || wv == null || player.getLocalLocation() == null)
		{
			return null;
		}
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
					if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(word))
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

	// ---- Effects ----------------------------------------------------------------------------

	/**
	 * WHAT: Plays the effect for one item made by an activity.
	 * WHY: The heart of the plugin: decides which props fly where and which particles burst.
	 * PRE: Client thread. station is the furnace or anvil, or null.
	 * POST: Props and particles are added; the panel status is updated.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void play(Activity activity, int itemId, String itemName, List<Integer> lost, LocalPoint station)
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
		String popText = "+1 " + itemName;

		switch (activity)
		{
			case WOODCUTTING:
				if (config.woodChips())
				{
					particles.burst(Particles.Kind.CHIP, tx, ty, 70, 10, amount);
				}
				if (config.logPile())
				{
					addToPile(activity, itemId, at, fx, fy, plane, tx, ty, 50, 70, now);
				}
				break;
			case FISHING:
				if (config.splashes())
				{
					particles.burst(Particles.Kind.DROPLET, tx, ty, 0, 12, amount);
				}
				if (config.catchBasket())
				{
					addToPile(activity, itemId, at, fx, fy, plane, tx, ty, 20, 120, now);
				}
				break;
			case MINING:
				if (config.dustSparks())
				{
					particles.burst(Particles.Kind.DUST, tx, ty, 50, 6, amount);
					particles.burst(Particles.Kind.SPARK, tx, ty, 60, 10, amount);
				}
				if (config.oreChunks())
				{
					addToPile(activity, itemId, at, fx, fy, plane, tx, ty, 50, 55, now);
				}
				break;
			case SMELTING:
				smelt(itemId, lost, station, at, plane, now, amount);
				break;
			case SMITHING:
				smith(itemId, station, tx, ty, plane, now, amount);
				break;
			default:
				fletch(itemId, at, fx, fy, plane, now, amount);
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
				props.launch(basket, Props.Group.BASKET, plane, pileX, pileY, 0, pileX, pileY, 0, 0, 0.01, false, 0, now);
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
			pileCount++;
		}
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
			}
		}
		if (config.forgeSparks())
		{
			overlay.getParticles().burst(Particles.Kind.EMBER, fx, fy, 70, 8, amount);
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
			Props.cool(bar, models, now + 0.9, 3.0);
			bar.expires = now + 5;
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
		}
		if (!config.hotMetal())
		{
			return;
		}
		// Only one item on the anvil at a time: the last one makes way.
		props.clear(Props.Group.FLOATING);
		Model[] models = heatModels(itemId);
		Props.Prop item = props.launch(models[Props.HEAT_STEPS], Props.Group.FLOATING, plane, ax, ay, top + 20, ax, ay, top, 0,
			0.25, true, 0, now);
		if (item != null)
		{
			Props.cool(item, models, now + 0.5, 3.0);
			item.expires = now + 4.5;
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
			item.spinPerSecond = 700;
			item.expires = now + 1.6;
		}
	}

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
				}
				break;
			case FISHING:
				if (config.splashes())
				{
					particles.burst(Particles.Kind.DROPLET, targetX, targetY, 0, 4, amount);
				}
				break;
			case MINING:
				if (config.dustSparks())
				{
					particles.burst(Particles.Kind.SPARK, targetX, targetY, 60, 6, amount);
					particles.burst(Particles.Kind.DUST, targetX, targetY, 50, 2, amount);
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
			default:
				if (config.shavings())
				{
					LocalPoint at = player.getLocalLocation();
					particles.burst(Particles.Kind.SHAVING, at.getX(), at.getY(), 70, 2, amount);
				}
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
					play(activity, ItemID.YEW_LOGS, "Yew logs", Collections.emptyList(), null);
					break;
				case FISHING:
					play(activity, ItemID.RAW_LOBSTER, "Raw lobster", Collections.emptyList(), null);
					break;
				case MINING:
					play(activity, ItemID.IRON_ORE, "Iron ore", Collections.emptyList(), null);
					break;
				case SMELTING:
					play(activity, ItemID.STEEL_BAR, "Steel bar", java.util.Arrays.asList(ItemID.IRON_ORE, ItemID.COAL), null);
					break;
				case SMITHING:
					play(activity, ItemID.STEEL_PLATEBODY, "Steel platebody", Collections.emptyList(), null);
					break;
				default:
					play(activity, ItemID.UNSTRUNG_YEW_LONGBOW, "Yew longbow (u)", Collections.emptyList(), null);
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
			props.clear(Props.Group.PILE);
			props.clear(Props.Group.BASKET);
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
		hint = "";
		statusCount = 0;
		refreshPanel();
	}

	private void updateStatus(Activity activity)
	{
		status = activity.getDisplayName();
		boolean piles = activity == Activity.WOODCUTTING || activity == Activity.FISHING || activity == Activity.MINING;
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
