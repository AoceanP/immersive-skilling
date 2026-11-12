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

	@ConfigSection(name = "Firemaking", description = "The log tossed onto the fire, and embers", position = 5)
	String firemakingSection = "firemaking";

	@ConfigSection(name = "Runecraft", description = "Runes bursting from the altar, and glints", position = 6)
	String runecraftSection = "runecraft";

	@ConfigSection(name = "Thieving", description = "Loot flying to you, and glints", position = 7)
	String thievingSection = "thieving";

	@ConfigSection(name = "Herblore", description = "The finished potion or herb, bubbles and leaves", position = 8)
	String herbloreSection = "herblore";

	@ConfigSection(name = "Crafting", description = "The finished item, glints and dust", position = 9)
	String craftingSection = "crafting";

	@ConfigSection(name = "Prayer", description = "Bones offered or buried, and rising light", position = 10)
	String prayerSection = "prayer";

	@ConfigSection(name = "Magic", description = "Alching: a green flash and a shower of coins", position = 11)
	String magicSection = "magic";

	@ConfigSection(name = "Farming", description = "A harvest pile beside you, and soil", position = 12)
	String farmingSection = "farming";

	@ConfigSection(name = "Hunter", description = "Your catch flying to you, and puffs", position = 13)
	String hunterSection = "hunter";

	@ConfigSection(name = "Agility", description = "A puff of dust where you land, and the XP", position = 14)
	String agilitySection = "agility";

	@ConfigSection(name = "Construction", description = "The plank flying into place, dust and sparks", position = 15)
	String constructionSection = "construction";

	@ConfigSection(name = "General", description = "Shared options", position = 17)
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

	@ConfigItem(keyName = "itemPops", name = "Item pops", description = "The item rises with how many you got, e.g. \"+15\", each time you get some", section = generalSection, position = 12)
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

	@ConfigItem(keyName = "tossedLog", name = "Log into the fire", description = "Each log arcs from you onto the fire", section = firemakingSection, position = 16)
	default boolean tossedLog()
	{
		return true;
	}

	@ConfigItem(keyName = "fireEmbers", name = "Embers", description = "Embers and sparks rise as the log catches", section = firemakingSection, position = 17)
	default boolean fireEmbers()
	{
		return true;
	}

	@ConfigItem(keyName = "runeBurst", name = "Rune burst", description = "Your new runes burst up out of the altar", section = runecraftSection, position = 18)
	default boolean runeBurst()
	{
		return true;
	}

	@ConfigItem(keyName = "runeGlints", name = "Glints", description = "Glints sparkle around the altar", section = runecraftSection, position = 19)
	default boolean runeGlints()
	{
		return true;
	}

	@ConfigItem(keyName = "lootFlight", name = "Loot flight", description = "What you steal flies from the stall or pocket to you", section = thievingSection, position = 20)
	default boolean lootFlight()
	{
		return true;
	}

	@ConfigItem(keyName = "lootGlints", name = "Glints", description = "Glints sparkle as the loot reaches you", section = thievingSection, position = 21)
	default boolean lootGlints()
	{
		return true;
	}

	@ConfigItem(keyName = "herbItem", name = "Finished item", description = "The potion or clean herb appears in front of you for a moment", section = herbloreSection, position = 22)
	default boolean herbItem()
	{
		return true;
	}

	@ConfigItem(keyName = "bubbles", name = "Bubbles and leaves", description = "Potions bubble; cleaned herbs shed a few leaves", section = herbloreSection, position = 23)
	default boolean bubbles()
	{
		return true;
	}

	@ConfigItem(keyName = "craftedItem", name = "Finished item", description = "Each finished item appears in front of you for a moment", section = craftingSection, position = 24)
	default boolean craftedItem()
	{
		return true;
	}

	@ConfigItem(keyName = "craftGlints", name = "Glints and dust", description = "Gems and jewellery glint; everything else gives a puff of dust", section = craftingSection, position = 25)
	default boolean craftGlints()
	{
		return true;
	}

	@ConfigItem(keyName = "offeredBone", name = "Bone offering", description = "The bone flies onto the altar, or sinks into the ground when you bury it", section = prayerSection, position = 26)
	default boolean offeredBone()
	{
		return true;
	}

	@ConfigItem(keyName = "prayerLight", name = "Rising light", description = "Soft motes of light rise from the altar or the ground", section = prayerSection, position = 27)
	default boolean prayerLight()
	{
		return true;
	}

	@ConfigItem(keyName = "alchFlash", name = "Green flash", description = "The item vanishes above you in a green flash", section = magicSection, position = 28)
	default boolean alchFlash()
	{
		return true;
	}

	@ConfigItem(keyName = "alchCoins", name = "Coin shower", description = "Coins burst out and fall around you", section = magicSection, position = 29)
	default boolean alchCoins()
	{
		return true;
	}

	@ConfigItem(keyName = "harvestPile", name = "Harvest pile", description = "Your harvest pops out of the patch onto a pile beside you", section = farmingSection, position = 30)
	default boolean harvestPile()
	{
		return true;
	}

	@ConfigItem(keyName = "soilPuffs", name = "Soil", description = "Clumps of soil fly up as you harvest", section = farmingSection, position = 31)
	default boolean soilPuffs()
	{
		return true;
	}

	@ConfigItem(keyName = "huntCatch", name = "Catch flight", description = "Your catch flies from the trap or net to you", section = hunterSection, position = 32)
	default boolean huntCatch()
	{
		return true;
	}

	@ConfigItem(keyName = "huntPuffs", name = "Puffs", description = "A puff of dust where the catch came from", section = hunterSection, position = 33)
	default boolean huntPuffs()
	{
		return true;
	}

	@ConfigItem(keyName = "landingDust", name = "Landing dust", description = "A puff of dust where you land after each obstacle", section = agilitySection, position = 34)
	default boolean landingDust()
	{
		return true;
	}

	@ConfigItem(keyName = "agilityPops", name = "XP pops", description = "The XP for each obstacle rises over you, e.g. \"+46 Agility XP\"", section = agilitySection, position = 35)
	default boolean agilityPops()
	{
		return true;
	}

	@ConfigItem(keyName = "plankFlight", name = "Plank flight", description = "The plank flies from you to what you build", section = constructionSection, position = 36)
	default boolean plankFlight()
	{
		return true;
	}

	@ConfigItem(keyName = "buildDust", name = "Dust and sparks", description = "Sawdust and hammer sparks where you build", section = constructionSection, position = 37)
	default boolean buildDust()
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
