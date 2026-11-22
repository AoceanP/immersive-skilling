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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Skill;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The plugin's logic without the game: which activity an item came from, the motion curves,
 * red-hot colours, pile layout, particles and the settings' defaults.
 */
public class ImmersiveSkillingTest
{
	private static final double EPS = 1e-9;

	// ---- Activity -------------------------------------------------------------------------------

	@Test
	public void logsFromWoodcuttingAreWoodcutting()
	{
		assertEquals(Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Yew logs", false, false));
		assertEquals(Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Logs", false, false));
		assertNull("A bird nest isn't a log", Activity.classify(Skill.WOODCUTTING, "Bird nest", false, false));
	}

	@Test
	public void catchesAreFishing()
	{
		assertEquals(Activity.FISHING, Activity.classify(Skill.FISHING, "Raw lobster", false, false));
		assertEquals(Activity.FISHING, Activity.classify(Skill.FISHING, "Leaping trout", false, false));
		assertNull(Activity.classify(Skill.FISHING, "Clue bottle (easy)", false, false));
	}

	@Test
	public void oresAndGemsAreMining()
	{
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Iron ore", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Coal", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Uncut sapphire", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Pure essence", false, false));
	}

	@Test
	public void smithingDependsOnWhereYouStand()
	{
		assertEquals("A bar at a furnace is smelting", Activity.SMELTING, Activity.classify(Skill.SMITHING, "Steel bar", true, false));
		assertEquals("An item at an anvil is smithing", Activity.SMITHING, Activity.classify(Skill.SMITHING, "Steel platebody", false, true));
		assertNull("A bar away from a furnace shows nothing", Activity.classify(Skill.SMITHING, "Steel bar", false, true));
		assertNull("An item away from an anvil shows nothing", Activity.classify(Skill.SMITHING, "Steel platebody", false, false));
		assertTrue(Activity.SMELTING.isHot());
		assertTrue(Activity.SMITHING.isHot());
	}

	@Test
	public void fletchingAndUnrelatedSkills()
	{
		assertEquals(Activity.FLETCHING, Activity.classify(Skill.FLETCHING, "Yew longbow (u)", false, false));
		assertNull(Activity.classify(Skill.SLAYER, "Lobster", false, false));
		assertNull(Activity.classify(null, "Yew logs", false, false));
		assertNull(Activity.classify(Skill.WOODCUTTING, null, false, false));
	}

