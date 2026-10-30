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
 * WHAT: Easing and path maths for the animations: smooth starts and stops, arcs and bounces.
 * WHY: Every prop and particle moves on a curve, so motion looks natural at any frame rate.
 * PRE: Inputs are plain numbers; nothing here touches the game.
 * POST: Nothing changes.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class Motion
{
	private Motion()
	{
	}

	/**
	 * WHAT: Limits a value to 0..1.
	 * WHY: Animation progress must never overshoot its start or end.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: the value, clamped
	 * THROWS: Nothing.
	 */
	static double clamp(double x)
	{
		return Math.max(0, Math.min(1, x));
	}

	/**
	 * WHAT: Smoothstep: starts slowly, speeds up, then slows into the end.
	 * WHY: The default motion for anything sliding or growing.
	 * PRE: None (clamped).
	 * POST: Nothing changes.
	 * RETURN: 0..1
	 * THROWS: Nothing.
	 */
	static double smooth(double x)
	{
		x = clamp(x);
		return x * x * (3 - 2 * x);
	}

	/**
	 * WHAT: Ease-out: fast at first, slowing to a stop.
	 * WHY: For bursts of sparks and chips, which fly fast then lose speed.
	 * PRE: None (clamped).
	 * POST: Nothing changes.
	 * RETURN: 0..1
	 * THROWS: Nothing.
	 */
	static double easeOut(double x)
	{
		x = clamp(x);
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	/**
	 * WHAT: A landing curve: falls in, then bounces twice and settles at 1.
	 * WHY: Ore chunks and logs hitting the ground.
	 * PRE: None (clamped).
	 * POST: Nothing changes.
	 * RETURN: about 0..1, dipping below 1 during the bounces
	 * THROWS: Nothing.
	 */
	static double bounce(double x)
	{
		x = clamp(x);
		if (x < 0.6)
		{
			double u = x / 0.6;
			return u * u;
		}
		if (x < 0.85)
		{
			double u = (x - 0.6) / 0.25;
			return 1 - 0.18 * Math.sin(u * Math.PI);
		}
		double u = (x - 0.85) / 0.15;
		return 1 - 0.05 * Math.sin(u * Math.PI);
	}

	/**
	 * WHAT: The height of a thrown object along its flight, peaking in the middle.
	 * WHY: Gives every flying prop a natural arc.
	 * PRE: peak is the highest point, in local units.
	 * POST: Nothing changes.
	 * RETURN: the height at progress u (0 at both ends)
	 * THROWS: Nothing.
	 */
	static double arcHeight(double u, double peak)
	{
		u = clamp(u);
		return 4 * peak * u * (1 - u);
	}

	/**
	 * WHAT: Linear interpolation between two values.
	 * WHY: Positions along a path.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: a + (b - a) * u
	 * THROWS: Nothing.
	 */
	static double lerp(double a, double b, double u)
	{
		return a + (b - a) * u;
	}
}
