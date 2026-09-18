package com.siegedempires.guide;

import com.siegedempires.Siegedempires;
import com.siegedempires.config.ModSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds Sieged Empire Guide pages from {@code wiki/towns.txt} (or the bundled
 * {@code assets/siegedempires/texts/towns.txt} fallback). Bracket directives
 * are interpreted at build time; quoted values are filled from {@link ModSettings}.
 */
public final class GuideBookContent {
	private static final Path WIKI_PATH = Path.of("wiki", "towns.txt");
	private static final String RESOURCE_PATH = "/assets/siegedempires/texts/towns.txt";
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
	private static final Style TITLE_STYLE = Style.EMPTY.withBold(true);

	public static final Pattern ITEM_MARKER = Pattern.compile("\\{\\{item:([^}:]+):([^}]+)\\}\\}");
	/** Cycles displayed ItemStacks in the guide GUI (pipe-separated ns:path ids). */
	public static final Pattern CYCLE_ITEM_MARKER = Pattern.compile("\\{\\{cycle_item:([^}]+)\\}\\}");
	/**
	 * Crafting-table display: {@code {{crafting:c0,c1,...,c8;result}}} where each cell is
	 * {@code .}, {@code ns:path} / {@code path}, or {@code cycle:id+id+...}.
	 */
	public static final Pattern CRAFTING_MARKER = Pattern.compile("\\{\\{crafting:([^;]+);([^}]+)\\}\\}");
	/** Large item top-left beside a bold title: {@code {{section_header:ns:path:count|Title}}}. */
	public static final Pattern SECTION_HEADER_MARKER =
			Pattern.compile("\\{\\{section_header:([^:}]+):([^:}]+):(\\d+)\\|([^}]+)\\}\\}");

	public static final String RECIPE_BLOCK_START = "{{recipe_block}}";
	public static final String RECIPE_BLOCK_END = "{{/recipe_block}}";
	public static final String EMPTY_CRAFT_CELL = ".";

	public static final String WELCOME_SECTION_ID = "welcome";
	public static final String INDEX_SECTION_ID = "index";
	public static final String CRAFTING_SECTION_ID = "crafting";

	private GuideBookContent() {
	}

	/** Parsed wiki sections (welcome first); crafting is appended by the screen. */
	public static List<GuideSection> wikiSections() {
		List<String> lines = loadSourceLines();
		List<GuideSection> sections = new ArrayList<>();
		String sectionId = WELCOME_SECTION_ID;
		String sectionTitle = "Welcome";
		List<Component> sectionPages = new ArrayList<>();
		MutableComponent page = Component.empty();
		boolean pageHasContent = false;

		for (String rawLine : lines) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}

			Directive directive = parseDirective(line);
			if (directive != null) {
				switch (directive.kind()) {
					case END -> {
						if (pageHasContent) {
							sectionPages.add(page);
						}
						if (!sectionPages.isEmpty() || pageHasContent) {
							sections.add(new GuideSection(sectionId, sectionTitle, List.copyOf(sectionPages)));
						}
						return sections;
					}
					case SECTION -> {
						if (pageHasContent) {
							sectionPages.add(page);
							page = Component.empty();
							pageHasContent = false;
						}
						if (!sectionPages.isEmpty()) {
							sections.add(new GuideSection(sectionId, sectionTitle, List.copyOf(sectionPages)));
							sectionPages = new ArrayList<>();
						}
						sectionId = directive.arg();
						sectionTitle = directive.arg2() != null ? directive.arg2() : directive.arg();
					}
					case NEW_PAGE -> {
						if (pageHasContent) {
							sectionPages.add(page);
						}
						page = Component.empty();
						pageHasContent = false;
					}
					case BLANK_LINE -> {
						page.append("\n");
						pageHasContent = true;
					}
					case TITLE -> {
						if (pageHasContent) {
							page.append("\n");
						}
						page.append(Component.literal(applyConfigQuotes(directive.arg())).withStyle(TITLE_STYLE));
						pageHasContent = true;
					}
					case IRON_SWORD_SPACER -> {
						page.append("\n\n  ");
						page.append(itemMarker("minecraft", "iron_sword"));
						page.append("\n\n");
						pageHasContent = true;
					}
					case RECIPES_LOCK_SET, RECIPE_LOCKPICK, SKIP -> {
						// Lock recipes live in Crafting Recipes section now.
					}
					case UNKNOWN -> Siegedempires.LOGGER.warn("Unknown guide book directive: {}", line);
				}
				continue;
			}

