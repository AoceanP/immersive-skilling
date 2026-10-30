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
	FLETCHING("Fletching");

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
				return name.startsWith("raw ") || name.contains("fish") || name.contains("leaping") ? FISHING : null;
			case MINING:
				return name.endsWith(" ore") || name.equals("coal") || name.startsWith("uncut ") || name.contains("essence")
					|| name.equals("clay") || name.contains("granite") || name.contains("sandstone") ? MINING : null;
			case SMITHING:
				if (name.endsWith(" bar") && nearFurnace)
				{
					return SMELTING;
				}
				return nearAnvil && !name.endsWith(" bar") ? SMITHING : null;
			case FLETCHING:
				return FLETCHING;
			default:
				return null;
		}
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
