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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

/**
 * WHAT: The 3D props: the game's own item models, placed in the world as client-side objects
 * that only you see. They can fly along an arc, bounce as they land, cool from red-hot, slowly
 * spin, and disappear after a while.
 * WHY: Piles, the catch basket, hot bars and finished items are real models, lit and shaded by
 * the game like everything else.
 * PRE: Every method runs on the client thread (game events or before each frame is drawn).
 * POST: Objects are registered with the client while shown and removed on clear().
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class Props
{
	/** Cooling is shown in this many steps, from fully hot to normal. */
	static final int HEAT_STEPS = 8;

	/** Which group a prop belongs to, so a group can be cleared on its own. */
	enum Group
	{
		PILE, BASKET, FLOATING
	}

	static final class Prop
	{
		final RuneLiteObject object;
		final Group group;
		final int plane;
		// The worldview it lives in: the main world, or a boat's deck so it sails with you.
		final int view;
		// Flight from (fromX, fromY, fromZ) to (toX, toY, toZ), heights above the ground.
		double fromX;
		double fromY;
		double fromZ;
		double toX;
		double toY;
		double toZ;
		double peak;
		double start;
		double duration;
		boolean bounce;
		// Cooling: models per heat step (null when the item isn't hot).
		Model[] heatModels;
		double coolStart;
		double coolDuration;
		int shownStep = -1;
		double spinPerSecond;
		double baseOrientation;
		double expires = Double.MAX_VALUE;

		Prop(RuneLiteObject object, Group group, int plane, int view)
		{
			this.object = object;
			this.group = group;
			this.plane = plane;
			this.view = view;
		}
	}

	private final Client client;
	private final List<Prop> props = new ArrayList<>();
	private final Map<String, Model> modelCache = new HashMap<>();
	private int view = -1;

	Props(Client client)
	{
		this.client = client;
	}

	/**
	 * WHAT: Sets the worldview new props are placed in.
	 * WHY: On a Sailing boat your position is on the boat's own worldview, not the main world.
	 * PRE: Client thread. view is a WorldView id (-1 is the main world).
	 * POST: Props launched from now on live in that worldview.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void setView(int view)
	{
		this.view = view;
	}

	/**
	 * WHAT: The worldview new props are placed in.
	 * WHY: The pile is cleared when you board or leave a boat.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: a WorldView id (-1 is the main world)
	 * THROWS: Nothing.
	 */
	int getView()
	{
		return view;
	}

	int count(Group group)
	{
		int n = 0;
		for (Prop p : props)
		{
			if (p.group == group)
			{
				n++;
			}
		}
		return n;
	}

	int size()
	{
		return props.size();
	}

	/**
	 * WHAT: The model for an item, scaled, and heated by a step (0 is normal, HEAT_STEPS is
	 * fully hot). Models are made once and reused.
	 * WHY: Loading and lighting a model is the costly part; props reuse the same few models.
	 * PRE: Client thread. scale is 128 for the item's normal size.
	 * POST: The model may be added to the cache.
	 * RETURN: the model, or null if the item has no model
	 * THROWS: Nothing.
	 */
	Model itemModel(int itemId, int scale, int heatStep)
	{
		String key = itemId + ":" + scale + ":" + heatStep;
		Model cached = modelCache.get(key);
		if (cached != null)
		{
			return cached;
		}
		ItemComposition item = client.getItemDefinition(itemId);
		if (item == null || item.getInventoryModel() <= 0)
		{
			return null;
		}
		ModelData data = client.loadModelData(item.getInventoryModel());
		if (data == null)
		{
			return null;
		}
		data = data.shallowCopy().cloneVertices().cloneColors();
		short[] from = item.getColorToReplace();
		short[] to = item.getColorToReplaceWith();
		if (from != null && to != null)
		{
			for (int i = 0; i < Math.min(from.length, to.length); i++)
			{
				data.recolor(from[i], to[i]);
			}
		}
		if (heatStep > 0)
		{
			// Change each face directly, so one colour's new value is never re-heated as another.
			short[] faces = data.getFaceColors();
			double heat = heatStep / (double) HEAT_STEPS;
			for (int i = 0; i < faces.length; i++)
			{
				faces[i] = HeatColor.heat(faces[i], heat);
			}
		}
		if (scale != 128)
		{
			data.scale(scale, scale, scale);
		}
		Model model = data.light();
		modelCache.put(key, model);
		return model;
	}

	/**
	 * WHAT: Adds a prop that flies from one point to another along an arc, then stays.
	 * WHY: Logs onto the pile, catches into the basket, ore out of the rock.
	 * PRE: Client thread. Points are in local units; heights are above the ground.
	 * POST: A new object is shown in the world.
	 * RETURN: the prop, or null if there was no model
	 * THROWS: Nothing.
	 */
	Prop launch(Model model, Group group, int plane, double fromX, double fromY, double fromZ, double toX, double toY,
		double toZ, double peak, double duration, boolean bounce, int orientation, double now)
	{
		if (model == null)
		{
			return null;
		}
		RuneLiteObject object = client.createRuneLiteObject();
		object.setModel(model);
		object.setOrientation(orientation);
		Prop p = new Prop(object, group, plane, view);
		p.fromX = fromX;
		p.fromY = fromY;
		p.fromZ = fromZ;
		p.toX = toX;
		p.toY = toY;
		p.toZ = toZ;
		p.peak = peak;
		p.start = now;
		p.duration = Math.max(0.001, duration);
		p.bounce = bounce;
		p.baseOrientation = orientation;
		place(p, fromX, fromY, fromZ);
		object.setActive(true);
		props.add(p);
		return p;
	}

	/**
	 * WHAT: Makes a prop cool from red-hot over a few seconds.
	 * WHY: Smelted bars and smithed items.
	 * PRE: models has HEAT_STEPS + 1 entries, from normal (0) to fully hot.
	 * POST: The prop swaps models as it cools.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	static void cool(Prop p, Model[] models, double start, double duration)
	{
		p.heatModels = models;
		p.coolStart = start;
		p.coolDuration = duration;
	}

	/**
	 * WHAT: Moves, cools, spins and expires every prop for the current time.
	 * WHY: Called before each frame is drawn, so motion is as smooth as the frame rate.
	 * PRE: Client thread. now is in seconds.
	 * POST: Props are updated; expired ones are removed from the world.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void update(double now)
	{
		Iterator<Prop> it = props.iterator();
		while (it.hasNext())
		{
			Prop p = it.next();
			// A boat's worldview goes away when it leaves the scene; its props go with it.
			if (now >= p.expires || (p.view != -1 && client.getWorldView(p.view) == null))
			{
				p.object.setActive(false);
				it.remove();
				continue;
			}
			double u = Motion.clamp((now - p.start) / p.duration);
			if (u < 1 || p.bounce)
			{
				double move = Motion.smooth(u);
				double x = Motion.lerp(p.fromX, p.toX, move);
				double y = Motion.lerp(p.fromY, p.toY, move);
				double z = Motion.lerp(p.fromZ, p.toZ, move) + Motion.arcHeight(u, p.peak);
				if (p.bounce && u >= 0.7)
				{
					// A small hop as it lands, fading out.
					double b = (u - 0.7) / 0.3;
					z = p.toZ + Math.abs(Math.sin(b * Math.PI * 2)) * 10 * (1 - b);
				}
				place(p, x, y, z);
				if (u >= 1)
				{
					// Landed: stop moving it every frame.
					p.bounce = false;
				}
			}
			if (p.heatModels != null)
			{
				double cooled = Motion.clamp((now - p.coolStart) / p.coolDuration);
				int step = (int) Math.round((1 - Motion.smooth(cooled)) * HEAT_STEPS);
				if (step != p.shownStep && p.heatModels[step] != null)
				{
					p.object.setModel(p.heatModels[step]);
					p.shownStep = step;
				}
			}
			if (p.spinPerSecond != 0)
			{
				int o = (int) (p.baseOrientation + (now - p.start) * p.spinPerSecond) & 2047;
				p.object.setOrientation(o);
			}
		}
	}

	private void place(Prop p, double x, double y, double heightAboveGround)
	{
		LocalPoint lp = new LocalPoint((int) x, (int) y, p.view);
		p.object.setLocation(lp, p.plane);
		// setLocation puts it on the ground; lift it (up is negative in the game).
		p.object.setZ(Perspective.getTileHeight(client, lp, p.plane) - (int) heightAboveGround);
	}

	/**
	 * WHAT: Removes every prop in a group, or all of them.
	 * WHY: Piles clear when you walk away or bank; everything clears on logout or when the
	 * plugin stops.
	 * PRE: Client thread. group may be null for all.
	 * POST: The props are gone from the world.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void clear(Group group)
	{
		Iterator<Prop> it = props.iterator();
		while (it.hasNext())
		{
			Prop p = it.next();
			if (group == null || p.group == group)
			{
				p.object.setActive(false);
				it.remove();
			}
		}
	}

	/**
	 * WHAT: Removes everything and forgets the cached models.
	 * WHY: When the plugin stops, nothing should stay in the world or in memory.
	 * PRE: Client thread.
	 * POST: No props and no cached models.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void shutDown()
	{
		clear(null);
		modelCache.clear();
	}
}
