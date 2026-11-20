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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * WHAT: Draws the particles (chips, sparks, dust, droplets, embers, shavings, bubbles, glints
 * and leaves) and the "+N" item pops on the game view, every frame.
 * WHY: Small fast effects look best as a smooth 2D layer on top of the scene; they're drawn at
 * world positions, so they stay in place when the camera moves.
 * PRE: Overlays render on the client thread, the same thread that adds particles and pops.
 * POST: Particles and pops age with real time and are removed when done.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
class EffectsOverlay extends Overlay
{
	/** How close the mouse must be to a pile, in pixels, to show its count. */
	private static final int HOVER_PIXELS = 34;
	private volatile List<PileLabel> pileLabels = java.util.Collections.emptyList();

	/** How long an item pop lasts, in seconds. */
	static final double POP_SECONDS = 1.3;

	static final class Pop
	{
		final int itemId;
		final String text;
		final double x;
		final double y;
		final int plane;
		final double start;
		// Its row when several arrive together: 0 is the lowest.
		int lane;

		Pop(int itemId, String text, double x, double y, int plane, double start)
		{
			this.itemId = itemId;
			this.text = text;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.start = start;
		}
	}

	private final Client client;
	private final ItemManager itemManager;
	private final Particles particles = new Particles(7);
	private final List<Pop> pops = new ArrayList<>();
	private int plane;
	private int view = -1;
	private long lastNanos;

	@Inject
	EffectsOverlay(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(Overlay.PRIORITY_LOW);
	}

	Particles getParticles()
	{
		return particles;
	}

	/**
	 * WHAT: Sets the worldview and plane particles and pops are drawn on.
	 * WHY: On a Sailing boat your position is on the boat's own worldview, not the main world.
	 * PRE: view is a WorldView id (-1 is the main world).
	 * POST: Drawing uses them from the next frame.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	void setView(int view, int plane)
	{
		this.view = view;
		this.plane = plane;
	}

	/** At most this many pops at once, so a fast activity doesn't stack a tower of them. */
	static final int MAX_POPS = 6;
	/** Pops starting this close together (seconds) share the screen, so they take separate rows. */
	static final double TOGETHER_SECONDS = 0.5;
	/** How far apart the rows are, in pixels. */
	static final int LANE_PIXELS = 18;

	void addPop(Pop pop)
	{
		if (pops.size() >= MAX_POPS)
		{
			pops.remove(0);
		}
		pop.lane = freeLane(pops, pop.start);
		pops.add(pop);
	}

	/**
	 * WHAT: The lowest row not taken by a pop that started at about the same time.
	 * WHY: A money pouch or a birdhouse gives several items at once; their pops drew on top of each
	 * other (issues #3 and #4). Each now gets its own row.
	 * PRE: pops are the ones showing; start is the new pop's start, in seconds.
	 * POST: Nothing changes.
	 * RETURN: the row, 0 for the usual place
	 * THROWS: Nothing.
	 */
	static int freeLane(java.util.List<Pop> pops, double start)
	{
		java.util.Set<Integer> taken = new java.util.HashSet<>();
		for (Pop other : pops)
		{
			if (Math.abs(other.start - start) < TOGETHER_SECONDS)
			{
				taken.add(other.lane);
			}
		}
		int lane = 0;
		while (taken.contains(lane))
		{
			lane++;
		}
		return lane;
	}

	void clear()
	{
		particles.clear();
		pops.clear();
		pileLabels = java.util.Collections.emptyList();
	}

