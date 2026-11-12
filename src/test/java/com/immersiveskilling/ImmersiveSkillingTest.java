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
		assertNull(Activity.classify(Skill.COOKING, "Lobster", false, false));
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
	public void popsShowHowMany()
	{
		assertEquals("+15 Arrow shaft", ImmersiveSkillingPlugin.popText(Activity.FLETCHING, 15, "Arrow shaft"));
		assertEquals("+1 Yew logs", ImmersiveSkillingPlugin.popText(Activity.WOODCUTTING, 1, "Yew logs"));
	}

	@Test
	public void onlyTheFiveSkillsAreWatched()
	{
		int shown = 0;
		for (Skill s : Skill.values())
		{
			shown += ImmersiveSkillingPlugin.isShown(s) ? 1 : 0;
		}
		// Cooking is left to Immersive Cooking; combat skills are never touched.
		assertEquals(5, shown);
		assertFalse(ImmersiveSkillingPlugin.isShown(Skill.ATTACK) || ImmersiveSkillingPlugin.isShown(Skill.SLAYER));
		assertFalse(ImmersiveSkillingPlugin.isShown(Skill.COOKING));
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
}