			if (pageHasContent) {
				page.append("\n");
			}
			page.append(Component.literal(applyConfigQuotes(line)));
			pageHasContent = true;
		}

		if (pageHasContent) {
			sectionPages.add(page);
		}
		if (!sectionPages.isEmpty()) {
			sections.add(new GuideSection(sectionId, sectionTitle, List.copyOf(sectionPages)));
		}
		return sections;
	}

	/** Flat page list for written-book NBT fallback (no index page). */
	public static List<Component> pageComponents() {
		List<Component> pages = new ArrayList<>();
		for (GuideSection section : wikiSections()) {
			pages.addAll(section.pages());
		}
		pages.addAll(GuideBookRecipes.pageComponents());
		return pages;
	}

	public static List<Filterable<Component>> buildPages() {
		List<Component> pages = pageComponents();
		List<Filterable<Component>> filterable = new ArrayList<>(pages.size());
		for (Component page : pages) {
			filterable.add(Filterable.passThrough(page));
		}
		return filterable;
	}

	public static WrittenBookContent createContent() {
		return new WrittenBookContent(
				Filterable.passThrough(GuideBook.TITLE),
				GuideBook.AUTHOR,
				0,
				buildPages(),
				true);
	}

	public static MutableComponent itemMarker(String namespace, String path) {
		return Component.literal("{{item:" + namespace + ":" + path + "}}");
	}

	public static MutableComponent cycleItemMarker(String... ids) {
		return Component.literal("{{cycle_item:" + String.join("|", ids) + "}}");
	}

	static void beginRecipeBlock(MutableComponent page) {
		page.append(Component.literal(RECIPE_BLOCK_START));
	}

	static void endRecipeBlock(MutableComponent page) {
		page.append(Component.literal(RECIPE_BLOCK_END));
	}

	/** Nine crafting-grid cells (row-major) plus result; empty cells use {@link #EMPTY_CRAFT_CELL}. */
	public static void appendCrafting(MutableComponent page, String result, String... cells) {
		if (cells == null || cells.length != 9) {
			throw new IllegalArgumentException("Crafting grid requires exactly 9 cells");
		}
		StringBuilder marker = new StringBuilder("{{crafting:");
		for (int i = 0; i < 9; i++) {
			if (i > 0) {
				marker.append(',');
			}
			String cell = cells[i];
			marker.append(cell == null || cell.isEmpty() ? EMPTY_CRAFT_CELL : cell);
		}
		marker.append(';').append(result == null || result.isEmpty() ? EMPTY_CRAFT_CELL : result);
		marker.append("}}");
		page.append(Component.literal(marker.toString()));
	}

	/** Cycle cell for {@link #appendCrafting}: ids are {@code ns:path}. */
	public static String cycleCraftCell(String... ids) {
		return "cycle:" + String.join("+", ids);
	}

	static void appendGridRow(MutableComponent page, String... cells) {
		page.append(" ");
		for (String cell : cells) {
			if (cell == null || cell.isEmpty() || ".".equals(cell)) {
				page.append(" ");
			} else if (cell.startsWith("cycle:")) {
				page.append(Component.literal("{{" + cell.substring("cycle:".length()) + "}}"));
			} else {
				int colon = cell.indexOf(':');
				if (colon > 0) {
					page.append(itemMarker(cell.substring(0, colon), cell.substring(colon + 1)));
				} else {
					page.append(itemMarker("minecraft", cell));
				}
			}
		}
	}

	static String applyConfigQuotes(String text) {
		ModSettings settings = ModSettings.get();
		Matcher matcher = QUOTED.matcher(text);
		StringBuffer out = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			matcher.appendReplacement(out, Matcher.quoteReplacement(resolveQuoted(key, settings)));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String resolveQuoted(String key, ModSettings settings) {
		return switch (key) {
			case "5" -> Integer.toString(settings.createTownCost);
			case "3" -> Integer.toString(settings.invasionMinOnlinePlayers);
			case "8", "2" -> Integer.toString(settings.invasionPrice);
			case "80" -> Integer.toString(settings.warBannerHealth);
			case "20" -> Integer.toString(Math.max(1, settings.invasionDurationSeconds / 60));
			case "1:60" -> settings.worldScaleBook;
			default -> key;
		};
	}

	private static List<String> loadSourceLines() {
		if (Files.isRegularFile(WIKI_PATH)) {
			try {
				return Files.readAllLines(WIKI_PATH, StandardCharsets.UTF_8);
			} catch (IOException e) {
				Siegedempires.LOGGER.error("Failed to read {}", WIKI_PATH, e);
			}
		}
		try (InputStream stream = GuideBookContent.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				Siegedempires.LOGGER.error("Missing guide book resource {}", RESOURCE_PATH);
				return List.of("Guide book text is missing.");
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				List<String> lines = new ArrayList<>();
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
				return lines;
			}
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to read guide book resource", e);
			return List.of("Guide book text failed to load.");
		}
	}

	private static Directive parseDirective(String line) {
		if (!line.startsWith("[") || !line.endsWith("]")) {
			return null;
		}
		String inner = line.substring(1, line.length() - 1).strip();
		String lower = inner.toLowerCase(Locale.ROOT);

		if (lower.startsWith("ending of book")) {
			return Directive.end();
		}
		if (lower.startsWith("section ")) {
			List<String> quotes = extractAllQuoted(inner);
			if (quotes.size() >= 2) {
				return Directive.section(quotes.get(0), quotes.get(1));
			}
			if (quotes.size() == 1) {
				return Directive.section(quotes.get(0), quotes.get(0));
			}
			return new Directive(DirectiveKind.UNKNOWN, inner, null);
		}
		if (lower.equals("new page") || lower.equals("newpage")) {
			return Directive.newPage();
		}
		if (lower.equals("blank line") || lower.equals("new line") || lower.startsWith("new line.")) {
			return Directive.blankLine();
		}
		if (lower.startsWith("add a few blank lines") && lower.contains("iron sword")) {
			return new Directive(DirectiveKind.IRON_SWORD_SPACER, null, null);
		}
		if (lower.startsWith("insert crafting recip") && lower.contains("locksmithing")) {
			return new Directive(DirectiveKind.RECIPES_LOCK_SET, null, null);
		}
		if (lower.startsWith("insert crafting recip") && lower.contains("lockpick")) {
			return new Directive(DirectiveKind.RECIPE_LOCKPICK, null, null);
		}
		if (lower.startsWith("display icons")) {
			return new Directive(DirectiveKind.SKIP, null, null);
		}
		if (lower.contains("world-scale-book") || lower.contains("make a new value in settings")) {
			String title = extractQuoted(inner);
			return title != null ? Directive.title(title) : Directive.blankLine();
		}
		if (lower.startsWith("in bold and large")
				|| lower.startsWith("large and bold")
				|| lower.startsWith("large and bold:")) {
			String title = extractQuoted(inner);
			return title != null ? Directive.title(title) : new Directive(DirectiveKind.UNKNOWN, inner, null);
		}
		return new Directive(DirectiveKind.UNKNOWN, inner, null);
	}

	private static String extractQuoted(String text) {
		Matcher matcher = QUOTED.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static List<String> extractAllQuoted(String text) {
		List<String> out = new ArrayList<>();
		Matcher matcher = QUOTED.matcher(text);
		while (matcher.find()) {
			out.add(matcher.group(1));
		}
		return out;
	}

	private enum DirectiveKind {
		NEW_PAGE,
		BLANK_LINE,
		TITLE,
		SECTION,
		IRON_SWORD_SPACER,
		RECIPES_LOCK_SET,
		RECIPE_LOCKPICK,
		SKIP,
		END,
		UNKNOWN
	}

	private record Directive(DirectiveKind kind, String arg, String arg2) {
		static Directive newPage() {
			return new Directive(DirectiveKind.NEW_PAGE, null, null);
		}

		static Directive blankLine() {
			return new Directive(DirectiveKind.BLANK_LINE, null, null);
		}

		static Directive title(String text) {
			return new Directive(DirectiveKind.TITLE, text, null);
		}

		static Directive section(String id, String title) {
			return new Directive(DirectiveKind.SECTION, id, title);
		}

		static Directive end() {
			return new Directive(DirectiveKind.END, null, null);
		}
	}

	/** One navigable guide section (may span multiple auto-split pages). */
	public record GuideSection(String id, String title, List<Component> pages) {
	}
}