	@Test
	public void catchesAndRocksThatArentRawOrOre()
	{
		assertEquals(Activity.FISHING, Activity.classify(Skill.FISHING, "Infernal eel", false, false));
		assertEquals(Activity.FISHING, Activity.classify(Skill.FISHING, "Minnow", false, false));
		assertEquals(Activity.FISHING, Activity.classify(Skill.FISHING, "Bluegill", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Pay-dirt", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Amethyst", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Barronite shards", false, false));
	}

	@Test
	public void newSkills()
	{
		assertEquals(Activity.RUNECRAFT, Activity.classify(Skill.RUNECRAFT, "Nature rune", false, false));
		assertNull("A tiara isn't a rune", Activity.classify(Skill.RUNECRAFT, "Air tiara", false, false));
		assertEquals(Activity.THIEVING, Activity.classify(Skill.THIEVING, "Coins", false, false));
		assertEquals(Activity.HERBLORE, Activity.classify(Skill.HERBLORE, "Prayer potion(3)", false, false));
		// Firemaking is recognised by what you burn, not by what arrives.
		assertNull(Activity.classify(Skill.FIREMAKING, "Ashes", false, false));
		assertEquals(Activity.FIREMAKING, Activity.classifyUsed(Skill.FIREMAKING, "Yew logs"));
		assertEquals(Activity.FIREMAKING, Activity.classifyUsed(Skill.FIREMAKING, "Bruma root"));
		assertNull(Activity.classifyUsed(Skill.FIREMAKING, "Tinderbox"));
		assertNull("Dropping a log isn't firemaking", Activity.classifyUsed(Skill.WOODCUTTING, "Yew logs"));
		assertTrue(ImmersiveSkillingPlugin.isShown(Skill.THIEVING));
		assertTrue(Activity.MINING.isGathering());
		assertFalse(Activity.RUNECRAFT.isGathering());
	}

	@Test
	public void craftingPrayerMagicFarmingAndHunter()
	{
		assertEquals(Activity.CRAFTING, Activity.classify(Skill.CRAFTING, "Sapphire ring", false, false));
		assertEquals(Activity.ALCHING, Activity.classify(Skill.MAGIC, "Coins", false, false));
		assertNull("Enchanting isn't alching", Activity.classify(Skill.MAGIC, "Ring of recoil", false, false));
		assertEquals(Activity.FARMING, Activity.classify(Skill.FARMING, "Potato", false, false));
		assertEquals(Activity.HUNTER, Activity.classify(Skill.HUNTER, "Red chinchompa", false, false));
		// Prayer, like Firemaking, is recognised by what leaves.
		assertEquals(Activity.PRAYER, Activity.classifyUsed(Skill.PRAYER, "Dragon bones"));
		assertEquals(Activity.PRAYER, Activity.classifyUsed(Skill.PRAYER, "Infernal ashes"));
		assertNull(Activity.classifyUsed(Skill.PRAYER, "Prayer potion(4)"));
		assertNull("Burying isn't firemaking", Activity.classifyUsed(Skill.FIREMAKING, "Dragon bones"));
		assertTrue(Activity.FARMING.isGathering());
		assertFalse(Activity.HUNTER.isGathering());
		assertEquals("-1 Dragon bones", ImmersiveSkillingPlugin.popText(Activity.PRAYER, 1, "Dragon bones"));
		assertEquals("+39000 Coins", ImmersiveSkillingPlugin.popText(Activity.ALCHING, 39000, "Coins"));
	}

	@Test
	public void itemChangingSpellsAreToldApartByWhatWentIn()
	{
		assertEquals("superheat", Activity.spell("Steel bar", Arrays.asList("Iron ore", "Coal", "Nature rune")));
		assertEquals("plank", Activity.spell("Oak plank", Arrays.asList("Oak logs", "Astral rune", "Nature rune")));
		assertEquals("plank", Activity.spell("Plank", Arrays.asList("Logs", "Astral rune")));
		assertEquals("bolts", Activity.spell("Ruby bolts (e)", Arrays.asList("Ruby bolts", "Cosmic rune", "Blood rune")));
		assertEquals("enchant", Activity.spell("Ring of recoil", Arrays.asList("Sapphire ring", "Cosmic rune")));
		assertEquals("enchant", Activity.spell("Games necklace(8)", Arrays.asList("Emerald necklace", "Cosmic rune")));
		assertEquals("string", Activity.spell("Sapphire amulet", Arrays.asList("Sapphire amulet (u)", "Astral rune")));
		// Alching pays coins, telegrab takes only runes, and a bow string isn't jewellery.
		assertNull(Activity.spell("Coins", Arrays.asList("Rune platebody", "Nature rune")));
		assertNull(Activity.spell("Wine of zamorak", Arrays.asList("Law rune", "Air rune")));
		assertNull(Activity.spell("Leather", Arrays.asList("Bow string")));
		assertNull(Activity.spell(null, Collections.emptyList()));
		assertEquals("Magic", Activity.SPELLCASTING.skillName());
		assertEquals(0xFF3C50, Activity.gemColor("Ruby bolts (e) Ruby bolts"));
		assertEquals(0x4A78FF, Activity.gemColor("Ring of recoil Sapphire ring"));
		assertEquals(0x9EC8FF, Activity.gemColor(null));
		ImmersiveSkillingConfig config = new ImmersiveSkillingConfig() { };
		assertTrue(config.enchantGlow() && config.superheatMelt() && config.plankMake() && config.stringJewellery());
	}

	@Test
	public void agilityAndConstruction()
	{
		assertEquals(Activity.CONSTRUCTION, Activity.classifyUsed(Skill.CONSTRUCTION, "Oak plank"));
		assertNull("Nails go too, but the plank is what you see", Activity.classifyUsed(Skill.CONSTRUCTION, "Steel nails"));
		assertEquals("-4 Oak plank", ImmersiveSkillingPlugin.popText(Activity.CONSTRUCTION, 4, "Oak plank"));
		assertEquals("+46 Agility XP", ImmersiveSkillingPlugin.agilityText(46));
		assertEquals("+1,140 Agility XP", ImmersiveSkillingPlugin.agilityText(1140));
		assertTrue(ImmersiveSkillingPlugin.isShown(Skill.AGILITY) && ImmersiveSkillingPlugin.isShown(Skill.CONSTRUCTION));
	}

	@Test
	public void propsGrowInAndShrinkAway()
	{
		// Starts small, overshoots a little, settles at full size, then shrinks to nothing.
		assertEquals(0.3, Props.size(0, 10, 0.2, 0.25), 1e-9);
		assertTrue(Props.size(0.14, 10, 0.2, 0.25) > 1.05);
		assertEquals(1.0, Props.size(0.5, 10, 0.2, 0.25), 1e-9);
		assertEquals(0.5, Props.size(0.5, 0.125, 0.2, 0.25), 1e-9);
		assertEquals(0.0, Props.size(0.5, 0, 0.2, 0.25), 1e-9);
		assertEquals("No resize asked, no change", 1.0, Props.size(0, 0, 0, 0), 1e-9);
	}

	@Test
	public void gemsAndJewelleryShine()
	{
		assertTrue(ImmersiveSkillingPlugin.isShiny("Sapphire"));
		assertTrue(ImmersiveSkillingPlugin.isShiny("Gold amulet (u)"));
		assertTrue(ImmersiveSkillingPlugin.isShiny("Unpowered orb"));
		assertFalse("An uncut gem is still rough", ImmersiveSkillingPlugin.isShiny("Uncut ruby"));
		assertFalse(ImmersiveSkillingPlugin.isShiny("Leather body"));
		assertFalse(ImmersiveSkillingPlugin.isShiny(null));
	}

	@Test
	public void popsShowHowMany()
	{
		assertEquals("+15 Arrow shaft", ImmersiveSkillingPlugin.popText(Activity.FLETCHING, 15, "Arrow shaft"));
		assertEquals("+1 Yew logs", ImmersiveSkillingPlugin.popText(Activity.WOODCUTTING, 1, "Yew logs"));
		assertEquals("-1 Yew logs", ImmersiveSkillingPlugin.popText(Activity.FIREMAKING, 1, "Yew logs"));
		assertTrue(ImmersiveSkillingPlugin.isPotion("Saradomin brew(4)"));
		assertFalse(ImmersiveSkillingPlugin.isPotion("Ranarr weed"));
	}

	@Test
	public void onlyTheEighteenSkillsAreWatched()
	{
		int shown = 0;
		for (Skill s : Skill.values())
		{
			shown += ImmersiveSkillingPlugin.isShown(s) ? 1 : 0;
		}
		// Combat skills are never touched.
		assertEquals(18, shown);
		assertTrue("Cooking joined in 1.5.0", ImmersiveSkillingPlugin.isShown(Skill.COOKING));
		assertTrue("Sailing joined in 1.4.0", ImmersiveSkillingPlugin.isShown(Skill.SAILING));
		assertFalse(ImmersiveSkillingPlugin.isShown(Skill.ATTACK) || ImmersiveSkillingPlugin.isShown(Skill.SLAYER));
	}

	// ---- Motion ---------------------------------------------------------------------------------

	@Test
	public void curvesStartAtZeroAndEndAtOne()
	{
		assertEquals(0, Motion.smooth(0), EPS);
		assertEquals(1, Motion.smooth(1), EPS);
		assertEquals(0.5, Motion.smooth(0.5), EPS);
		assertEquals(1, Motion.easeOut(1), EPS);
		assertEquals(1, Motion.bounce(1), EPS);
		assertEquals(0, Motion.bounce(0), EPS);
		assertEquals(1, Motion.clamp(7), EPS);
		assertEquals(0, Motion.clamp(-3), EPS);
	}

	@Test
	public void arcsPeakInTheMiddle()
	{
		assertEquals(0, Motion.arcHeight(0, 80), EPS);
		assertEquals(0, Motion.arcHeight(1, 80), EPS);
		assertEquals(80, Motion.arcHeight(0.5, 80), EPS);
		assertTrue(Motion.arcHeight(0.25, 80) < 80);
	}

	@Test
	public void smoothNeverGoesBackwards()
	{
		double last = -1;
		for (int i = 0; i <= 100; i++)
		{
			double v = Motion.smooth(i / 100.0);
			assertTrue(v >= last);
			last = v;
		}
	}

	// ---- Heat colours ---------------------------------------------------------------------------

	@Test
	public void coldIsUnchanged()
	{
		short grey = HeatColor.pack(20, 1, 50);
		assertEquals(grey, HeatColor.heat(grey, 0));
	}

	@Test
	public void fullyHotIsBrightOrange()
	{
		short hot = HeatColor.heat(HeatColor.pack(42, 2, 40), 1);
		assertEquals(HeatColor.HOT_HUE, HeatColor.hue(hot));
		assertEquals(HeatColor.HOT_SATURATION, HeatColor.saturation(hot));
		assertTrue(HeatColor.lightness(hot) >= HeatColor.HOT_MIN_LIGHTNESS);
	}

	@Test
	public void packingRoundTrips()
	{
		short c = HeatColor.pack(37, 5, 99);
		assertEquals(37, HeatColor.hue(c));
		assertEquals(5, HeatColor.saturation(c));
		assertEquals(99, HeatColor.lightness(c));
	}

	@Test
	public void coolingDimsStepByStep()
	{
		short steel = HeatColor.pack(40, 1, 45);
		int last = Integer.MAX_VALUE;
		for (int step = Props.HEAT_STEPS; step >= 0; step--)
		{
			int light = HeatColor.lightness(HeatColor.heat(steel, step / (double) Props.HEAT_STEPS));
			assertTrue(light <= last);
			last = light;
		}
	}

	// ---- Pile layout ----------------------------------------------------------------------------

	@Test
	public void pileStacksThreeTwoOne()
	{
		int[] heights = new int[6];
		Set<String> spots = new HashSet<>();
		for (int i = 0; i < 6; i++)
		{
			int[] s = PileLayout.slot(i);
			heights[i] = s[2];
			spots.add(s[0] + "," + s[1] + "," + s[2]);
		}
		assertEquals("No two logs in the same spot", 6, spots.size());
		assertEquals(0, heights[0]);
		assertEquals(0, heights[2]);
		assertEquals(PileLayout.LAYER_HEIGHT, heights[3]);
		assertEquals(PileLayout.LAYER_HEIGHT * 2, heights[5]);
		// Each row lies across the one below, give or take a hand-stacked wobble.
		assertTrue("The middle layer lies across the bottom one", turn(PileLayout.slot(3)[3], 512) <= 20);
		assertTrue(turn(PileLayout.slot(0)[3], 0) <= 20);
	}

	private static int turn(int a, int b)
	{
		int d = Math.abs(a - b) & 2047;
		return Math.min(d, 2048 - d);
	}

	@Test
	public void biggerStacksHaveAWiderBase()
	{
		// Ten logs: 4, 3, 2, 1, all in one stack instead of a second one off to the side.
		for (int i = 0; i < 4; i++)
		{
			assertEquals(0, PileLayout.slot(PileLayout.Shape.STACK, i, 10)[2]);
		}
		assertEquals(PileLayout.LAYER_HEIGHT, PileLayout.slot(PileLayout.Shape.STACK, 4, 10)[2]);
		assertEquals(PileLayout.LAYER_HEIGHT * 3, PileLayout.slot(PileLayout.Shape.STACK, 9, 10)[2]);
		assertTrue(Math.abs(PileLayout.slot(PileLayout.Shape.STACK, 0, 10)[0]) > Math.abs(PileLayout.slot(PileLayout.Shape.STACK, 0, 6)[0]));
	}

	@Test
	public void oreAndHarvestsHeapUp()
	{
		Set<String> spots = new HashSet<>();
		int top = 0;
		for (int i = 0; i < 11; i++)
		{
			int[] s = PileLayout.slot(PileLayout.Shape.HEAP, i, 10);
			spots.add(s[0] + "," + s[1] + "," + s[2]);
			top = Math.max(top, s[2]);
			if (i < 5)
			{
				assertEquals("The first five ring the ground", 0, s[2]);
			}
		}
		assertEquals("No two in the same spot", 11, spots.size());
		assertEquals("The last one crowns the heap", top, PileLayout.slot(PileLayout.Shape.HEAP, 10, 10)[2]);
		assertEquals(0, PileLayout.slot(PileLayout.Shape.HEAP, 10, 10)[0]);
	}

	@Test
	public void catchesSitInTheBasket()
	{
		for (int i = 0; i < 10; i++)
		{
			int[] s = PileLayout.slot(PileLayout.Shape.BASKET, i, 10);
			assertTrue("Above the basket floor", s[2] >= 22);
			assertTrue("Inside the basket", Math.hypot(s[0], s[1]) <= 16);
		}
	}

	@Test
	public void pilesGoOnAFreeTileBesideYou()
	{
		// Facing north (0, 1): behind-left is (-1, -1) from you.
		assertArrayEquals(new int[]{9, 9}, PileLayout.freeTile(10, 10, 0, 1, (x, y) -> false));
		// That tile is a wall: try behind-right instead.
		assertArrayEquals(new int[]{11, 9}, PileLayout.freeTile(10, 10, 0, 1, (x, y) -> x == 9 && y == 9));
		// Never the tile you're working on, and nothing at all when you're boxed in.
		Set<String> tried = new HashSet<>();
		PileLayout.freeTile(10, 10, 0, 1, (x, y) -> tried.add(x + "," + y) || true);
		assertFalse(tried.contains("10,11"));
		assertFalse(tried.contains("10,10"));
		assertNull(PileLayout.freeTile(10, 10, 0, 1, (x, y) -> true));
	}

	@Test
	public void onABoatThePileStaysOnYourTile()
	{
		// Issue #2: on a raft the tile behind you is the sea. Every slot of every full pile, turned
		// any way, must stay inside your own tile (64 units from its middle).
		for (PileLayout.Shape shape : PileLayout.Shape.values())
		{
			for (int i = 0; i < 12; i++)
			{
				int[] s = PileLayout.slot(shape, Math.min(i, 9), 10);
				double reach = Math.hypot(s[0], s[1]) * ImmersiveSkillingPlugin.BOAT_SPREAD + ImmersiveSkillingPlugin.BOAT_PILE_BACK;
				assertTrue(shape + " slot " + i + " reaches " + reach, reach < 64);
			}
		}
		double[] spot = ImmersiveSkillingPlugin.boatPileSpot(1000, 1000, 0, 1);
		assertEquals("Just behind you", 1000 - ImmersiveSkillingPlugin.BOAT_PILE_BACK, spot[1], 1e-9);
		assertEquals(1000, spot[0], 1e-9);
	}

	@Test
	public void eachResourceGetsItsOwnPileBesideIt()
	{
		java.util.Set<Long> none = new HashSet<>();
		// A tree at (10, 10), you standing two tiles south: the pile goes on the tile between you.
		int[] t = PileLayout.tileBeside(10 * 128 + 64, 10 * 128 + 64, 1, 10, 8, (x, y) -> false, none);
		assertEquals(10, t[0]);
		assertEquals(9, t[1]);
		// That tile is water: the next nearest tile beside the tree instead.
		int[] dry = PileLayout.tileBeside(10 * 128 + 64, 10 * 128 + 64, 1, 10, 8, (x, y) -> x == 10 && y == 9, none);
		assertEquals(1, Math.abs(dry[0] - 10) + Math.abs(dry[1] - 9));
		// Never on the tree itself or on you, and never on another pile's tile.
		java.util.Set<Long> taken = new HashSet<>(java.util.Collections.singleton(PileLayout.key(10, 9)));
		int[] next = PileLayout.tileBeside(10 * 128 + 64, 10 * 128 + 64, 1, 10, 9, (x, y) -> false, taken);
		assertFalse(next[0] == 10 && next[1] == 10);
		assertFalse(next[0] == 10 && next[1] == 9);
		// A big 2x2 tree: the pile sits just outside it.
		int[] big = PileLayout.tileBeside(20 * 128, 20 * 128, 2, 20, 16, (x, y) -> false, none);
		assertEquals(18, big[1]);
		// Everything blocked: no tile.
		assertNull(PileLayout.tileBeside(10 * 128 + 64, 10 * 128 + 64, 1, 10, 8, (x, y) -> true, none));
	}

	@Test
	public void pilesAreKeptPerResource()
	{
		String treeA = ImmersiveSkillingPlugin.pileKey(Activity.WOODCUTTING, false, 1, 10 * 128 + 64, 10 * 128 + 64, 0, -1);
		String treeB = ImmersiveSkillingPlugin.pileKey(Activity.WOODCUTTING, false, 1, 13 * 128 + 64, 10 * 128 + 64, 0, -1);
		assertFalse("Two trees, two piles", treeA.equals(treeB));
		assertEquals("The same tree, the same pile", treeA,
			ImmersiveSkillingPlugin.pileKey(Activity.WOODCUTTING, false, 1, 10 * 128 + 70, 10 * 128 + 50, 0, -1));
		assertFalse("A rock on that tile is its own pile",
			treeA.equals(ImmersiveSkillingPlugin.pileKey(Activity.MINING, false, 1, 10 * 128 + 64, 10 * 128 + 64, 0, -1)));
		assertEquals("On a boat, one pile on your tile", ImmersiveSkillingPlugin.pileKey(Activity.FISHING, true, 1, 1, 1, 0, 3),
			ImmersiveSkillingPlugin.pileKey(Activity.FISHING, true, 1, 900, 900, 0, 3));
		assertTrue(ImmersiveSkillingPlugin.MAX_PILES >= 2 && ImmersiveSkillingPlugin.WALK_AWAY_TILES >= 5);
	}

	@Test
	public void potionSipsArentHerblore()
	{
		assertTrue(ImmersiveSkillingPlugin.isSip("Prayer potion(3)", java.util.Arrays.asList("Prayer potion(4)")));
		assertTrue("The last dose leaves a vial", ImmersiveSkillingPlugin.isSip("Vial", java.util.Arrays.asList("Super restore(1)")));
		assertFalse("Making a potion from an unfinished one is Herblore",
			ImmersiveSkillingPlugin.isSip("Prayer potion(3)", java.util.Arrays.asList("Ranarr potion (unf)", "Snape grass")));
		assertFalse(ImmersiveSkillingPlugin.isSip("Yew longbow (u)", java.util.Arrays.asList("Yew logs")));
		assertFalse(ImmersiveSkillingPlugin.isSip(null, null));
	}

	@Test
	public void skillsThatTakeAnyItemWaitForTheTick()
	{
		assertTrue(ImmersiveSkillingPlugin.takesAnyItem(Skill.HERBLORE) && ImmersiveSkillingPlugin.takesAnyItem(Skill.FLETCHING));
		assertFalse("Logs are only logs", ImmersiveSkillingPlugin.takesAnyItem(Skill.WOODCUTTING));
		assertTrue(ImmersiveSkillingPlugin.WORN_TICKS >= 1);
	}

	@Test
	public void popsThatArriveTogetherStack()
	{
		java.util.List<EffectsOverlay.Pop> pops = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			EffectsOverlay.Pop p = new EffectsOverlay.Pop(1, "+1 Item", 0, 0, 0, 10.0 + i * 0.05);
			p.lane = EffectsOverlay.freeLane(pops, p.start);
			pops.add(p);
		}
		assertEquals(0, pops.get(0).lane);
		assertEquals(1, pops.get(1).lane);
		assertEquals(2, pops.get(2).lane);
		assertEquals("A pop a second later goes back to the usual row", 0, EffectsOverlay.freeLane(pops, 11.5));
	}

	@Test
	public void gauntletBarkAndRootsAreWoodcutting()
	{
		assertEquals(Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Phren bark", false, false));
		assertEquals(Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Bruma root", false, false));
		assertEquals("Goal reached: Lucky five", ImmersiveSkillingPlugin.goalText("Lucky five"));
	}

	@Test
	public void pilesForMadeItemsAreOffByDefault()
	{
		ImmersiveSkillingConfig c = new ImmersiveSkillingConfig()
		{
		};
		assertFalse("Made items still fly into your pack unless you turn this on", c.productionPiles());
	}

	@Test
	public void workObjectsAreFoundByTheirActions()
	{
		assertTrue(ImmersiveSkillingPlugin.hasAction(new String[]{null, "Chop down", "Examine"}, "chop", "cut"));
		assertTrue(ImmersiveSkillingPlugin.hasAction(new String[]{"Mine", null}, "mine"));
		assertTrue(ImmersiveSkillingPlugin.hasAction(new String[]{"Pick", "Inspect"}, "harvest", "pick", "dig"));
		assertFalse(ImmersiveSkillingPlugin.hasAction(new String[]{"Examine"}, "chop"));
		assertFalse(ImmersiveSkillingPlugin.hasAction(null, "chop"));
	}

	@Test
	public void resourcesComeOffTheSideFacingYou()
	{
		// A 2x2 yew with its middle at (1000, 1000) and you two tiles south: the log comes off the
		// south side of the trunk, 112 units out, not from the middle.
		double[] e = ImmersiveSkillingPlugin.edge(1000, 1000, 2, 1000, 744);
		assertEquals(1000, e[0], 1e-9);
		assertEquals(888, e[1], 1e-9);
		// Never past you, even right up against it.
		double[] close = ImmersiveSkillingPlugin.edge(1000, 1000, 3, 1000, 950);
		assertEquals(950, close[1], 1e-9);
		// Standing in the middle: the middle.
		assertArrayEquals(new double[]{5, 5}, ImmersiveSkillingPlugin.edge(5, 5, 1, 5, 5), 1e-9);
	}

	@Test
	public void rareItemsTurnGold()
	{
		// Any colour becomes gold, keeping light and shade: darker in stays darker out.
		short darkRed = HeatColor.pack(0, 7, 20);
		short paleBlue = HeatColor.pack(40, 3, 100);
		for (short c : new short[]{darkRed, paleBlue})
		{
			int g = HeatColor.gold(c) & 0xFFFF;
			assertEquals(HeatColor.GOLD_HUE, HeatColor.hue(g));
			assertEquals(HeatColor.GOLD_SATURATION, HeatColor.saturation(g));
		}
		assertTrue(HeatColor.lightness(HeatColor.gold(darkRed)) < HeatColor.lightness(HeatColor.gold(paleBlue)));
	}

	@Test
	public void rareMomentsAreOneIn512ByDefault()
	{
		ImmersiveSkillingConfig c = new ImmersiveSkillingConfig()
		{
		};
		assertTrue(c.rareMoments());
		assertEquals(512, c.rareChance().getOdds());
		assertEquals("1 in 512", c.rareChance().toString());
	}

	@Test
	public void rareLogUnlocksAchievements()
	{
		RareLog log = new RareLog();
		assertEquals(java.util.Collections.singletonList(RareLog.Achievement.FIRST_GLEAM), log.record("Woodcutting", "Yew logs", 1));
		for (int i = 0; i < 3; i++)
		{
			assertTrue(log.record("Woodcutting", "Yew logs", 2 + i).isEmpty());
		}
		assertEquals(java.util.Collections.singletonList(RareLog.Achievement.LUCKY_FIVE), log.record("Fishing", "Raw lobster", 9));
		assertEquals(2, log.skills());
		assertEquals(4, log.count("Woodcutting"));
		assertEquals("Newest first", "Raw lobster", log.recent().get(0).item);
		assertEquals(RareLog.RECENT, log.recent().size());
		for (String skill : new String[]{"Mining", "Prayer", "Magic"})
		{
			log.record(skill, "x", 20);
		}
		assertTrue(log.has(RareLog.Achievement.ALL_ROUNDER));
		assertFalse(log.has(RareLog.Achievement.GILDED_HANDS));
		assertEquals(5, RareLog.Achievement.GILDED_HANDS.progress(log.total(), log.skills()));
	}

	@Test
	public void rareLogSurvivesSavingAndDamage()
	{
		RareLog log = new RareLog();
		log.record("Woodcutting", "Yew logs", 1730000000000L);
		log.record("Smithing", "Steel; platebody|odd", 1730000001000L);
		RareLog back = RareLog.load(log.save());
		assertEquals(2, back.total());
		assertEquals(1, back.count("Smithing"));
		assertEquals("Odd characters can't break the line", "Steel  platebody odd", back.recent().get(0).item);
		assertEquals(1730000001000L, back.recent().get(0).time);
		// Damaged or missing text never throws.
		assertEquals(0, RareLog.load(null).total());
		assertEquals(0, RareLog.load("").total());
		assertEquals(3, RareLog.load("Mining:3,Bad:x,:4;nope|Mining").total());
	}

	@Test
	public void smeltingCountsAsSmithingAndAlchingAsMagic()
	{
		assertEquals("Smithing", Activity.SMELTING.skillName());
		assertEquals("Magic", Activity.ALCHING.skillName());
		assertEquals("Woodcutting", Activity.WOODCUTTING.skillName());
		java.util.Set<String> skills = new HashSet<>();
		for (Activity a : Activity.values())
		{
			skills.add(a.skillName());
		}
		assertEquals("Every skill with a rare moment, counted once", RareLog.SKILLS, skills.size());
	}

	@Test
	public void ripplesAndGlowsKeepTheirColour()
	{
		Particles p = new Particles(2);
		p.single(Particles.Kind.GLOW, 0, 0, 60, 0xFF8A2A, 100, 0.5);
		assertEquals(1, p.getLive().size());
		assertEquals(0xFF8A2A, p.getLive().get(0).rgb);
		// Updates step at most 0.1 s at a time, so live through it in steps.
		for (int i = 0; i < 7; i++)
		{
			p.update(0.1);
		}
		assertTrue("Gone after its life", p.getLive().isEmpty());
	}

	// ---- Particles ------------------------------------------------------------------------------

	@Test
	public void burstsScaleWithTheAmountSetting()
	{
		Particles p = new Particles(1);
		p.burst(Particles.Kind.SPARK, 0, 0, 50, 10, 0.5);
		assertEquals(5, p.getLive().size());
		p.burst(Particles.Kind.SPARK, 0, 0, 50, 10, 2.0);
		assertEquals(25, p.getLive().size());
	}

	@Test
	public void particlesNeverPassTheCap()
	{
		Particles p = new Particles(1);
		for (int i = 0; i < 100; i++)
		{
			p.burst(Particles.Kind.CHIP, 0, 0, 50, 10, 1.0);
		}
		assertEquals(Particles.MAX, p.getLive().size());
	}

	@Test
	public void particlesDieAtTheEndOfTheirLife()
	{
		Particles p = new Particles(1);
		p.burst(Particles.Kind.DUST, 0, 0, 50, 4, 1.0);
		for (int i = 0; i < 30; i++)
		{
			p.update(0.1);
		}
		assertTrue(p.getLive().isEmpty());
	}

	@Test
	public void fallingThingsStopOnTheGround()
	{
		Particles p = new Particles(3);
		p.burst(Particles.Kind.CHIP, 0, 0, 10, 1, 1.0);
		for (int i = 0; i < 8; i++)
		{
			p.update(0.1);
			for (Particles.Particle x : p.getLive())
			{
				assertTrue(x.z >= 0);
			}
		}
	}

	@Test
	public void aLongPauseDoesNotTeleportParticles()
	{
		Particles p = new Particles(4);
		p.burst(Particles.Kind.EMBER, 0, 0, 0, 1, 1.0);
		Particles.Particle ember = p.getLive().get(0);
		p.update(30);
		assertTrue("A minimised client must not jump particles far", ember.age <= 0.1 + EPS);
	}

	// ---- Settings -------------------------------------------------------------------------------

	@Test
	public void everythingIsOnByDefault()
	{
		ImmersiveSkillingConfig c = new ImmersiveSkillingConfig()
		{
		};
		assertTrue(c.enabled() && c.logPile() && c.woodChips() && c.catchBasket() && c.splashes() && c.oreChunks()
			&& c.dustSparks() && c.hotMetal() && c.forgeSparks() && c.fletchedItem() && c.shavings() && c.itemPops() && c.showPanel());
		assertTrue("1.1 skills are on too", c.tossedLog() && c.fireEmbers() && c.runeBurst() && c.runeGlints() && c.lootFlight()
			&& c.lootGlints() && c.herbItem() && c.bubbles());
		assertTrue("The newest skills are on too", c.craftedItem() && c.craftGlints() && c.offeredBone() && c.prayerLight()
			&& c.alchFlash() && c.alchCoins() && c.harvestPile() && c.soilPuffs() && c.huntCatch() && c.huntPuffs()
			&& c.landingDust() && c.agilityPops() && c.plankFlight() && c.buildDust());
		assertEquals(ImmersiveSkillingConfig.PileSize.MEDIUM, c.pileSize());
		assertEquals(6, c.pileSize().getCount());
		assertEquals(ImmersiveSkillingConfig.Amount.NORMAL, c.amount());
		assertEquals(ImmersiveSkillingConfig.ClearWhen.WALK_AWAY, c.clearWhen());
	}

	// ---- Bags, boats and bug reports ------------------------------------------------------------

	@Test
	public void bagsAreRecognisedPerSkill()
	{
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.WOODCUTTING, "Log basket"));
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.WOODCUTTING, "Forestry basket"));
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.FISHING, "Open fish barrel"));
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.FISHING, "Fish sack"));
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.MINING, "Open coal bag"));
		assertTrue(ImmersiveSkillingPlugin.isBag(Skill.MINING, "Gem bag"));
		assertFalse("A barrel isn't a woodcutting bag", ImmersiveSkillingPlugin.isBag(Skill.WOODCUTTING, "Fish barrel"));
		assertFalse(ImmersiveSkillingPlugin.isBag(Skill.MINING, "Rune pickaxe"));
		assertFalse(ImmersiveSkillingPlugin.isBag(Skill.FISHING, null));
	}

	@Test
	public void bagTipsNameTheBag()
	{
		assertTrue(ImmersiveSkillingPlugin.bagHint(Skill.WOODCUTTING).contains("basket"));
		assertTrue(ImmersiveSkillingPlugin.bagHint(Skill.FISHING).contains("barrel"));
		assertTrue(ImmersiveSkillingPlugin.bagHint(Skill.MINING).contains("bag"));
	}

	@Test
	public void versionMatchesTheHubProperties() throws java.io.IOException
	{
		String props = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("runelite-plugin.properties")),
			java.nio.charset.StandardCharsets.UTF_8);
		assertTrue("Bump ImmersiveSkillingPlugin.VERSION with the release",
			props.contains("version=" + ImmersiveSkillingPlugin.VERSION));
	}

	@Test
	public void bugLinkCarriesTheVersion()
	{
		String link = ImmersiveSkillingPanel.bugLink();
		assertTrue(link.startsWith("https://github.com/AoceanP/immersive-skilling/issues/new?labels=bug&body="));
		assertTrue(link.contains("Plugin%20version%3A%20" + ImmersiveSkillingPlugin.VERSION + "%0A"));
		assertFalse("No raw spaces or new lines in a link", link.contains(" ") || link.contains("\n"));
		assertEquals("a%20b%0Ac%3A%28d%29", ImmersiveSkillingPanel.encode("a b\nc:(d)"));
	}

	@Test
	public void aMapLoadKeepsXpAndInventory() throws java.io.IOException
	{
		// Forgetting them on LOADING made the first item after every region load play nothing.
		String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
			"src/main/java/com/immersiveskilling/ImmersiveSkillingPlugin.java")), java.nio.charset.StandardCharsets.UTF_8);
		int start = src.indexOf("public void onGameStateChanged(");
		String body = src.substring(start, src.indexOf("public void onStatChanged(", start));
		int loading = body.indexOf("GameState.LOADING");
		assertTrue(loading > 0 && body.indexOf("return;", loading) < body.indexOf("resetState()"));
	}

	@Test
	public void effectsFollowYourWorldview() throws java.io.IOException
	{
		// On a Sailing boat you stand on the boat's worldview; the main one put effects out at sea.
		for (String file : new String[]{"ImmersiveSkillingPlugin.java", "Props.java", "EffectsOverlay.java"})
		{
			String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
				"src/main/java/com/immersiveskilling/" + file)), java.nio.charset.StandardCharsets.UTF_8);
			int uses = src.split("getTopLevelWorldView\\(", -1).length - 1;
			assertTrue(file + " places effects in the main world", uses <= (file.startsWith("Immersive") ? 1 : 0));
		}
	}

	@Test
	public void sailingIsToldApartByWhatLeftWithTheItem()
	{
		assertEquals("salvage", Activity.sailing("Small salvage", Collections.emptyList(), false));
		assertEquals("salvage", Activity.sailing("Fremennik salvage", null, false));
		assertEquals("Sorting gives almost anything", "sort",
			Activity.sailing("Rusty coin", Arrays.asList("Large salvage"), false));
		assertEquals("deliver", Activity.sailing("Crate of fish (Port Sarim)", Collections.emptyList(), true));
		assertNull("Picking crates up gives no XP and isn't a delivery",
			Activity.sailing("Crate of fish (Port Sarim)", Collections.emptyList(), false));
		assertNull("Anything else with Sailing XP is left alone", Activity.sailing("Coins", Arrays.asList("Shark"), false));
		assertNull(Activity.sailing(null, null, false));
		assertTrue("Sorting waits for the tick's gear swaps", ImmersiveSkillingPlugin.takesAnyItem(Skill.SAILING));
	}

	@Test
	public void deliveredCratesCountDown()
	{
		assertEquals("-3 Crate of rum (The Pandemonium)",
			ImmersiveSkillingPlugin.popText(Activity.SAILING, 3, "Crate of rum (The Pandemonium)"));
		assertEquals("+1 Small salvage", ImmersiveSkillingPlugin.popText(Activity.SAILING, 1, "Small salvage"));
	}

	@Test
	public void minigameHandInsKnowWhereTheyGo()
	{
		assertEquals("hopper", Activity.depositTarget("Pay-dirt"));
		assertEquals("conveyor belt", Activity.depositTarget("Runite ore"));
		assertEquals("conveyor belt", Activity.depositTarget("Coal"));
		assertEquals("ammunition crate", Activity.depositTarget("Raw harpoonfish"));
		assertEquals("ammunition crate", Activity.depositTarget("Harpoonfish"));
		assertNull("A dropped log isn't a hand-in", Activity.depositTarget("Yew logs"));
		assertNull(Activity.depositTarget(null));
	}

	@Test
	public void theSackGivesOreNuggetsAndGems()
	{
		assertTrue(Activity.fromSack("Mithril ore"));
		assertTrue(Activity.fromSack("Coal"));
		assertTrue(Activity.fromSack("Golden nugget"));
		assertTrue(Activity.fromSack("Uncut sapphire"));
		assertFalse("Pay-dirt goes in, it doesn't come out", Activity.fromSack("Pay-dirt"));
		assertFalse(Activity.fromSack(null));
	}

	@Test
	public void wintertodtIsAlreadyCovered()
	{
		assertEquals(Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Bruma root", false, false));
		assertEquals(Activity.FLETCHING, Activity.classify(Skill.FLETCHING, "Bruma kindling", false, false));
		assertEquals(Activity.FIREMAKING, Activity.classifyUsed(Skill.FIREMAKING, "Bruma root"));
		assertEquals(Activity.FIREMAKING, Activity.classifyUsed(Skill.FIREMAKING, "Bruma kindling"));
		assertEquals("Blast Furnace bars count as smelting once the dispenser is the furnace", Activity.SMELTING,
			Activity.classify(Skill.SMITHING, "Steel bar", true, false));
	}

	@Test
	public void levelUpsAddToTheClassicFireworksAndAreOffByDefault()
	{
		ImmersiveSkillingConfig config = new ImmersiveSkillingConfig() { };
		assertFalse("Level-up moments only add to the game's fireworks, and only when asked", config.levelUpMoments());
		assertEquals("Woodcutting level 70", ImmersiveSkillingPlugin.levelUpText(Skill.WOODCUTTING, 70));
	}

	@Test
	public void pilesKeepTheirOldBehaviourUnlessAsked()
	{
		ImmersiveSkillingConfig config = new ImmersiveSkillingConfig() { };
		assertEquals(ImmersiveSkillingConfig.PileFade.NEVER, config.pileFade());
		assertEquals(0, ImmersiveSkillingConfig.PileFade.NEVER.getMinutes());
		assertEquals(5, ImmersiveSkillingConfig.PileFade.FIVE.getMinutes());
		assertEquals("23 Yew logs", ImmersiveSkillingPlugin.pileText(23, "Yew logs"));
		assertEquals("4 here", ImmersiveSkillingPlugin.pileText(4, ""));
	}

	@Test
	public void gauntletResourcesAreFoundByTheirActions()
	{
		assertTrue("Phren roots are cut", ImmersiveSkillingPlugin.hasAction(new String[]{"Cut", null, "Examine"},
			ImmersiveSkillingPlugin.actionsFor(Activity.WOODCUTTING)));
		assertTrue("Deposits are mined", ImmersiveSkillingPlugin.hasAction(new String[]{"Mine"},
			ImmersiveSkillingPlugin.actionsFor(Activity.MINING)));
		assertNull("Fishing spots are found another way", ImmersiveSkillingPlugin.actionsFor(Activity.FISHING));
		assertEquals("Phren bark", Activity.WOODCUTTING, Activity.classify(Skill.WOODCUTTING, "Phren bark", false, false));
		assertEquals(Activity.MINING, Activity.classify(Skill.MINING, "Corrupted ore", false, false));
	}

	@Test
	public void cookingShowsWhatYouCookedButNotWhatBurnt()
	{
		assertEquals(Activity.COOKING, Activity.classify(Skill.COOKING, "Shark", false, false));
		assertEquals(Activity.COOKING, Activity.classify(Skill.COOKING, "Jug of wine", false, false));
		assertNull("Burnt food gives no XP and is shown as smoke instead",
			Activity.classify(Skill.COOKING, "Burnt shark", false, false));
		assertTrue(Activity.isBurnt("Burnt shark"));
		assertFalse(Activity.isBurnt("Shark"));
		assertFalse(Activity.isBurnt(null));
		assertTrue("Cooking waits for the tick's gear swaps", ImmersiveSkillingPlugin.takesAnyItem(Skill.COOKING));
		assertEquals("+1 Shark", ImmersiveSkillingPlugin.popText(Activity.COOKING, 1, "Shark"));
		ImmersiveSkillingConfig config = new ImmersiveSkillingConfig() { };
		assertFalse("Cooking is off by default so it never doubles up with other cooking plugins",
			config.cookSteam() || config.cookedItem() || config.burntSmoke());
	}
}
