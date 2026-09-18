package com.siegedempires.client.gui;

import com.siegedempires.guide.GuideBook;
import com.siegedempires.guide.GuideBookContent;
import com.siegedempires.guide.GuideBookRecipes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Larger custom guide book viewer with an Index page (scrollable section jump
 * buttons), crafting recipes, and cycling bayonet material icons.
 */
public class GuideBookScreen extends Screen {
	private static final Style PAGE_TEXT_STYLE = Style.EMPTY.withoutShadow().withColor(-16777216);
	private static final int PAGE_TEXT_COLOR = -16777216;

	private static final int TEXT_WIDTH = 250;
	private static final int TEXT_HEIGHT = 220;
	private static final int PANEL_PAD = 16;
	private static final int FOOTER_HEIGHT = 64;
	private static final int TITLE_EXTRA = 8;
	private static final int PANEL_WIDTH = TEXT_WIDTH + PANEL_PAD * 2 + 8;
	private static final int LINE_GAP = 2;
	private static final int BUTTON_WIDTH = 70;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;
	private static final int INDEX_OUTSIDE_GAP = 10;
	private static final int PAGE_NUM_GAP_ABOVE_BUTTONS = 10;
	private static final int INDEX_ROW_HEIGHT = 22;
	private static final int INDEX_ROW_GAP = 4;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final long CYCLE_MS = 900L;

	private static final int ITEM_BASE = 16;
	private static final float ITEM_SCALE = 1.5F;
	private static final int ITEM_SIZE = Math.round(ITEM_BASE * ITEM_SCALE);
	private static final int ITEM_GAP = 2;

	private static final float HEADER_ITEM_SCALE = 3.0F;
	private static final int HEADER_ITEM_SIZE = Math.round(ITEM_BASE * HEADER_ITEM_SCALE);
	private static final int HEADER_GAP = 8;

	/** Compact crafting-table display (fits more recipes per page; title stays with grid). */
	private static final int CRAFT_SLOT = 16;
	private static final int CRAFT_RESULT_SLOT = 20;
	private static final int CRAFT_ARROW_GAP = 4;
	private static final int CRAFT_ARROW_WIDTH = 14;
	private static final int CRAFT_PAD = 2;
	private static final int CRAFT_AFTER_GAP = 1;
	private static final int CRAFT_GRID = CRAFT_SLOT * 3;
	private static final int CRAFT_WIDTH = CRAFT_PAD * 2 + CRAFT_GRID + CRAFT_ARROW_GAP
			+ CRAFT_ARROW_WIDTH + CRAFT_ARROW_GAP + CRAFT_RESULT_SLOT;
	private static final int CRAFT_HEIGHT = CRAFT_PAD * 2 + Math.max(CRAFT_GRID, CRAFT_RESULT_SLOT);

	private final List<GuidePage> pages = new ArrayList<>();
	private final List<IndexEntry> indexEntries = new ArrayList<>();
	private int currentPage;
	private int indexPageIndex = 1;
	private int indexScroll;
	private int indexMaxScroll;
	private int panelX;
	private int panelY;
	private int panelHeight;
	private int textX;
	private int textY;
	private Button backButton;
	private Button forwardButton;
	private Button doneButton;
	private Button indexButton;

	public GuideBookScreen(ItemStack bookStack) {
		super(Component.literal(GuideBook.TITLE));
	}

	@Override
	protected void init() {
		super.init();
		layoutPanel();
		rebuildPages();
		currentPage = Math.max(0, Math.min(currentPage, Math.max(0, pages.size() - 1)));
		clampIndexScroll();

		int buttonsY = pageButtonsY();
		int centerX = this.width / 2;
		int doneX = centerX - BUTTON_WIDTH / 2;

		backButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.guide.prev"),
				button -> turnPage(-1)
		).bounds(doneX - BUTTON_GAP - BUTTON_WIDTH, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		doneButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.guide.done"),
				button -> onClose()
		).bounds(doneX, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		forwardButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.guide.next"),
				button -> turnPage(1)
		).bounds(doneX + BUTTON_WIDTH + BUTTON_GAP, buttonsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		indexButton = this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.guide.index"),
				button -> goToIndex()
		).bounds(indexButtonX(), indexButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build());

