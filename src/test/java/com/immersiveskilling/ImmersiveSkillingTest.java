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
	public void onlyTheFiveSkillsAreWatched()
	{
		int shown = 0;
		for (Skill s : Skill.values())
		{
			shown += ImmersiveSkillingPlugin.isShown(s) ? 1 : 0;
		}
		assertEquals(5, shown);
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
		assertEquals("The middle layer lies across the bottom one", 512, PileLayout.slot(3)[3]);
		assertEquals(0, PileLayout.slot(0)[3]);
	}

	@Test
	public void biggerPilesGrowToTheSide()
	{
		assertEquals(0, PileLayout.slot(6)[2]);
		assertTrue(PileLayout.slot(6)[0] > PileLayout.slot(2)[0]);
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
}
