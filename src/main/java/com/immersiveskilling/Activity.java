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

import java.util.Locale;
import net.runelite.api.Skill;

/**
 * WHAT: The skilling activities the plugin dresses up, and how each one is recognised.
 * WHY: Effects are chosen from what the game already told you this tick: which skill gave XP,
 * which item arrived in your inventory, and whether you stand by a furnace or anvil.
 * PRE: Nothing here touches the game; callers pass plain values.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
enum Activity
{
	WOODCUTTING("Woodcutting"),
	FISHING("Fishing"),
	MINING("Mining"),
	SMELTING("Smelting"),
	SMITHING("Smithing"),
	FLETCHING("Fletching"),
	FIREMAKING("Firemaking"),
	RUNECRAFT("Runecraft"),
	THIEVING("Thieving"),
	HERBLORE("Herblore"),
	CRAFTING("Crafting"),
	PRAYER("Prayer"),
	ALCHING("Alching");

	/** Fish that don't start with "raw": eels, minnows and the aerial fishing catches. */
	private static final java.util.Set<String> OTHER_CATCHES = new java.util.HashSet<>(java.util.Arrays.asList(
		"infernal eel", "sacred eel", "minnow", "bluegill", "common tench", "mottled eel", "greater siren"));
	/** Mining finds that aren't ore: Motherlode pay-dirt, amethyst and Camdozaal shards. */
	private static final java.util.Set<String> OTHER_ROCKS = new java.util.HashSet<>(java.util.Arrays.asList(
		"pay-dirt", "amethyst", "barronite shards", "barronite deposit", "calcified deposit"));

	private final String displayName;

	Activity(String displayName)
	{
		this.displayName = displayName;
	}

	String getDisplayName()
	{
		return displayName;
	}

	/**
	 * WHAT: Works out which activity made an item: the skill that gave XP in the same tick, the
	 * item's name and what you're standing next to.
	 * WHY: XP alone isn't enough (Smithing covers both the furnace and the anvil), and an item
	 * alone isn't either (logs can come from a bank or a trade).
	 * PRE: skill is the skill that gained XP this tick, or null; itemName may be null.
	 * POST: Nothing changes.
	 * RETURN: the activity, or null if this isn't one the plugin shows
	 * THROWS: Nothing.
	 */
	static Activity classify(Skill skill, String itemName, boolean nearFurnace, boolean nearAnvil)
	{
		if (skill == null || itemName == null)
		{
			return null;
		}
		String name = itemName.toLowerCase(Locale.ROOT);
		switch (skill)
		{
			case WOODCUTTING:
				return name.endsWith("logs") || name.equals("logs") || name.contains("log") ? WOODCUTTING : null;
			case FISHING:
				return name.startsWith("raw ") || name.contains("fish") || name.contains("leaping") || OTHER_CATCHES.contains(name)
					? FISHING : null;
			case MINING:
				return name.endsWith(" ore") || name.equals("coal") || name.startsWith("uncut ") || name.contains("essence")
					|| name.equals("clay") || name.contains("granite") || name.contains("sandstone") || OTHER_ROCKS.contains(name)
					? MINING : null;
			case SMITHING:
				if (name.endsWith(" bar") && nearFurnace)
				{
					return SMELTING;
				}
				return nearAnvil && !name.endsWith(" bar") ? SMITHING : null;
			case FLETCHING:
				return FLETCHING;
			case RUNECRAFT:
				return name.endsWith(" rune") ? RUNECRAFT : null;
			case THIEVING:
				// Coins, seeds, stall goods: whatever you stole.
				return THIEVING;
			case HERBLORE:
				return HERBLORE;
			case CRAFTING:
				return CRAFTING;
			case MAGIC:
				// Only alching pays out coins for Magic XP; other spells are left alone.
				return name.equals("coins") ? ALCHING : null;
			default:
				return null;
		}
	}

	/**
	 * WHAT: Works out which activity used up an item: Firemaking burns logs and roots, Prayer
	 * buries or offers bones and scatters ashes, all without giving you anything back.
	 * WHY: Every other activity is recognised by what arrives; these only by what leaves.
	 * PRE: skill is the skill that gained XP this tick, or null; itemName may be null.
	 * POST: Nothing changes.
	 * RETURN: FIREMAKING for a burnt log, root or kindling, PRAYER for bones or ashes,
	 * otherwise null
	 * THROWS: Nothing.
	 */
	static Activity classifyUsed(Skill skill, String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		String name = itemName.toLowerCase(Locale.ROOT);
		if (skill == Skill.FIREMAKING)
		{
			return name.contains("log") || name.contains("root") || name.contains("kindling") ? FIREMAKING : null;
		}
		if (skill == Skill.PRAYER)
		{
			return name.contains("bones") || name.endsWith("ashes") ? PRAYER : null;
		}
		return null;
	}

	/**
	 * WHAT: Whether the activity builds a pile beside you.
	 * WHY: Only gathering piles up; the rest play and fade.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: true for woodcutting, fishing and mining
	 * THROWS: Nothing.
	 */
	boolean isGathering()
	{
		return this == WOODCUTTING || this == FISHING || this == MINING;
	}

	/**
	 * WHAT: Whether the product glows hot when it's made.
	 * WHY: Only metalwork comes out red-hot and cools; logs and fish don't.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: true for smelting and smithing
	 * THROWS: Nothing.
	 */
	boolean isHot()
	{
		return this == SMELTING || this == SMITHING;
	}
}
