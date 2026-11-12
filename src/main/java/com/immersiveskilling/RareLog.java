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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT: The rare moments log: how many you've had, in which skills, the latest few, and the
 * achievements they unlock. Saved per account as one short line of text.
 * WHY: A 1 in 512 golden moment is worth remembering; a small log and a few goals give people
 * something to chase and talk about.
 * PRE: record is called on the client thread; the panel reads from the Swing thread, so every
 * method is synchronized.
 * POST: Not applicable.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class RareLog
{
	/** Skills that can have a rare moment (Smelting counts as Smithing, Alching as Magic). */
	static final int SKILLS = 16;
	/** How many of the latest rare moments are kept. */
	static final int RECENT = 5;

	/** The goals, in the order the panel shows them. */
	enum Achievement
	{
		FIRST_GLEAM("First gleam", "Your first rare moment", false, 1),
		LUCKY_FIVE("Lucky five", "5 rare moments", false, 5),
		ALL_ROUNDER("All-rounder", "Rare moments in 5 skills", true, 5),
		GOLDEN_TOUCH("Golden touch", "25 rare moments", false, 25),
		GILDED_HANDS("Gilded hands", "Rare moments in 10 skills", true, 10),
		MIDAS("Midas", "100 rare moments", false, 100),
		GILDED_EVERYTHING("Gilded everything", "A rare moment in every skill", true, SKILLS);

		private final String title;
		private final String description;
		private final boolean bySkills;
		private final int target;

		Achievement(String title, String description, boolean bySkills, int target)
		{
			this.title = title;
			this.description = description;
			this.bySkills = bySkills;
			this.target = target;
		}

		String getTitle()
		{
			return title;
		}

		String getDescription()
		{
			return description;
		}

		int getTarget()
		{
			return target;
		}

		/**
		 * WHAT: How far along this goal is, out of its target.
		 * WHY: The panel shows "3 / 5" for goals not yet reached.
		 * PRE: total and skills are the log's counts.
		 * POST: Nothing changes.
		 * RETURN: progress, capped at the target
		 * THROWS: Nothing.
		 */
		int progress(int total, int skills)
		{
			return Math.min(target, bySkills ? skills : total);
		}
	}

	/** One rare moment: when, in which skill, and what turned gold. */
	static final class Entry
	{
		final long time;
		final String skill;
		final String item;

		Entry(long time, String skill, String item)
		{
			this.time = time;
			this.skill = skill;
			this.item = item;
		}
	}

	private final Map<String, Integer> bySkill = new LinkedHashMap<>();
	private final List<Entry> recent = new ArrayList<>();

	/**
	 * WHAT: Records a rare moment and says which achievements it just unlocked.
	 * WHY: The plugin shows each new achievement as it happens.
	 * PRE: skill is the skill's name, e.g. "Woodcutting"; item may be null.
	 * POST: The counts and the latest list are updated.
	 * RETURN: the achievements unlocked by this one, usually none
	 * THROWS: Nothing.
	 */
	synchronized List<Achievement> record(String skill, String item, long time)
	{
		int totalBefore = total();
		int skillsBefore = bySkill.size();
		bySkill.merge(clean(skill), 1, Integer::sum);
		recent.add(0, new Entry(time, clean(skill), clean(item == null ? "" : item)));
		while (recent.size() > RECENT)
		{
			recent.remove(recent.size() - 1);
		}
		List<Achievement> unlocked = new ArrayList<>();
		for (Achievement a : Achievement.values())
		{
			boolean had = a.progress(totalBefore, skillsBefore) >= a.getTarget();
			if (!had && a.progress(total(), bySkill.size()) >= a.getTarget())
			{
				unlocked.add(a);
			}
		}
		return unlocked;
	}

	synchronized int total()
	{
		int n = 0;
		for (int c : bySkill.values())
		{
			n += c;
		}
		return n;
	}

	synchronized int skills()
	{
		return bySkill.size();
	}

	synchronized int count(String skill)
	{
		return bySkill.getOrDefault(skill, 0);
	}

	synchronized List<Entry> recent()
	{
		return Collections.unmodifiableList(new ArrayList<>(recent));
	}

	synchronized boolean has(Achievement a)
	{
		return a.progress(total(), bySkill.size()) >= a.getTarget();
	}

	/**
	 * WHAT: The log as one line of text, for the account's settings.
	 * WHY: Kept small and readable; no files, nothing sent anywhere.
	 * PRE: None.
	 * POST: Nothing changes.
	 * RETURN: e.g. "Woodcutting:3,Fishing:1;1730000000000|Woodcutting|Yew logs"
	 * THROWS: Nothing.
	 */
	synchronized String save()
	{
		StringBuilder s = new StringBuilder();
		for (Map.Entry<String, Integer> e : bySkill.entrySet())
		{
			if (s.length() > 0)
			{
				s.append(',');
			}
			s.append(e.getKey()).append(':').append(e.getValue());
		}
		s.append(';');
		for (int i = 0; i < recent.size(); i++)
		{
			Entry e = recent.get(i);
			if (i > 0)
			{
				s.append('^');
			}
			s.append(e.time).append('|').append(e.skill).append('|').append(e.item);
		}
		return s.toString();
	}

	/**
	 * WHAT: Reads a log saved with save().
	 * WHY: The log carries over between sessions.
	 * PRE: text may be null, empty or damaged.
	 * POST: Nothing changes.
	 * RETURN: the log; whatever can't be read is skipped, never an error
	 * THROWS: Nothing.
	 */
	static RareLog load(String text)
	{
		RareLog log = new RareLog();
		if (text == null || text.isEmpty())
		{
			return log;
		}
		String[] parts = text.split(";", -1);
		for (String pair : parts[0].split(","))
		{
			int colon = pair.lastIndexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			try
			{
				int n = Integer.parseInt(pair.substring(colon + 1));
				if (n > 0)
				{
					log.bySkill.put(pair.substring(0, colon), n);
				}
			}
			catch (NumberFormatException ignored)
			{
				// A damaged count is skipped.
			}
		}
		if (parts.length > 1 && !parts[1].isEmpty())
		{
			for (String e : parts[1].split("\\^"))
			{
				String[] f = e.split("\\|", -1);
				if (f.length != 3 || log.recent.size() >= RECENT)
				{
					continue;
				}
				try
				{
					log.recent.add(new Entry(Long.parseLong(f[0]), f[1], f[2]));
				}
				catch (NumberFormatException ignored)
				{
					// A damaged entry is skipped.
				}
			}
		}
		return log;
	}

	private static String clean(String s)
	{
		return s.replaceAll("[;,:|^]", " ").trim();
	}
}
