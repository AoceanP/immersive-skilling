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
	ALCHING("Alching"),
	FARMING("Farming"),
	HUNTER("Hunter"),
	AGILITY("Agility"),
	CONSTRUCTION("Construction"),
	SAILING("Sailing"),
	COOKING("Cooking"),
	SPELLCASTING("Spellcasting");

	/** Ring, necklace, amulet or bracelet as a whole word, so "Bow string" isn't jewellery. */
	private static final java.util.regex.Pattern JEWELLERY = java.util.regex.Pattern.compile(
		".*\\b(ring|necklace|amulet|bracelet)\\b.*");

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
	 * WHAT: The skill this activity trains, for the rare moments log.
	 * WHY: Smelting and Smithing are both Smithing; Alching and the other spells are Magic.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the skill's name
	 * THROWS: Nothing.
	 */
	String skillName()
	{
		return this == SMELTING ? "Smithing" : this == ALCHING || this == SPELLCASTING ? "Magic" : displayName;
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
				// Gauntlet trees give bark and Wintertodt gives roots, not logs.
				return name.contains("log") || name.endsWith(" bark") || name.endsWith(" root") ? WOODCUTTING : null;
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
			case FARMING:
				return FARMING;
			case HUNTER:
				return HUNTER;
			case COOKING:
				// Fish, meat, pies, pizzas, wine: whatever you cooked or made.
				return isBurnt(itemName) ? null : COOKING;
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
	 * CONSTRUCTION for planks, otherwise null
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
		if (skill == Skill.CONSTRUCTION)
		{
			// Nails and cloth go too, but the plank is what you see.
			return name.contains("plank") ? CONSTRUCTION : null;
		}
		return null;
	}

	/**
	 * WHAT: Which part of Sailing an item belongs to: salvage hauled up by your hook, finds
	 * sorted out of salvage at a salvaging station, or courier crates handed over at a ledger table.
	 * WHY: Sailing XP comes with very different items, and sorting can give almost anything, so
	 * the item alone can't say what happened; what left the inventory with it can.
	 * PRE: gained is the item's name (for a delivery, the crate that left); lost holds the names of
	 * what left the inventory in the same change; used is true when nothing arrived.
	 * POST: Nothing changes.
	 * RETURN: "salvage", "sort", "deliver", or null when it isn't one the plugin shows
	 * THROWS: Nothing.
	 */
	static String sailing(String gained, java.util.List<String> lost, boolean used)
	{
		if (gained == null)
		{
			return null;
		}
		String name = gained.toLowerCase(Locale.ROOT);
		if (used)
		{
			return name.startsWith("crate of ") ? "deliver" : null;
		}
		if (name.endsWith(" salvage"))
		{
			return "salvage";
		}
		if (lost != null)
		{
			for (String l : lost)
			{
				if (l != null && l.toLowerCase(Locale.ROOT).endsWith(" salvage"))
				{
					return "sort";
				}
			}
		}
		return null;
	}

	/**
	 * WHAT: What an item is dropped into at a minigame: pay-dirt into the Motherlode hopper, ore
	 * and coal onto the Blast Furnace conveyor belt, harpoonfish into a Tempoross ammunition crate.
	 * WHY: These hand-ins give no XP (or XP with nothing arriving), so they're told apart by the
	 * item that left and the object you stand at.
	 * PRE: itemName may be null.
	 * POST: Nothing changes.
	 * RETURN: the object's name in lower case ("hopper", "conveyor belt", "ammunition crate"), or null
	 * THROWS: Nothing.
	 */
	static String depositTarget(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		String name = itemName.toLowerCase(Locale.ROOT);
		if (name.equals("pay-dirt"))
		{
			return "hopper";
		}
		if (name.endsWith(" ore") || name.equals("coal"))
		{
			return "conveyor belt";
		}
		return name.equals("raw harpoonfish") || name.equals("harpoonfish") ? "ammunition crate" : null;
	}

	/**
	 * WHAT: Whether an item comes out of the Motherlode Mine sack: cleaned ore, coal, golden
	 * nuggets and uncut gems.
	 * WHY: Searching the sack gives no XP, so the haul is told apart by what arrives beside it.
	 * PRE: itemName may be null.
	 * POST: Nothing changes.
	 * RETURN: true for a sack find
	 * THROWS: Nothing.
	 */
	static boolean fromSack(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		String name = itemName.toLowerCase(Locale.ROOT);
		return name.endsWith(" ore") || name.equals("coal") || name.equals("golden nugget") || name.startsWith("uncut ");
	}

	/**
	 * WHAT: Which item-changing spell was cast: Superheat (ore into a bar), Plank Make (logs into
	 * a plank), an enchant on bolts or jewellery, or String Jewellery.
	 * WHY: These all give Magic XP with one item swapped for another, so they're told apart by
	 * what arrived and what left with it. Alching pays coins and is handled on its own.
	 * PRE: gained is the name of what arrived; lost holds the names of what left in the same
	 * change (the item and its runes); either may be null.
	 * POST: Nothing changes.
	 * RETURN: "superheat", "plank", "bolts", "string", "enchant", or null for anything else
	 * THROWS: Nothing.
	 */
	static String spell(String gained, java.util.List<String> lost)
	{
		if (gained == null || lost == null)
		{
			return null;
		}
		String name = gained.toLowerCase(Locale.ROOT);
		if (name.equals("coins"))
		{
			return null;
		}
		for (String l : lost)
		{
			String used = l == null ? "" : l.toLowerCase(Locale.ROOT);
			if (name.endsWith(" bar") && (used.endsWith(" ore") || used.equals("coal")))
			{
				return "superheat";
			}
			if (name.contains("plank") && used.contains("logs"))
			{
				return "plank";
			}
			if (name.endsWith("bolts (e)") && used.endsWith("bolts"))
			{
				return "bolts";
			}
			if (JEWELLERY.matcher(used).matches() && !used.equals(name))
			{
				return used.endsWith(" (u)") ? "string" : "enchant";
			}
		}
		return null;
	}

	/**
	 * WHAT: The colour of the gem named in an item, for an enchant's glow.
	 * WHY: A ruby bolt should flash red and a sapphire ring blue, not every enchant the same.
	 * PRE: names may be null; it can hold several item names together.
	 * POST: Nothing changes.
	 * RETURN: an RGB colour; a pale blue when no gem is named
	 * THROWS: Nothing.
	 */
	static int gemColor(String names)
	{
		String n = names == null ? "" : names.toLowerCase(Locale.ROOT);
		String[] gems = {"sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "zenyte", "opal", "jade", "topaz", "pearl"};
		int[] colours = {0x4A78FF, 0x3CDC78, 0xFF3C50, 0xDCF0FF, 0xC060FF, 0xB4B4C8, 0xFFB040, 0xF0E0FF, 0x60D080, 0xFF7840, 0xF5F0E6};
		for (int i = 0; i < gems.length; i++)
		{
			if (n.contains(gems[i]))
			{
				return colours[i];
			}
		}
		return 0x9EC8FF;
	}

	/**
	 * WHAT: Whether an item is burnt food.
	 * WHY: Burning food gives no XP, so it's told apart by its name, beside a range or fire.
	 * PRE: itemName may be null.
	 * POST: Nothing changes.
	 * RETURN: true for "Burnt ..." items
	 * THROWS: Nothing.
	 */
	static boolean isBurnt(String itemName)
	{
		return itemName != null && itemName.toLowerCase(Locale.ROOT).startsWith("burnt ");
	}

	/**
	 * WHAT: Whether the activity builds a pile beside you.
	 * WHY: Only gathering piles up; the rest play and fade.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: true for woodcutting, fishing, mining and farming harvests
	 * THROWS: Nothing.
	 */
	boolean isGathering()
	{
		return this == WOODCUTTING || this == FISHING || this == MINING || this == FARMING;
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
