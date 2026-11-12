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

/**
 * WHAT: Turns a model colour red-hot, by an amount from 0 (as it is) to 1 (glowing).
 * WHY: Smelted bars and smithed items come out glowing and cool over a few seconds. The game
 * stores model colours as 16-bit HSL: 6 bits hue, 3 bits saturation, 7 bits lightness.
 * PRE: Colours are the game's packed HSL shorts.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class HeatColor
{
	/** Orange in the game's 64-step hue wheel (0 is red). */
	static final int HOT_HUE = 5;
	static final int HOT_SATURATION = 7;
	/** Hot metal is bright: lightness never drops below this when fully hot. */
	static final int HOT_MIN_LIGHTNESS = 70;
	/** Gold in the game's hue wheel, a little past orange towards yellow. */
	static final int GOLD_HUE = 8;
	static final int GOLD_SATURATION = 6;

	private HeatColor()
	{
	}

	static int hue(int hsl)
	{
		return (hsl >> 10) & 0x3F;
	}

	static int saturation(int hsl)
	{
		return (hsl >> 7) & 0x7;
	}

	static int lightness(int hsl)
	{
		return hsl & 0x7F;
	}

	static short pack(int hue, int saturation, int lightness)
	{
		return (short) (((hue & 0x3F) << 10) | ((saturation & 0x7) << 7) | (lightness & 0x7F));
	}

	/**
	 * WHAT: The colour turned to polished gold, keeping its light and shade.
	 * WHY: A rare moment's item turns golden, a little trophy in your pile.
	 * PRE: color is the game's packed HSL.
	 * POST: Nothing changes.
	 * RETURN: the golden colour
	 * THROWS: Nothing.
	 */
	static short gold(short color)
	{
		// Keep the model's shading, squeezed into a bright band, so it reads as polished gold.
		int light = lightness(color & 0xFFFF);
		int goldLight = Math.max(40, Math.min(118, (int) Math.round(light * 0.6 + 45)));
		return pack(GOLD_HUE, GOLD_SATURATION, goldLight);
	}

	/**
	 * WHAT: The colour heated by an amount: moves hue to orange, saturation to full, and lifts
	 * lightness, blending from the original as heat rises.
	 * WHY: One function for every stage of cooling, so a model can step smoothly from hot to cold.
	 * PRE: heat is 0..1 (clamped).
	 * POST: Nothing changes.
	 * RETURN: the heated colour
	 * THROWS: Nothing.
	 */
	static short heat(short color, double heat)
	{
		double h = Motion.clamp(heat);
		int c = color & 0xFFFF;
		int hue = hue(c);
		int sat = saturation(c);
		int light = lightness(c);
		int hotLight = Math.max(HOT_MIN_LIGHTNESS, Math.min(127, light + 30));
		// Hue blends the short way round to orange, without passing through blue.
		int newHue = (int) Math.round(hue + (HOT_HUE - hue) * h);
		int newSat = (int) Math.round(sat + (HOT_SATURATION - sat) * h);
		int newLight = (int) Math.round(light + (hotLight - light) * h);
		return pack(newHue, newSat, newLight);
	}
}
