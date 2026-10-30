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
 * WHAT: Where each item in a pile sits: a crisscross stack like firewood (3 on the bottom, then
 * 2 turned across them, then 1), and wider for bigger piles.
 * WHY: Logs, fish and ore build up beside you neatly, not in one spot.
 * PRE: Offsets are in the game's local units (128 per tile), around the pile's centre.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class PileLayout
{
	/** Spacing between items in a row, and height of one layer, in local units. */
	static final int SPACING = 26;
	static final int LAYER_HEIGHT = 14;

	private PileLayout()
	{
	}

	/**
	 * WHAT: The position of item number index (0-based) in a pile.
	 * WHY: Each new item lands on the next free spot, bottom layer first.
	 * PRE: index >= 0.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}: offsets from the centre, height above the ground,
	 * and the game's 0-2047 orientation (layers alternate by a quarter turn)
	 * THROWS: Nothing.
	 */
	static int[] slot(int index)
	{
		// Layers hold 3, 2, 1, then repeat a little to the side for piles bigger than 6.
		int group = index / 6;
		int i = index % 6;
		int layer;
		int pos;
		int count;
		if (i < 3)
		{
			layer = 0;
			pos = i;
			count = 3;
		}
		else if (i < 5)
		{
			layer = 1;
			pos = i - 3;
			count = 2;
		}
		else
		{
			layer = 2;
			pos = 0;
			count = 1;
		}
		int offset = (pos * 2 - (count - 1)) * SPACING / 2;
		boolean across = layer % 2 == 1;
		int dx = across ? 0 : offset;
		int dy = across ? offset : 0;
		dx += group * SPACING * 3;
		int orientation = across ? 512 : 0;
		return new int[]{dx, dy, layer * LAYER_HEIGHT, orientation};
	}
}
