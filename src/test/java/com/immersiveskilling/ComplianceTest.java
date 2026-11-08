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

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Checks the source against the Plugin Hub's rules and Jagex's third-party client guidelines:
 * no reflection, network, processes or file access; no input automation or menu changes;
 * nothing that changes the game for others; the panel never reads the game off the client
 * thread; everything is cleaned up on shutdown; Java 11 only; and the hub listing is valid.
 */
public class ComplianceTest
{
	private static final Path SOURCES = Paths.get("src/main/java/com/immersiveskilling");

	private static String read(Path p) throws IOException
	{
		return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
	}

	private static List<Path> sources() throws IOException
	{
		try (Stream<Path> s = Files.list(SOURCES))
		{
			return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}

	/** Code only: comments are removed so explanations can mention banned words. */
	private static String code(String source)
	{
		return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
	}

	private static void assertNone(String what, String... patterns) throws IOException
	{
		List<String> found = new ArrayList<>();
		for (Path p : sources())
		{
			String c = code(read(p));
			for (String pattern : patterns)
			{
				if (Pattern.compile(pattern).matcher(c).find())
				{
					found.add(p.getFileName() + ": " + pattern);
				}
			}
		}
		assertTrue(what + ": " + found, found.isEmpty());
	}

	@Test
	public void noReflectionNetworkProcessesOrFiles() throws IOException
	{
		assertNone("Banned by the Plugin Hub",
			"java\\.lang\\.reflect", "getDeclared(Field|Method|Constructor)", "setAccessible", "Class\\.forName",
			"java\\.net\\.", "HttpURLConnection", "OkHttpClient", "Runtime\\.getRuntime", "ProcessBuilder",
			"System\\.exit", "java\\.io\\.File\\b", "Files\\.(write|newOutputStream|delete)", "new Thread\\(",
			"Unsafe", "JNI", "loadLibrary");
	}

	@Test
	public void noInputAutomationOrMenuChanges() throws IOException
	{
		// Jagex's guidelines: nothing that clicks, types or changes menus for you.
		assertNone("Not a visual change",
			"java\\.awt\\.Robot", "dispatchEvent", "KeyEvent", "MenuEntry", "createMenuEntry", "menuAction", "MenuOptionClicked",
			"setMenuEntries", "invokeMenuAction", "setCameraYaw", "setCameraPitch", "client\\.set(Var|Skill|Selected)",
			"getWidget\\(.*\\)\\.set", "setHidden");
	}

	@Test
	public void neverChangesOtherPlayersOrNpcs() throws IOException
	{
		// Only our own RuneLiteObjects are created or changed; other actors are only read.
		assertNone("Visual changes are limited to our own props",
			"getPlayers\\(\\)", "getNpcs\\(\\)", "\\.setAnimation\\(", "\\.setPoseAnimation", "\\.setSpotAnim",
			"\\.setOverheadText", "setModelOverride");
	}

	@Test
	public void loggingIsDebugOnly() throws IOException
	{
		assertNone("Hub plugins log at debug level only", "log\\.(info|warn|error)\\(", "System\\.out", "System\\.err",
			"printStackTrace");
	}

	@Test
	public void java11Only() throws IOException
	{
		// The hub builds with Java 11.
		assertNone("Needs a newer Java than 11", "\\brecord\\s+\\w+\\s*\\(", "\"\"\"", "case [^:\\n]*->", "instanceof \\w+ \\w+\\s*[)&|]",
			"\\.toList\\(\\)", "sealed ", "permits ");
	}

	@Test
	public void thePanelNeverReadsTheGame() throws IOException
	{
		String panel = code(read(SOURCES.resolve("ImmersiveSkillingPanel.java"))).replaceAll("(?m)^import .*$", "");
		assertTrue("The panel must not touch the client",
			!Pattern.compile("\\bclient\\.\\w+\\(").matcher(panel).find() && !panel.contains("Client ") && !panel.contains("itemManager"));
		// Every plugin method the panel calls must be safe from the Swing thread.
		List<String> allowed = Arrays.asList("preview", "clearFromPanel", "setPaused", "isPaused", "getStatus",
			"getStatusCount", "getStatusCap", "getHint");
		Matcher m = Pattern.compile("plugin(?:::|\\.)(\\w+)").matcher(panel);
		while (m.find())
		{
			assertTrue("Panel calls plugin." + m.group(1), allowed.contains(m.group(1)));
		}
		String plugin = code(read(SOURCES.resolve("ImmersiveSkillingPlugin.java")));
		for (String method : new String[]{"void preview(", "void clearFromPanel("})
		{
			int at = plugin.indexOf(method);
			assertTrue(method, at >= 0);
			int body = plugin.indexOf('{', at);
			assertTrue(method + " must hop to the client thread", plugin.startsWith("clientThread.invokeLater", plugin.indexOf(
				"clientThread", body)) && plugin.indexOf("clientThread", body) < plugin.indexOf('}', body));
		}
	}

	@Test
	public void everythingIsCleanedUpOnShutdown() throws IOException
	{
		String plugin = code(read(SOURCES.resolve("ImmersiveSkillingPlugin.java")));
		int start = plugin.indexOf("protected void shutDown()");
		String body = plugin.substring(start, plugin.indexOf("private void resetState()", start));
		assertTrue("Overlay removed", body.contains("overlayManager.remove(overlay)"));
		assertTrue("Sidebar button removed", body.contains("clientToolbar.removeNavigation(navButton)"));
		assertTrue("Props removed from the world", body.contains(".shutDown()"));
		String props = code(read(SOURCES.resolve("Props.java")));
		assertTrue("Removed props are taken out of the world", props.contains("setActive(false)"));
	}

	@Test
	public void pluginListingIsValid() throws IOException
	{
		Properties p = new Properties();
		try (InputStreamReader in = new InputStreamReader(new FileInputStream("runelite-plugin.properties"), StandardCharsets.UTF_8))
		{
			p.load(in);
		}
		assertEquals("Immersive Skilling", p.getProperty("displayName"));
		assertEquals("AoceanP", p.getProperty("author"));
		assertEquals("com.immersiveskilling.ImmersiveSkillingPlugin", p.getProperty("plugins"));
		assertNotNull(p.getProperty("description"));
		assertTrue(Files.exists(SOURCES.resolve("ImmersiveSkillingPlugin.java")));
		String plugin = read(SOURCES.resolve("ImmersiveSkillingPlugin.java"));
		assertTrue("Descriptor name matches the listing", plugin.contains("name = \"Immersive Skilling\""));
		BufferedImage icon = ImageIO.read(Paths.get("icon.png").toFile());
		assertNotNull("icon.png", icon);
		assertTrue("Hub icons are at most 48x72", icon.getWidth() <= 48 && icon.getHeight() <= 72);
		assertNotNull("Sidebar icon", getClass().getResource("/com/immersiveskilling/panel_icon.png"));
	}
}