		updateButtonState();
	}

	private int indexButtonX() {
		return panelX + PANEL_WIDTH + INDEX_OUTSIDE_GAP;
	}

	private int indexButtonY() {
		return panelY + PANEL_PAD;
	}

	private void layoutWidgets() {
		layoutPanel();
		int buttonsY = pageButtonsY();
		int centerX = this.width / 2;
		int doneX = centerX - BUTTON_WIDTH / 2;
		if (backButton != null) {
			backButton.setPosition(doneX - BUTTON_GAP - BUTTON_WIDTH, buttonsY);
		}
		if (doneButton != null) {
			doneButton.setPosition(doneX, buttonsY);
		}
		if (forwardButton != null) {
			forwardButton.setPosition(doneX + BUTTON_WIDTH + BUTTON_GAP, buttonsY);
		}
		if (indexButton != null) {
			indexButton.setPosition(indexButtonX(), indexButtonY());
		}
	}

	private int panelHeight() {
		return PANEL_PAD + this.font.lineHeight + TITLE_EXTRA + TEXT_HEIGHT + FOOTER_HEIGHT;
	}

	private int pageButtonsY() {
		return panelY + panelHeight - PANEL_PAD - BUTTON_HEIGHT;
	}

	private int pageNumberY() {
		return pageButtonsY() - PAGE_NUM_GAP_ABOVE_BUTTONS - this.font.lineHeight;
	}

	private void layoutPanel() {
		panelHeight = panelHeight();
		panelX = (this.width - PANEL_WIDTH) / 2;
		panelY = Math.max(4, (this.height - panelHeight) / 2);
		textX = panelX + PANEL_PAD;
		textY = panelY + PANEL_PAD + this.font.lineHeight + TITLE_EXTRA;
	}

	private void rebuildPages() {
		pages.clear();
		indexEntries.clear();

		List<GuideBookContent.GuideSection> sections = new ArrayList<>(GuideBookContent.wikiSections());
		sections.add(GuideBookRecipes.section());

		List<Component> welcomePages = List.of();
		List<GuideBookContent.GuideSection> bodySections = new ArrayList<>();
		for (GuideBookContent.GuideSection section : sections) {
			if (GuideBookContent.WELCOME_SECTION_ID.equals(section.id())) {
				welcomePages = section.pages();
			} else {
				bodySections.add(section);
			}
		}

		for (Component source : welcomePages) {
			addSplitPages(source, null);
		}

		indexPageIndex = pages.size();
		pages.add(GuidePage.createIndex());

		indexEntries.add(new IndexEntry(
				Component.translatable("gui.siegedempires.guide.section.welcome").getString(),
				0));

		for (GuideBookContent.GuideSection section : bodySections) {
			int startPage = pages.size();
			indexEntries.add(new IndexEntry(section.title(), startPage));
			for (Component source : section.pages()) {
				addSplitPages(source, section.id());
			}
		}

		if (pages.isEmpty()) {
			pages.add(new GuidePage(List.of(PageLine.textOnly(
					this.font.split(Component.literal("This guide is empty.").withStyle(PAGE_TEXT_STYLE), TEXT_WIDTH).getFirst())),
					false, null));
			indexPageIndex = -1;
		}
		recomputeIndexMaxScroll();
	}

	private void addSplitPages(Component source, String sectionId) {
		List<PageLine> current = new ArrayList<>();
		int currentHeight = 0;
		List<Segment> segments = flatten(source, Style.EMPTY);
		for (ContentBlock block : groupIntoBlocks(wrapSegments(segments))) {
			int blockH = blockHeight(block);
			if (currentHeight > 0 && currentHeight + blockH > TEXT_HEIGHT) {
				pages.add(new GuidePage(List.copyOf(current), false, sectionId));
				current = new ArrayList<>();
				currentHeight = 0;
			}
			// Named crafting recipes must never split (title on one page, grid on the next).
			if (block.keepTogether()) {
				current.addAll(block.lines());
				currentHeight += blockH;
			} else if (blockH > TEXT_HEIGHT) {
				for (PageLine line : block.lines()) {
					int lineH = lineHeight(line);
					if (currentHeight > 0 && currentHeight + lineH > TEXT_HEIGHT) {
						pages.add(new GuidePage(List.copyOf(current), false, sectionId));
						current = new ArrayList<>();
						currentHeight = 0;
					}
					current.add(line);
					currentHeight += lineH;
				}
			} else {
				current.addAll(block.lines());
				currentHeight += blockH;
			}
		}
		if (!current.isEmpty()) {
			pages.add(new GuidePage(List.copyOf(current), false, sectionId));
		}
	}

	private void recomputeIndexMaxScroll() {
		int listH = Math.max(1, TEXT_HEIGHT - this.font.lineHeight - 8);
		int contentH = indexEntries.isEmpty()
				? 0
				: indexEntries.size() * (INDEX_ROW_HEIGHT + INDEX_ROW_GAP) - INDEX_ROW_GAP;
		indexMaxScroll = Math.max(0, contentH - listH);
		clampIndexScroll();
	}

	private int indexListTop() {
		return textY + this.font.lineHeight + 8;
	}

	private int indexListHeight() {
		return Math.max(1, TEXT_HEIGHT - this.font.lineHeight - 8);
	}

	private void clampIndexScroll() {
		indexScroll = Mth.clamp(indexScroll, 0, indexMaxScroll);
	}

	private List<ContentBlock> groupIntoBlocks(List<PageLine> lines) {
		List<ContentBlock> blocks = new ArrayList<>();
		List<PageLine> recipeLines = null;
		for (PageLine line : lines) {
			if (line.recipeBlockStart()) {
				if (recipeLines != null && !recipeLines.isEmpty()) {
					blocks.add(new ContentBlock(List.copyOf(recipeLines), true));
				}
				recipeLines = new ArrayList<>();
				PageLine stripped = line.withoutRecipeMarkers();
				if (!stripped.isEmpty()) {
					recipeLines.add(stripped);
				}
				continue;
			}
			if (line.recipeBlockEnd()) {
				PageLine stripped = line.withoutRecipeMarkers();
				if (recipeLines == null) {
					recipeLines = new ArrayList<>();
				}
				if (!stripped.isEmpty()) {
					recipeLines.add(stripped);
				}
				if (!recipeLines.isEmpty()) {
					blocks.add(new ContentBlock(List.copyOf(recipeLines), true));
				}
				recipeLines = null;
				continue;
			}
			if (recipeLines != null) {
				recipeLines.add(line);
			} else {
				blocks.add(new ContentBlock(List.of(line), false));
			}
		}
		if (recipeLines != null && !recipeLines.isEmpty()) {
			blocks.add(new ContentBlock(List.copyOf(recipeLines), true));
		}
		return blocks;
	}

	private int blockHeight(ContentBlock block) {
		int total = 0;
		for (PageLine line : block.lines()) {
			total += lineHeight(line);
		}
		return total;
	}

	private List<Segment> flatten(Component component, Style parent) {
		List<Segment> out = new ArrayList<>();
		flattenInto(component, parent, out);
		return out;
	}

	private void flattenInto(Component component, Style parent, List<Segment> out) {
		Style style = component.getStyle().applyTo(parent);
		ComponentContents contents = component.getContents();
		if (contents instanceof PlainTextContents plain) {
			String text = plain.text();
			if (!text.isEmpty()) {
				parseTextWithMarkers(text, style, out);
			}
		}
		for (Component sibling : component.getSiblings()) {
			flattenInto(sibling, style, out);
		}
	}

	private void parseTextWithMarkers(String text, Style style, List<Segment> out) {
		Matcher matcher = PatternBundle.COMBINED.matcher(text);
		int cursor = 0;
		while (matcher.find()) {
			if (matcher.start() > cursor) {
				appendPlain(text.substring(cursor, matcher.start()), style, out);
			}
			String matched = matcher.group();
			if (matched.equals(GuideBookContent.RECIPE_BLOCK_START)) {
				out.add(Segment.recipeBlockStart());
			} else if (matched.equals(GuideBookContent.RECIPE_BLOCK_END)) {
				out.add(Segment.recipeBlockEnd());
			} else if (matched.startsWith("{{crafting:")) {
				Matcher craft = GuideBookContent.CRAFTING_MARKER.matcher(matched);
				craft.matches();
				out.add(Segment.crafting(parseCrafting(craft.group(1), craft.group(2))));
			} else if (matched.startsWith("{{section_header:")) {
				Matcher header = GuideBookContent.SECTION_HEADER_MARKER.matcher(matched);
				header.matches();
				out.add(Segment.sectionHeader(
						resolveItem(header.group(1), header.group(2), Integer.parseInt(header.group(3))),
						header.group(4)));
			} else if (matched.startsWith("{{cycle_item:")) {
				Matcher cycle = GuideBookContent.CYCLE_ITEM_MARKER.matcher(matched);
				cycle.matches();
				out.add(Segment.cycle(resolveCycle(cycle.group(1))));
			} else if (matched.startsWith("{{item:")) {
				Matcher item = GuideBookContent.ITEM_MARKER.matcher(matched);
				item.matches();
				out.add(Segment.item(resolveItem(item.group(1), item.group(2))));
			}
			cursor = matcher.end();
		}
		if (cursor < text.length()) {
			appendPlain(text.substring(cursor), style, out);
		}
	}

	private List<ItemStack> resolveCycle(String joined) {
		List<ItemStack> stacks = new ArrayList<>();
		if (joined == null || joined.isBlank()) {
			stacks.add(new ItemStack(Items.BARRIER));
			return stacks;
		}
		for (String part : joined.split("\\|")) {
			String id = part.strip();
			int colon = id.indexOf(':');
			if (colon <= 0) {
				stacks.add(new ItemStack(Items.BARRIER));
				continue;
			}
			stacks.add(resolveItem(id.substring(0, colon), id.substring(colon + 1)));
		}
		if (stacks.isEmpty()) {
			stacks.add(new ItemStack(Items.BARRIER));
		}
		return stacks;
	}

	private CraftingRecipe parseCrafting(String cellsJoined, String resultSpec) {
		String[] rawCells = cellsJoined.split(",", -1);
		List<CraftingCell> cells = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			String raw = i < rawCells.length ? rawCells[i].strip() : GuideBookContent.EMPTY_CRAFT_CELL;
			cells.add(parseCraftCell(raw));
		}
		return new CraftingRecipe(List.copyOf(cells), parseCraftCell(resultSpec == null ? "" : resultSpec.strip()));
	}

	private CraftingCell parseCraftCell(String raw) {
		if (raw == null || raw.isEmpty() || GuideBookContent.EMPTY_CRAFT_CELL.equals(raw)) {
			return CraftingCell.empty();
		}
		if (raw.startsWith("cycle:")) {
			String body = raw.substring("cycle:".length());
			List<ItemStack> stacks = new ArrayList<>();
			for (String part : body.split("\\+")) {
				stacks.add(resolveItemId(part.strip()));
			}
			if (stacks.isEmpty()) {
				stacks.add(new ItemStack(Items.BARRIER));
			}
			return CraftingCell.cycle(stacks);
		}
		return CraftingCell.item(resolveItemId(raw));
	}

	private ItemStack resolveItemId(String id) {
		if (id == null || id.isBlank()) {
			return new ItemStack(Items.BARRIER);
		}
		int count = 1;
		int star = id.lastIndexOf('*');
		if (star > 0) {
			try {
				count = Integer.parseInt(id.substring(star + 1));
				id = id.substring(0, star);
			} catch (NumberFormatException ignored) {
				count = 1;
			}
		}
		int colon = id.indexOf(':');
		if (colon <= 0) {
			return resolveItem("minecraft", id, count);
		}
		return resolveItem(id.substring(0, colon), id.substring(colon + 1), count);
	}

	private void appendPlain(String text, Style style, List<Segment> out) {
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				if (i > start) {
					out.add(Segment.text(text.substring(start, i), style));
				}
				out.add(Segment.newline());
				start = i + 1;
			}
		}
		if (start < text.length()) {
			out.add(Segment.text(text.substring(start), style));
		}
	}

	private ItemStack resolveItem(String namespace, String path) {
		return resolveItem(namespace, path, 1);
	}

	private ItemStack resolveItem(String namespace, String path, int count) {
		Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		return new ItemStack(item.orElse(Items.BARRIER), Math.max(1, count));
	}

	private List<PageLine> wrapSegments(List<Segment> segments) {
		List<PageLine> lines = new ArrayList<>();
		List<LinePart> current = new ArrayList<>();
		int currentWidth = 0;
		boolean recipeStartPending = false;
		boolean recipeEndPending = false;

		for (Segment segment : segments) {
			switch (segment.kind()) {
				case RECIPE_BLOCK_START -> recipeStartPending = true;
				case RECIPE_BLOCK_END -> recipeEndPending = true;
				case CRAFTING -> {
					if (!current.isEmpty() || recipeStartPending) {
						lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
						current.clear();
						currentWidth = 0;
						recipeStartPending = false;
					}
					lines.add(PageLine.crafting(segment.crafting()));
				}
				case SECTION_HEADER -> {
					if (!current.isEmpty() || recipeStartPending) {
						lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
						current.clear();
						currentWidth = 0;
						recipeStartPending = false;
					}
					lines.add(PageLine.sectionHeader(segment.stack(), segment.text()));
				}
				case NEWLINE -> {
					lines.add(new PageLine(List.copyOf(current), recipeStartPending, recipeEndPending, null, null));
					current.clear();
					currentWidth = 0;
					recipeStartPending = false;
					recipeEndPending = false;
				}
				case ITEM, CYCLE -> {
					int need = ITEM_SIZE + ITEM_GAP;
					if (currentWidth > 0 && currentWidth + need > TEXT_WIDTH) {
						lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
						current.clear();
						currentWidth = 0;
						recipeStartPending = false;
					}
					if (segment.kind() == SegmentKind.CYCLE) {
						current.add(LinePart.cycle(segment.cycleStacks()));
					} else {
						current.add(LinePart.item(segment.stack()));
					}
					currentWidth += need;
				}
				case TEXT -> {
					String remaining = segment.text();
					Style style = segment.style().applyTo(PAGE_TEXT_STYLE);
					while (!remaining.isEmpty()) {
						int available = TEXT_WIDTH - currentWidth;
						if (available <= 0) {
							lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
							current.clear();
							currentWidth = 0;
							recipeStartPending = false;
							available = TEXT_WIDTH;
						}
						int fit = fitChars(remaining, style, available);
						if (fit <= 0) {
							if (currentWidth > 0) {
								lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
								current.clear();
								currentWidth = 0;
								recipeStartPending = false;
								continue;
							}
							fit = Math.max(1, firstCodePointLen(remaining));
						}
						String chunk = remaining.substring(0, fit);
						FormattedCharSequence ordered = Component.literal(chunk).withStyle(style).getVisualOrderText();
						current.add(LinePart.text(ordered));
						currentWidth += this.font.width(ordered);
						remaining = remaining.substring(fit);
						if (!remaining.isEmpty()) {
							lines.add(new PageLine(List.copyOf(current), recipeStartPending, false, null, null));
							current.clear();
							currentWidth = 0;
							recipeStartPending = false;
						}
					}
				}
			}
		}
		if (!current.isEmpty() || recipeStartPending || recipeEndPending) {
			lines.add(new PageLine(List.copyOf(current), recipeStartPending, recipeEndPending, null, null));
		}
		return lines;
	}

	private int fitChars(String text, Style style, int maxWidth) {
		if (maxWidth <= 0) {
			return 0;
		}
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) / 2;
			int width = this.font.width(Component.literal(text.substring(0, mid)).withStyle(style));
			if (width <= maxWidth) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		if (low < text.length() && low > 0) {
			int breakAt = findBreak(text, low);
			if (breakAt > 0) {
				return breakAt;
			}
		}
		return low;
	}

	private static int findBreak(String text, int maxExclusive) {
		for (int i = maxExclusive; i > 0; i--) {
			char c = text.charAt(i - 1);
			if (c == ' ' || c == '-' || c == '/') {
				return i;
			}
		}
		return 0;
	}

	private static int firstCodePointLen(String text) {
		return Character.charCount(text.codePointAt(0));
	}

	private int lineHeight(PageLine line) {
		if (line.sectionHeader() != null) {
			return sectionHeaderHeight(line.sectionHeader().title());
		}
		if (line.crafting() != null) {
			return CRAFT_HEIGHT + CRAFT_AFTER_GAP;
		}
		boolean hasItem = false;
		for (LinePart part : line.parts()) {
			if (part.kind() == LinePartKind.ITEM || part.kind() == LinePartKind.CYCLE) {
				hasItem = true;
				break;
			}
		}
		int textH = this.font.lineHeight + LINE_GAP;
		return hasItem ? Math.max(textH, ITEM_SIZE + LINE_GAP) : textH;
	}

	private void turnPage(int delta) {
		int next = currentPage + delta;
		if (next < 0 || next >= pages.size()) {
			return;
		}
		currentPage = next;
		if (isIndexPage()) {
			clampIndexScroll();
		}
		updateButtonState();
	}

	private void goToIndex() {
		if (indexPageIndex >= 0 && indexPageIndex < pages.size()) {
			currentPage = indexPageIndex;
			clampIndexScroll();
			updateButtonState();
		}
	}

	private void jumpToPage(int page) {
		if (page < 0 || page >= pages.size()) {
			return;
		}
		currentPage = page;
		updateButtonState();
	}

	private boolean isIndexPage() {
		return indexPageIndex >= 0 && currentPage == indexPageIndex;
	}

	private void updateButtonState() {
		if (backButton != null) {
			backButton.active = currentPage > 0;
		}
		if (forwardButton != null) {
			forwardButton.active = currentPage < pages.size() - 1;
		}
		if (indexButton != null) {
			indexButton.active = indexPageIndex >= 0;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) {
			turnPage(-1);
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
			turnPage(1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int listTop = indexListTop();
		int listBottom = listTop + indexListHeight();
		if (isIndexPage()
				&& mouseX >= textX && mouseX < textX + TEXT_WIDTH
				&& mouseY >= listTop && mouseY < listBottom) {
			indexScroll = Mth.clamp(indexScroll - (int) (scrollY * 14), 0, indexMaxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (isIndexPage() && event.button() == 0) {
			int hit = indexHitAt(event.x(), event.y());
			if (hit >= 0) {
				jumpToPage(indexEntries.get(hit).pageIndex());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int indexHitAt(double mouseX, double mouseY) {
		int listTop = indexListTop();
		int listBottom = listTop + indexListHeight();
		int listWidth = TEXT_WIDTH - (indexMaxScroll > 0 ? SCROLLBAR_WIDTH + 6 : 0);
		if (mouseX < textX || mouseX >= textX + listWidth
				|| mouseY < listTop || mouseY >= listBottom) {
			return -1;
		}
		int y = listTop - indexScroll;
		for (int i = 0; i < indexEntries.size(); i++) {
			int rowBottom = y + INDEX_ROW_HEIGHT;
			if (mouseY >= Math.max(y, listTop) && mouseY < Math.min(rowBottom, listBottom)) {
				return i;
			}
			y += INDEX_ROW_HEIGHT + INDEX_ROW_GAP;
		}
		return -1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xC0101010);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		layoutWidgets();

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xF2F0E6D2);
		graphics.outline(panelX, panelY, PANEL_WIDTH, panelHeight, 0xFF5A4632);
		graphics.outline(panelX + 1, panelY + 1, PANEL_WIDTH - 2, panelHeight - 2, 0xFFC4A882);

		Component title = this.getTitle().copy().withStyle(PAGE_TEXT_STYLE);
		graphics.text(this.font, title,
				panelX + (PANEL_WIDTH - this.font.width(title)) / 2,
				panelY + PANEL_PAD - 2, PAGE_TEXT_COLOR, false);

		if (isIndexPage()) {
			drawIndex(graphics, mouseX, mouseY);
		} else {
			drawContentPage(graphics);
		}

		Component pageMsg = Component.translatable(
				"gui.siegedempires.guide.page",
				currentPage + 1,
				pages.size()).withStyle(PAGE_TEXT_STYLE);
		graphics.text(this.font, pageMsg,
				panelX + (PANEL_WIDTH - this.font.width(pageMsg)) / 2,
				pageNumberY(),
				PAGE_TEXT_COLOR, false);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawContentPage(GuiGraphicsExtractor graphics) {
		int y = textY;
		int bottom = textY + TEXT_HEIGHT;
		GuidePage page = pages.get(currentPage);
		for (PageLine line : page.lines()) {
			int height = lineHeight(line);
			if (y + height - LINE_GAP > bottom) {
				break;
			}
			drawLine(graphics, line, textX, y, height);
			y += height;
		}
	}

	private void drawIndex(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component heading = Component.translatable("gui.siegedempires.guide.index_title").withStyle(PAGE_TEXT_STYLE);
		graphics.text(this.font, heading, textX, textY, PAGE_TEXT_COLOR, false);

		int listTop = indexListTop();
		int listH = indexListHeight();
		int listBottom = listTop + listH;
		int listWidth = TEXT_WIDTH - (indexMaxScroll > 0 ? SCROLLBAR_WIDTH + 6 : 0);
		int y = listTop - indexScroll;
		int hit = indexHitAt(mouseX, mouseY);
		for (int i = 0; i < indexEntries.size(); i++) {
			int rowTop = y;
			int rowBottom = y + INDEX_ROW_HEIGHT;
			boolean visible = rowBottom > listTop && rowTop < listBottom;
			if (visible) {
				int drawTop = Math.max(rowTop, listTop);
				int drawBottom = Math.min(rowBottom, listBottom);
				boolean hovered = i == hit;
				int bg = hovered ? 0xFFD4C4A8 : 0xFFE8DCC8;
				graphics.fill(textX, drawTop, textX + listWidth, drawBottom, bg);
				if (rowTop >= listTop && rowBottom <= listBottom) {
					graphics.outline(textX, rowTop, listWidth, INDEX_ROW_HEIGHT, 0xFF8A7050);
					String label = (i + 1) + ". " + indexEntries.get(i).title();
					graphics.text(this.font, Component.literal(label).withStyle(PAGE_TEXT_STYLE),
							textX + 6, rowTop + (INDEX_ROW_HEIGHT - this.font.lineHeight) / 2,
							PAGE_TEXT_COLOR, false);
				}
			}
			y += INDEX_ROW_HEIGHT + INDEX_ROW_GAP;
		}
		drawIndexScrollbar(graphics, listTop, listH);
	}

	private void drawIndexScrollbar(GuiGraphicsExtractor graphics, int listTop, int listH) {
		if (indexMaxScroll <= 0) {
			return;
		}
		int barX = textX + TEXT_WIDTH - SCROLLBAR_WIDTH;
		graphics.fill(barX, listTop, barX + SCROLLBAR_WIDTH, listTop + listH, 0xFFB8A890);
		int thumbH = Math.max(16, listH * listH / (listH + indexMaxScroll));
		int travel = listH - thumbH;
		int thumbY = listTop + (indexMaxScroll == 0 ? 0 : indexScroll * travel / indexMaxScroll);
		graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFF6A5640);
	}

	private void drawLine(GuiGraphicsExtractor graphics, PageLine line, int x, int y, int height) {
		if (line.sectionHeader() != null) {
			drawSectionHeader(graphics, line.sectionHeader(), x, y);
			return;
		}
		if (line.crafting() != null) {
			drawCraftingRecipe(graphics, line.crafting(), x, y);
			return;
		}
		int cursorX = x;
		int textBaselineY = y + Math.max(0, (height - LINE_GAP - this.font.lineHeight) / 2);
		int itemY = y + Math.max(0, (height - LINE_GAP - ITEM_SIZE) / 2);
		for (LinePart part : line.parts()) {
			if (part.kind() == LinePartKind.TEXT) {
				graphics.text(this.font, part.text(), cursorX, textBaselineY, PAGE_TEXT_COLOR, false);
				cursorX += this.font.width(part.text());
			} else {
				ItemStack stack = part.kind() == LinePartKind.CYCLE
						? pickCycle(part.cycleStacks())
						: part.stack();
				drawScaledItem(graphics, stack, cursorX, itemY);
				cursorX += ITEM_SIZE + ITEM_GAP;
			}
		}
	}

	private void drawCraftingRecipe(GuiGraphicsExtractor graphics, CraftingRecipe recipe, int x, int y) {
		int panelLeft = x + Math.max(0, (TEXT_WIDTH - CRAFT_WIDTH) / 2);
		int panelTop = y;
		int panelRight = panelLeft + CRAFT_WIDTH;
		int panelBottom = panelTop + CRAFT_HEIGHT;
		graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFFC6C6C6);
		graphics.outline(panelLeft, panelTop, CRAFT_WIDTH, CRAFT_HEIGHT, 0xFF373737);

		int gridX = panelLeft + CRAFT_PAD;
		int gridY = panelTop + CRAFT_PAD + Math.max(0, (CRAFT_GRID < CRAFT_RESULT_SLOT
				? (CRAFT_RESULT_SLOT - CRAFT_GRID) / 2 : 0));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				int slotX = gridX + col * CRAFT_SLOT;
				int slotY = gridY + row * CRAFT_SLOT;
				drawInventorySlot(graphics, slotX, slotY, CRAFT_SLOT);
				CraftingCell cell = recipe.ingredients().get(row * 3 + col);
				ItemStack stack = cell.current();
				if (!stack.isEmpty()) {
					drawSlotItem(graphics, stack, slotX, slotY, CRAFT_SLOT);
				}
			}
		}

		int arrowX = gridX + CRAFT_GRID + CRAFT_ARROW_GAP;
		int arrowY = panelTop + (CRAFT_HEIGHT - this.font.lineHeight) / 2;
		graphics.text(this.font, Component.literal("→").withStyle(PAGE_TEXT_STYLE),
				arrowX + (CRAFT_ARROW_WIDTH - this.font.width("→")) / 2,
				arrowY, PAGE_TEXT_COLOR, false);

		int resultX = arrowX + CRAFT_ARROW_WIDTH + CRAFT_ARROW_GAP;
		int resultY = panelTop + (CRAFT_HEIGHT - CRAFT_RESULT_SLOT) / 2;
		drawInventorySlot(graphics, resultX, resultY, CRAFT_RESULT_SLOT);
		ItemStack result = recipe.result().current();
		if (!result.isEmpty()) {
			drawSlotItem(graphics, result, resultX, resultY, CRAFT_RESULT_SLOT);
		}
	}

	private void drawInventorySlot(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.fill(x, y, x + size, y + size, 0xFF8B8B8B);
		graphics.fill(x, y, x + size - 1, y + 1, 0xFF373737);
		graphics.fill(x, y, x + 1, y + size - 1, 0xFF373737);
		graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B8B);
		graphics.fill(x + 1, y + 1, x + size - 2, y + size - 2, 0xFF373737);
	}

	private void drawSlotItem(GuiGraphicsExtractor graphics, ItemStack stack, int slotX, int slotY, int slotSize) {
		int pad = Math.max(1, (slotSize - ITEM_BASE) / 2);
		int itemX = slotX + pad;
		int itemY = slotY + pad;
		if (slotSize < ITEM_BASE + 2) {
			float scale = (slotSize - 2) / (float) ITEM_BASE;
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(slotX + 1, slotY + 1);
			pose.scale(scale, scale);
			graphics.item(stack, 0, 0);
			pose.popMatrix();
		} else {
			graphics.item(stack, itemX, itemY);
		}
	}

	private static ItemStack pickCycle(List<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return new ItemStack(Items.BARRIER);
		}
		int index = (int) ((System.currentTimeMillis() / CYCLE_MS) % stacks.size());
		return stacks.get(index);
	}

	private void drawScaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		drawScaledItem(graphics, stack, x, y, ITEM_SCALE);
	}

	private void drawScaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float scale) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.item(stack, 0, 0);
		pose.popMatrix();
	}

	private int sectionHeaderHeight(String title) {
		int titleWidth = Math.max(1, TEXT_WIDTH - HEADER_ITEM_SIZE - HEADER_GAP);
		List<FormattedCharSequence> lines = this.font.split(
				Component.literal(title).withStyle(Style.EMPTY.withBold(true).applyTo(PAGE_TEXT_STYLE)),
				titleWidth);
		int textHeight = lines.size() * (this.font.lineHeight + LINE_GAP);
		if (!lines.isEmpty()) {
			textHeight -= LINE_GAP;
		}
		return Math.max(HEADER_ITEM_SIZE, textHeight) + LINE_GAP;
	}

	private void drawSectionHeader(GuiGraphicsExtractor graphics, SectionHeader header, int x, int y) {
		drawScaledItem(graphics, header.stack(), x, y, HEADER_ITEM_SCALE);
		int titleX = x + HEADER_ITEM_SIZE + HEADER_GAP;
		int titleWidth = Math.max(1, TEXT_WIDTH - HEADER_ITEM_SIZE - HEADER_GAP);
		Style titleStyle = Style.EMPTY.withBold(true).applyTo(PAGE_TEXT_STYLE);
		List<FormattedCharSequence> titleLines = this.font.split(
				Component.literal(header.title()).withStyle(titleStyle), titleWidth);
		int textBlockHeight = titleLines.isEmpty()
				? 0
				: titleLines.size() * (this.font.lineHeight + LINE_GAP) - LINE_GAP;
		int textY = y + Math.max(0, (HEADER_ITEM_SIZE - textBlockHeight) / 2);
		for (FormattedCharSequence line : titleLines) {
			graphics.text(this.font, line, titleX, textY, PAGE_TEXT_COLOR, false);
			textY += this.font.lineHeight + LINE_GAP;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	private static final class PatternBundle {
		static final java.util.regex.Pattern COMBINED = java.util.regex.Pattern.compile(
				GuideBookContent.CRAFTING_MARKER.pattern()
						+ "|" + GuideBookContent.SECTION_HEADER_MARKER.pattern()
						+ "|" + GuideBookContent.CYCLE_ITEM_MARKER.pattern()
						+ "|" + GuideBookContent.ITEM_MARKER.pattern()
						+ "|\\Q" + GuideBookContent.RECIPE_BLOCK_START + "\\E"
						+ "|\\Q" + GuideBookContent.RECIPE_BLOCK_END + "\\E");
	}

	private enum SegmentKind {
		TEXT,
		ITEM,
		CYCLE,
		NEWLINE,
		CRAFTING,
		SECTION_HEADER,
		RECIPE_BLOCK_START,
		RECIPE_BLOCK_END
	}

	private record Segment(
			SegmentKind kind,
			String text,
			Style style,
			ItemStack stack,
			List<ItemStack> cycleStacks,
			CraftingRecipe crafting) {
		static Segment text(String text, Style style) {
			return new Segment(SegmentKind.TEXT, text, style, ItemStack.EMPTY, List.of(), null);
		}

		static Segment item(ItemStack stack) {
			return new Segment(SegmentKind.ITEM, "", Style.EMPTY, stack, List.of(), null);
		}

		static Segment cycle(List<ItemStack> stacks) {
			return new Segment(SegmentKind.CYCLE, "", Style.EMPTY, ItemStack.EMPTY, List.copyOf(stacks), null);
		}

		static Segment newline() {
			return new Segment(SegmentKind.NEWLINE, "", Style.EMPTY, ItemStack.EMPTY, List.of(), null);
		}

		static Segment crafting(CraftingRecipe recipe) {
			return new Segment(SegmentKind.CRAFTING, "", Style.EMPTY, ItemStack.EMPTY, List.of(), recipe);
		}

		static Segment sectionHeader(ItemStack stack, String title) {
			return new Segment(SegmentKind.SECTION_HEADER, title, Style.EMPTY, stack, List.of(), null);
		}

		static Segment recipeBlockStart() {
			return new Segment(SegmentKind.RECIPE_BLOCK_START, "", Style.EMPTY, ItemStack.EMPTY, List.of(), null);
		}

		static Segment recipeBlockEnd() {
			return new Segment(SegmentKind.RECIPE_BLOCK_END, "", Style.EMPTY, ItemStack.EMPTY, List.of(), null);
		}
	}

	private enum LinePartKind {
		TEXT,
		ITEM,
		CYCLE
	}

	private record LinePart(LinePartKind kind, FormattedCharSequence text, ItemStack stack, List<ItemStack> cycleStacks) {
		static LinePart text(FormattedCharSequence text) {
			return new LinePart(LinePartKind.TEXT, text, ItemStack.EMPTY, List.of());
		}

		static LinePart item(ItemStack stack) {
			return new LinePart(LinePartKind.ITEM, FormattedCharSequence.EMPTY, stack, List.of());
		}

		static LinePart cycle(List<ItemStack> stacks) {
			return new LinePart(LinePartKind.CYCLE, FormattedCharSequence.EMPTY, ItemStack.EMPTY, List.copyOf(stacks));
		}
	}

	private record PageLine(
			List<LinePart> parts,
			boolean recipeBlockStart,
			boolean recipeBlockEnd,
			CraftingRecipe crafting,
			SectionHeader sectionHeader) {
		static PageLine textOnly(FormattedCharSequence text) {
			return new PageLine(List.of(LinePart.text(text)), false, false, null, null);
		}

		static PageLine crafting(CraftingRecipe recipe) {
			return new PageLine(List.of(), false, false, recipe, null);
		}

		static PageLine sectionHeader(ItemStack stack, String title) {
			return new PageLine(List.of(), false, false, null, new SectionHeader(stack, title));
		}

		boolean isEmpty() {
			return parts.isEmpty() && crafting == null && sectionHeader == null;
		}

		PageLine withoutRecipeMarkers() {
			return new PageLine(parts, false, false, crafting, sectionHeader);
		}
	}

	private record SectionHeader(ItemStack stack, String title) {
	}

	private record CraftingCell(List<ItemStack> stacks) {
		static CraftingCell empty() {
			return new CraftingCell(List.of());
		}

		static CraftingCell item(ItemStack stack) {
			return new CraftingCell(List.of(stack));
		}

		static CraftingCell cycle(List<ItemStack> stacks) {
			return new CraftingCell(List.copyOf(stacks));
		}

		ItemStack current() {
			if (stacks.isEmpty()) {
				return ItemStack.EMPTY;
			}
			if (stacks.size() == 1) {
				return stacks.getFirst();
			}
			return pickCycle(stacks);
		}
	}

	private record CraftingRecipe(List<CraftingCell> ingredients, CraftingCell result) {
	}

	private record ContentBlock(List<PageLine> lines, boolean keepTogether) {
	}

	private record GuidePage(List<PageLine> lines, boolean indexPage, String sectionId) {
		static GuidePage createIndex() {
			return new GuidePage(List.of(), true, GuideBookContent.INDEX_SECTION_ID);
		}
	}

	private record IndexEntry(String title, int pageIndex) {
	}
}
