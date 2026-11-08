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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * WHAT: Small particles in the world: wood chips, sparks, dust, splash droplets, embers,
 * shavings, stone chips, falling leaves, ripples and glows. Each has a position in the game's local units plus a height, a velocity, gravity,
 * and a lifetime.
 * WHY: The overlay draws these every frame, so effects move smoothly at any frame rate. Only the
 * client thread touches them (events and overlay rendering both run there).
 * PRE: Units: x and y in local units (128 per tile), z is height above the ground, time in seconds.
 * POST: Particles age and are removed when their life ends.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class Particles
{
	/** A hard cap, so an effect can never pile up thousands of particles. */
	static final int MAX = 400;

	enum Kind
	{
		CHIP, SPARK, DUST, DROPLET, EMBER, SHAVING, STONE, FALLING_LEAF, RIPPLE, GLOW
	}

	static final class Particle
	{
		final Kind kind;
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		final double gravity;
		final double life;
		double age;
		// The colour of a ripple or glow, as 0xRRGGBB.
		int rgb;
		final double spin;
		final float size;

		Particle(Kind kind, double x, double y, double z, double vx, double vy, double vz, double gravity, double life,
			double spin, float size)
		{
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.gravity = gravity;
			this.life = life;
			this.spin = spin;
			this.size = size;
		}

		/** How far through its life, 0..1. */
		double progress()
		{
			return Motion.clamp(age / life);
		}
	}

	private final List<Particle> live = new ArrayList<>();
	private final Random random;

	Particles(long seed)
	{
		random = new Random(seed);
	}

	List<Particle> getLive()
	{
		return live;
	}

	/**
	 * WHAT: Moves every particle on by dt seconds and drops the ones whose life is over.
	 * WHY: Called once per frame by the overlay, so motion follows real time, not frame count.
	 * PRE: dt >= 0; very long gaps (minimised client) are capped so nothing jumps.
	 * POST: Positions and ages are updated; dead particles are gone.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void update(double dt)
	{
		double step = Math.min(dt, 0.1);
		Iterator<Particle> it = live.iterator();
		while (it.hasNext())
		{
			Particle p = it.next();
			p.age += step;
			if (p.age >= p.life)
			{
				it.remove();
				continue;
			}
			p.vz -= p.gravity * step;
			p.x += p.vx * step;
			p.y += p.vy * step;
			p.z += p.vz * step;
			if (p.z < 0 && p.gravity > 0)
			{
				// Things that fall stop on the ground and slide a little.
				p.z = 0;
				p.vz = 0;
				p.vx *= 0.6;
				p.vy *= 0.6;
			}
		}
	}

	void clear()
	{
		live.clear();
	}

	/**
	 * WHAT: A burst of n particles of one kind from a point, spraying up and outward.
	 * WHY: One call per chop, hit, catch or hammer strike.
	 * PRE: amount scales the count (0.5 subtle, 1 normal, 2 lots).
	 * POST: New particles are added, up to MAX.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	/**
	 * WHAT: Adds one still particle with its own colour and size: a ripple ring or a glow.
	 * WHY: Ripples on the water and flashes of light at a furnace or anvil aren't sprays;
	 * each is one shape that grows and fades where it is.
	 * PRE: rgb is 0xRRGGBB; size is the radius in local units; life is in seconds.
	 * POST: One particle is added, unless the limit is reached.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void single(Kind kind, double x, double y, double z, int rgb, float size, double life)
	{
		if (live.size() >= MAX)
		{
			return;
		}
		Particle p = new Particle(kind, x, y, z, 0, 0, 0, 0, life, 0, size);
		p.rgb = rgb;
		live.add(p);
	}

	void burst(Kind kind, double x, double y, double z, int n, double amount)
	{
		int count = (int) Math.round(n * amount);
		for (int i = 0; i < count && live.size() < MAX; i++)
		{
			double angle = random.nextDouble() * Math.PI * 2;
			double speed;
			double up;
			double gravity;
			double life;
			float size;
			switch (kind)
			{
				case CHIP:
					speed = 90 + random.nextDouble() * 120;
					up = 140 + random.nextDouble() * 120;
					gravity = 620;
					life = 0.9 + random.nextDouble() * 0.5;
					size = 3f;
					break;
				case SPARK:
					speed = 110 + random.nextDouble() * 160;
					up = 120 + random.nextDouble() * 200;
					gravity = 520;
					life = 0.35 + random.nextDouble() * 0.35;
					size = 1.6f;
					break;
				case DUST:
					speed = 20 + random.nextDouble() * 40;
					up = 30 + random.nextDouble() * 40;
					gravity = 0;
					life = 0.8 + random.nextDouble() * 0.5;
					size = 9f;
					break;
				case DROPLET:
					speed = 40 + random.nextDouble() * 80;
					up = 180 + random.nextDouble() * 120;
					gravity = 700;
					life = 0.6 + random.nextDouble() * 0.3;
					size = 2f;
					break;
				case EMBER:
					speed = 8 + random.nextDouble() * 16;
					up = 50 + random.nextDouble() * 50;
					gravity = 0;
					life = 1.2 + random.nextDouble() * 0.8;
					size = 1.8f;
					break;
				case STONE:
					// Rock chips: heavier and greyer than wood chips.
					speed = 60 + random.nextDouble() * 90;
					up = 110 + random.nextDouble() * 90;
					gravity = 700;
					life = 0.7 + random.nextDouble() * 0.3;
					size = 2.5f;
					break;
				case FALLING_LEAF:
					// Shaken loose from the canopy, drifting down slowly.
					speed = 10 + random.nextDouble() * 18;
					up = 5 + random.nextDouble() * 20;
					gravity = 90;
					life = 2.2 + random.nextDouble() * 0.6;
					size = 3f;
					break;
				default:
					speed = 20 + random.nextDouble() * 30;
					up = 20 + random.nextDouble() * 30;
					gravity = 300;
					life = 1.0 + random.nextDouble() * 0.5;
					size = 3f;
					break;
			}
			live.add(new Particle(kind, x, y, z, Math.cos(angle) * speed, Math.sin(angle) * speed, up, gravity, life,
				(random.nextDouble() - 0.5) * 14, size));
		}
	}
}
