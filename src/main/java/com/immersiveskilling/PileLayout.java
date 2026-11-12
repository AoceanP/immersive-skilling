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

import java.util.function.BiPredicate;

/**
 * WHAT: Where each item in a pile sits, by the kind of pile: logs in a crisscross firewood
 * stack, ore and harvests in a rounded heap, catches nested in the basket. Also picks a free
 * tile beside you for the pile.
 * WHY: One crisscross for everything made ore and potatoes look like logs, and a fixed spot
 * behind you could land the pile in a wall, a tree or the water.
 * PRE: Offsets are in the game's local units (128 per tile), around the pile's centre, with
 * +y pointing the way you faced when the pile started.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class PileLayout
{
	/** Spacing between items in a row, and height of one layer, in local units. */
	static final int SPACING = 26;
	static final int LAYER_HEIGHT = 14;

	/** The kinds of pile. */
	enum Shape
	{
		STACK, HEAP, BASKET
	}

	// The heap's layers: how many items each holds, how far from the middle, how high.
	private static final int[] HEAP_COUNT = {5, 3, 2, 1};
	private static final int[] HEAP_RADIUS = {24, 13, 7, 0};

	private PileLayout()
	{
	}

	/**
	 * WHAT: The position of item number index (0-based) in a medium firewood stack.
	 * WHY: Kept for the stack's original 3, 2, 1 shape.
	 * PRE: index >= 0.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}
	 * THROWS: Nothing.
	 */
	static int[] slot(int index)
	{
		return slot(Shape.STACK, index, 6);
	}

	/**
	 * WHAT: The position of item number index (0-based) in a pile of a shape that holds cap items.
	 * WHY: Each new item lands on the next free spot, bottom layer first, so the pile builds up.
	 * PRE: index >= 0; cap >= 1.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}: offsets from the centre, height above the ground,
	 * and the game's 0-2047 orientation, before turning to the way you face
	 * THROWS: Nothing.
	 */
	static int[] slot(Shape shape, int index, int cap)
	{
		switch (shape)
		{
			case HEAP:
				return heap(index);
			case BASKET:
				return basket(index);
			default:
				return stack(index, cap);
		}
	}

	/**
	 * WHAT: Firewood: rows that shrink by one as they go up (4, 3, 2, 1 for ten logs), each row
	 * turned across the one below.
	 * WHY: A big pile used to grow sideways in a second little stack; now it's one wider stack.
	 * PRE: index >= 0.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}
	 * THROWS: Nothing.
	 */
	private static int[] stack(int index, int cap)
	{
		int base = 2;
		while (base * (base + 1) / 2 < cap)
		{
			base++;
		}
		int layer = 0;
		int count = base;
		int pos = index;
		while (pos >= count && count > 1)
		{
			pos -= count;
			layer++;
			count--;
		}
		// Anything past the top keeps landing on the top log's spot, one layer higher.
		if (pos >= count)
		{
			layer += pos;
			pos = 0;
		}
		int offset = (pos * 2 - (count - 1)) * SPACING / 2;
		boolean across = layer % 2 == 1;
		// A little unevenness, the same every time, so it looks stacked by hand.
		int wobble = index * 37 % 9 - 4;
		int dx = across ? wobble : offset;
		int dy = across ? offset : wobble;
		int orientation = ((across ? 512 : 0) + index * 53 % 41 - 20) & 2047;
		return new int[]{dx, dy, layer * LAYER_HEIGHT, orientation};
	}

	/**
	 * WHAT: A rounded heap: a ring of five on the ground, then three, two and one on top, each
	 * at its own angle.
	 * WHY: Ore and potatoes tumble into a heap; they don't stack like logs.
	 * PRE: index >= 0.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}
	 * THROWS: Nothing.
	 */
	private static int[] heap(int index)
	{
		int layer = 0;
		int pos = index;
		while (layer < HEAP_COUNT.length - 1 && pos >= HEAP_COUNT[layer])
		{
			pos -= HEAP_COUNT[layer];
			layer++;
		}
		double angle = pos * Math.PI * 2 / HEAP_COUNT[layer] + layer * 0.7;
		int r = HEAP_RADIUS[layer];
		int dx = (int) Math.round(Math.cos(angle) * r);
		int dy = (int) Math.round(Math.sin(angle) * r);
		int height = Math.min(layer, HEAP_COUNT.length - 1) * 11 + Math.max(0, pos - (HEAP_COUNT[layer] - 1)) * 11;
		return new int[]{dx, dy, height, index * 733 & 2047};
	}

	/**
	 * WHAT: Catches nested in the basket, round the middle and rising slowly.
	 * WHY: In a basket they sit close together and higher than on the ground.
	 * PRE: index >= 0.
	 * POST: Nothing changes.
	 * RETURN: {dx, dy, height, orientation}
	 * THROWS: Nothing.
	 */
	private static int[] basket(int index)
	{
		double angle = index * 2.39996;
		double r = index == 0 ? 0 : 9 + (index % 3) * 3;
		return new int[]{(int) Math.round(Math.cos(angle) * r), (int) Math.round(Math.sin(angle) * r), 22 + index / 3 * 6,
			index * 733 & 2047};
	}

	/**
	 * WHAT: Picks the tile for a new pile: behind you and to the left first, then the other
	 * tiles around you, skipping any you couldn't walk onto.
	 * WHY: A pile in a wall, a tree or the sea looked broken; one beside and behind you never
	 * blocks your view of the work.
	 * PRE: (px, py) is your tile; (fx, fy) is the way you face, each -1, 0 or 1 and not both 0.
	 * blocked says whether a tile can't be walked on (including off the map).
	 * POST: Nothing changes.
	 * RETURN: {x, y} of the tile, or null if every tile around you is blocked
	 * THROWS: Nothing.
	 */
	static int[] freeTile(int px, int py, int fx, int fy, BiPredicate<Integer, Integer> blocked)
	{
		// Left of the way you face is (-fy, fx); behind is (-fx, -fy).
		int lx = -fy;
		int ly = fx;
		int[][] tries = {
			{-fx + lx, -fy + ly}, {-fx - lx, -fy - ly}, {lx, ly}, {-lx, -ly}, {-fx, -fy}, {fx + lx, fy + ly}, {fx - lx, fy - ly}
		};
		for (int[] d : tries)
		{
			int dx = Integer.signum(d[0]);
			int dy = Integer.signum(d[1]);
			if ((dx == 0 && dy == 0) || (dx == fx && dy == fy))
			{
				// Never on you, and never on the tile you're working on.
				continue;
			}
			if (!blocked.test(px + dx, py + dy))
			{
				return new int[]{px + dx, py + dy};
			}
		}
		return null;
	}
}
