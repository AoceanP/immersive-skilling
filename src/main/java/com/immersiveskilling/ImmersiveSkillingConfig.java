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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * WHAT: The plugin's settings: one section per skill, plus shared options.
 * WHY: Every effect can be turned off on its own. The sidebar panel edits these same settings.
 * PRE: None.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
@ConfigGroup(ImmersiveSkillingConfig.GROUP)
public interface ImmersiveSkillingConfig extends Config
{
	String GROUP = "immersiveskilling";

	enum PileSize
	{
		SMALL("3", 3), MEDIUM("6", 6), LARGE("10", 10);

		private final String label;
		private final int count;

		PileSize(String label, int count)
		{
			this.label = label;
			this.count = count;
		}

		public int getCount()
		{
			return count;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum Amount
	{
		SUBTLE("Subtle", 0.5), NORMAL("Normal", 1.0), LOTS("Lots", 1.8);

		private final String label;
		private final double scale;

		Amount(String label, double scale)
		{
			this.label = label;
			this.scale = scale;
		}

		public double getScale()
		{
			return scale;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum ClearWhen
	{
		WALK_AWAY("Walk away"), BANK("Bank"), LOGOUT("Logout");

		private final String label;

		ClearWhen(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(name = "Woodcutting", description = "Log pile and wood chips", position = 0)
	String woodcuttingSection = "woodcutting";

	@ConfigSection(name = "Fishing", description = "Catch basket and splashes", position = 1)
	String fishingSection = "fishing";

	@ConfigSection(name = "Mining", description = "Ore chunks, dust and sparks", position = 2)
	String miningSection = "mining";

	@ConfigSection(name = "Smelting and Smithing", description = "Hot bars and items that cool, sparks and embers", position = 3)
	String metalSection = "metal";

	@ConfigSection(name = "Fletching", description = "The finished item and shavings", position = 4)
	String fletchingSection = "fletching";

	@ConfigSection(name = "General", description = "Shared options", position = 5)
	String generalSection = "general";

	@ConfigItem(keyName = "enabled", name = "Effects on", description = "Turn every effect on or off at once", position = 0)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(keyName = "logPile", name = "Log pile", description = "Logs pile up beside you as you chop", section = woodcuttingSection, position = 1)
	default boolean logPile()
	{
		return true;
	}

	@ConfigItem(keyName = "woodChips", name = "Wood chips", description = "Chips fly off the tree as you chop", section = woodcuttingSection, position = 2)
	default boolean woodChips()
	{
		return true;
	}

	@ConfigItem(keyName = "pileSize", name = "Pile size", description = "The most items a pile holds", section = woodcuttingSection, position = 3)
	default PileSize pileSize()
	{
		return PileSize.MEDIUM;
	}

	@ConfigItem(keyName = "catchBasket", name = "Catch basket", description = "Your catches fly into a basket beside you", section = fishingSection, position = 4)
	default boolean catchBasket()
	{
		return true;
	}

	@ConfigItem(keyName = "splashes", name = "Splashes", description = "Droplets fly up from the water when you catch something", section = fishingSection, position = 5)
	default boolean splashes()
	{
		return true;
	}

	@ConfigItem(keyName = "oreChunks", name = "Ore chunks", description = "Ore bounces out of the rock and lands beside you", section = miningSection, position = 6)
	default boolean oreChunks()
	{
		return true;
	}

	@ConfigItem(keyName = "dustSparks", name = "Dust and sparks", description = "Dust and sparks while you mine", section = miningSection, position = 7)
	default boolean dustSparks()
	{
		return true;
	}

	@ConfigItem(keyName = "hotMetal", name = "Hot metal", description = "Bars and smithed items appear red-hot and cool down", section = metalSection, position = 8)
	default boolean hotMetal()
	{
		return true;
	}

	@ConfigItem(keyName = "forgeSparks", name = "Sparks and embers", description = "Sparks at the anvil, embers at the furnace", section = metalSection, position = 9)
	default boolean forgeSparks()
	{
		return true;
	}

	@ConfigItem(keyName = "fletchedItem", name = "Finished item", description = "Each finished item appears in front of you for a moment", section = fletchingSection, position = 10)
	default boolean fletchedItem()
	{
		return true;
	}

	@ConfigItem(keyName = "shavings", name = "Shavings", description = "Wood shavings drift down as you fletch", section = fletchingSection, position = 11)
	default boolean shavings()
	{
		return true;
	}

	@ConfigItem(keyName = "itemPops", name = "+1 pops", description = "The item rises with \"+1\" each time you get one", section = generalSection, position = 12)
	default boolean itemPops()
	{
		return true;
	}

	@ConfigItem(keyName = "amount", name = "Effect amount", description = "How many particles each effect makes", section = generalSection, position = 13)
	default Amount amount()
	{
		return Amount.NORMAL;
	}

	@ConfigItem(keyName = "clearWhen", name = "Clear piles when", description = "When piles beside you disappear. They always clear when you leave the area.", section = generalSection, position = 14)
	default ClearWhen clearWhen()
	{
		return ClearWhen.WALK_AWAY;
	}

	@ConfigItem(keyName = "showPanel", name = "Sidebar panel", description = "Show the Immersive Skilling panel in the sidebar", section = generalSection, position = 15)
	default boolean showPanel()
	{
		return true;
	}

	// The last item you got, so a log basket, fish barrel or bag still gets effects.
	@ConfigItem(keyName = "lastWoodcutting", name = "", description = "", hidden = true)
	default int lastWoodcutting()
	{
		return -1;
	}

	// The last item you got, so a log basket, fish barrel or bag still gets effects.
	@ConfigItem(keyName = "lastFishing", name = "", description = "", hidden = true)
	default int lastFishing()
	{
		return -1;
	}

	// The last item you got, so a log basket, fish barrel or bag still gets effects.
	@ConfigItem(keyName = "lastMining", name = "", description = "", hidden = true)
	default int lastMining()
	{
		return -1;
	}
}