	/**
	 * WHAT: A pile's place and its hover text.
	 * WHY: The overlay draws the text when the mouse is over the pile.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static final class PileLabel
	{
		final double x;
		final double y;
		final String text;

		PileLabel(double x, double y, String text)
		{
			this.x = x;
			this.y = y;
			this.text = text;
		}
	}

	void setPileLabels(List<PileLabel> labels)
	{
		pileLabels = labels;
	}

	/**
	 * WHAT: Draws the count of the pile under the mouse, just above it.
	 * WHY: Pile polish: hover a pile to see how much is on it.
	 * PRE: Render thread (the client thread).
	 * POST: At most one label is drawn.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void drawPileLabel(Graphics2D g)
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || mouse.getX() < 0)
		{
			return;
		}
		for (PileLabel label : pileLabels)
		{
			Point at = project(label.x, label.y, 30);
			if (at == null || Math.hypot(at.getX() - mouse.getX(), at.getY() - mouse.getY()) > HOVER_PIXELS)
			{
				continue;
			}
			g.setFont(FontManager.getRunescapeBoldFont());
			int x = at.getX() - g.getFontMetrics().stringWidth(label.text) / 2;
			int y = at.getY() - 24;
			g.setColor(Color.BLACK);
			g.drawString(label.text, x + 1, y + 1);
			g.setColor(Color.YELLOW);
			g.drawString(label.text, x, y);
			return;
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long nanos = System.nanoTime();
		double dt = lastNanos == 0 ? 0 : (nanos - lastNanos) / 1e9;
		lastNanos = nanos;
		particles.update(dt);
		List<PileLabel> labels = pileLabels;
		if (particles.getLive().isEmpty() && pops.isEmpty() && labels.isEmpty())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (Particles.Particle p : particles.getLive())
		{
			drawParticle(g, p);
		}
		double now = nanos / 1e9;
		Iterator<Pop> it = pops.iterator();
		while (it.hasNext())
		{
			Pop pop = it.next();
			double age = (now - pop.start) / POP_SECONDS;
			if (age >= 1)
			{
				it.remove();
				continue;
			}
			drawPop(g, pop, age);
		}
		if (!labels.isEmpty())
		{
			drawPileLabel(g);
		}
		return null;
	}

	private Point project(double x, double y, double z)
	{
		LocalPoint lp = new LocalPoint((int) x, (int) y, view);
		return Perspective.localToCanvas(client, lp, plane, (int) z);
	}

	private static Color colour(int rgb, double alpha)
	{
		return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) Math.round(255 * Math.max(0, Math.min(1, alpha))));
	}

	private void drawParticle(Graphics2D g, Particles.Particle p)
	{
		Point at = project(p.x, p.y, p.z);
		if (at == null)
		{
			return;
		}
		double life = p.progress();
		// Fade in quickly, fade out over the last third.
		float alpha = (float) (Math.min(1, life / 0.08) * (life > 0.66 ? 1 - (life - 0.66) / 0.34 : 1));
		int x = at.getX();
		int y = at.getY();
		switch (p.kind)
		{
			case CHIP:
			{
				AffineTransform t = g.getTransform();
				g.translate(x, y);
				g.rotate(p.age * p.spin);
				g.setColor(new Color(178, 132, 74, (int) (230 * alpha)));
				g.fillRect(-3, -1, 6, 3);
				g.setTransform(t);
				break;
			}
			case SPARK:
			{
				Point tail = project(p.x - p.vx * 0.03, p.y - p.vy * 0.03, p.z - p.vz * 0.03);
				g.setStroke(new BasicStroke(p.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(255, 205, 90, (int) (220 * alpha)));
				if (tail != null)
				{
					g.draw(new Line2D.Double(tail.getX(), tail.getY(), x, y));
				}
				g.setColor(new Color(255, 250, 220, (int) (240 * alpha)));
				g.fill(new Ellipse2D.Double(x - 1, y - 1, 2, 2));
				break;
			}
			case DUST:
			{
				double r = p.size * (0.6 + life);
				g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r, new float[]{0f, 1f},
					new Color[]{new Color(205, 195, 180, (int) (90 * alpha)), new Color(205, 195, 180, 0)}));
				g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
				break;
			}
			case DROPLET:
				g.setColor(new Color(205, 232, 255, (int) (210 * alpha)));
				g.fill(new Ellipse2D.Double(x - 1.2, y - 1.2, 2.4, 2.4));
				break;
			case EMBER:
			{
				float pulse = (float) Math.sin(life * Math.PI);
				g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), 4f, new float[]{0f, 1f},
					new Color[]{new Color(255, 160, 60, (int) (160 * pulse)), new Color(255, 160, 60, 0)}));
				g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
				g.setColor(new Color(255, 220, 140, (int) (230 * pulse)));
				g.fill(new Ellipse2D.Double(x - 1, y - 1, 2, 2));
				break;
			}
			case BUBBLE:
				g.setStroke(new BasicStroke(1f));
				g.setColor(new Color(190, 240, 210, (int) (200 * alpha)));
				g.draw(new Ellipse2D.Double(x - p.size, y - p.size, p.size * 2, p.size * 2));
				g.setColor(new Color(255, 255, 255, (int) (180 * alpha)));
				g.fill(new Ellipse2D.Double(x - p.size * 0.5, y - p.size * 0.6, 1.2, 1.2));
				break;
			case GLINT:
			{
				// A four-point twinkle that grows and shrinks.
				double r = 1 + p.size * Math.sin(life * Math.PI);
				g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(255, 236, 170, (int) (230 * alpha)));
				g.draw(new Line2D.Double(x - r, y, x + r, y));
				g.draw(new Line2D.Double(x, y - r, x, y + r));
				break;
			}
			case LEAF:
			{
				AffineTransform t = g.getTransform();
				g.translate(x, y);
				g.rotate(p.age * p.spin);
				g.setColor(new Color(110, 170, 80, (int) (230 * alpha)));
				g.fill(new Ellipse2D.Double(-3, -1.3, 6, 2.6));
				g.setTransform(t);
				break;
			}
			case MOTE:
			{
				// A soft halo with a bright core, breathing as it rises.
				float glow = (float) Math.sin(life * Math.PI);
				g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), 6f, new float[]{0f, 1f},
					new Color[]{new Color(255, 240, 190, (int) (150 * glow)), new Color(255, 240, 190, 0)}));
				g.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
				g.setColor(new Color(255, 255, 235, (int) (230 * glow)));
				g.fill(new Ellipse2D.Double(x - 1.2, y - 1.2, 2.4, 2.4));
				break;
			}
			case MAGIC:
				g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), 4f, new float[]{0f, 1f},
					new Color[]{new Color(120, 255, 130, (int) (170 * alpha)), new Color(120, 255, 130, 0)}));
				g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
				g.setColor(new Color(220, 255, 220, (int) (240 * alpha)));
				g.fill(new Ellipse2D.Double(x - 1, y - 1, 2, 2));
				break;
			case SOIL:
				g.setColor(new Color(96, 70, 44, (int) (230 * alpha)));
				g.fill(new Ellipse2D.Double(x - 1.5, y - 1.5, 3, 3));
				break;
			case STONE:
			{
				AffineTransform t = g.getTransform();
				g.translate(x, y);
				g.rotate(p.age * p.spin);
				g.setColor(new Color(132, 126, 118, (int) (235 * alpha)));
				g.fillRect(-2, -2, 4, 3);
				g.setTransform(t);
				break;
			}
			case FALLING_LEAF:
			{
				// Swings side to side as it falls.
				AffineTransform t = g.getTransform();
				g.translate(x + Math.sin(p.age * 3 + p.spin) * 6, y);
				g.rotate(Math.sin(p.age * 2.5 + p.spin) * 0.9);
				g.setColor(new Color(96, 152, 64, (int) (230 * alpha)));
				g.fill(new Ellipse2D.Double(-3.5, -1.5, 7, 3));
				g.setTransform(t);
				break;
			}
			case RIPPLE:
			{
				// A flat ring on the ground or water, sized by projecting its edge.
				double r = p.size * (0.25 + 0.75 * Motion.easeOut(life));
				Point edge = project(p.x + r, p.y, p.z);
				if (edge != null)
				{
					double rx = Math.max(2, Math.hypot(edge.getX() - x, edge.getY() - y));
					g.setStroke(new BasicStroke(1.6f));
					g.setColor(colour(p.rgb, 0.8 * (1 - life)));
					g.draw(new Ellipse2D.Double(x - rx, y - rx * 0.42, rx * 2, rx * 0.84));
				}
				break;
			}
			case GLOW:
			{
				// A flash of light: swells and fades.
				double k = Math.sin(life * Math.PI);
				Point edge = project(p.x + p.size, p.y, p.z);
				double r = edge == null ? p.size / 4 : Math.max(4, Math.hypot(edge.getX() - x, edge.getY() - y)) * (0.6 + 0.4 * k);
				g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r, new float[]{0f, 0.4f, 1f},
					new Color[]{colour(p.rgb, 0.55 * k), colour(p.rgb, 0.2 * k), colour(p.rgb, 0)}));
				g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
				break;
			}
			default:
				g.setStroke(new BasicStroke(1.4f));
				g.setColor(new Color(214, 176, 112, (int) (220 * alpha)));
				g.draw(new Arc2D.Double(x - 3.5, y - 2.5, 7, 5, p.spin * 20, 220, Arc2D.OPEN));
				break;
		}
	}

	private void drawPop(Graphics2D g, Pop pop, double age)
	{
		double rise = Motion.smooth(age) * 40;
		Point at = project(pop.x, pop.y, 150 + rise);
		if (at == null)
		{
			return;
		}
		float alpha = (float) (age < 0.12 ? age / 0.12 : 1 - Motion.smooth((age - 0.55) / 0.45));
		// Bounces in, slightly overshooting.
		double scale = age < 0.18 ? 0.5 + 0.7 * Motion.smooth(age / 0.18) - 0.2 * Motion.smooth((age - 0.1) / 0.08) : 1;
		Composite old = g.getComposite();
		AffineTransform t = g.getTransform();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
		g.translate(at.getX(), at.getY() - pop.lane * LANE_PIXELS);
		g.scale(scale, scale);
		BufferedImage icon = pop.itemId > 0 ? itemManager.getImage(pop.itemId) : null;
		int textX = 4;
		if (icon != null)
		{
			g.drawImage(icon, -icon.getWidth() / 2 - 10, -icon.getHeight() / 2, null);
			textX = icon.getWidth() / 2 - 6;
		}
		g.setFont(FontManager.getRunescapeBoldFont());
		g.setColor(Color.BLACK);
		g.drawString(pop.text, textX + 1, 5);
		g.setColor(Color.WHITE);
		g.drawString(pop.text, textX, 4);
		g.setTransform(t);
		g.setComposite(old);
	}
}
