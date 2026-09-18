package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.client.session.SessionJoinCinematic;
import com.siegedempires.client.title.WorldEntryFade;
import com.siegedempires.network.DiplomacyData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

/**
 * Post-world-load session menu. Opaque so the spectator world is never visible;
 * Join starts the enter-world cinematic. Black fades are drawn here so they
 * react immediately over the menu (HUD alone sits under screens).
 * <p>
 * Element placement follows {@code gamemenudemo.png} (1920×1080 reference).
 */
public class GameMenuScreen extends Screen {
	/** Layout fractions from gamemenudemo.png @ 1920×1080. */
	private static final float PANEL_LEFT_FRAC = 0.0563F;
	private static final float PANEL_TOP_FRAC = 0.0926F;
	private static final float PANEL_WIDTH_FRAC = 0.1495F;
	private static final float PANEL_HEIGHT_FRAC = 0.7676F;
	private static final float PANEL_GAP_FRAC = 0.0219F;

	private static final float FLAG_WIDTH_FRAC = 0.0865F;
	private static final float FLAG_HEIGHT_FRAC = 0.3194F;
	private static final float FLAG_AFTER_PANELS_GAP_FRAC = 0.0417F;
	private static final int TOWN_NAME_GAP = 10;

	private static final float JOIN_WIDTH_FRAC = 0.2800F;
	private static final float JOIN_HEIGHT_FRAC = 0.0800F;
	private static final float JOIN_CORRIDOR_GAP_FRAC = 0.015F;
	/** Nudge Join upward from corridor center (reference px @ 1080p). */
	private static final int JOIN_Y_OFFSET_UP = 80;

	private static final float PLAYER_NAME_TOP_FRAC = 0.1426F;
	private static final float PLAYER_SKIN_WIDTH_FRAC = 0.1417F;
	private static final float PLAYER_SKIN_HEIGHT_FRAC = 0.4907F;
	private static final float PLAYER_RIGHT_MARGIN_FRAC = 0.0411F;
	private static final int PLAYER_NAME_GAP = 6;

	private static final int GOLD = 0xFFFFAA00;

	private boolean joinStarted;
	private int alliesScroll;
	private int enemiesScroll;
	private int alliesPanelX;
	private int enemiesPanelX;
	private int sidePanelTop;
	private int sidePanelWidth;
	private int sidePanelHeight;
	private int joinX;
	private int joinY;
	private int joinWidth;
	private int joinHeight;
	private int headerContentX;
	private int headerNameX;
	private int headerNameY;
	private int flagWidth;
	private int flagHeight;

	private GameMenuAttentionButton joinButton;
	private Button mailButton;
	private final MailBoxWidget mailBox = new MailBoxWidget();

	/** Which side-panel scrollbar is being dragged, or 0 if none. */
	private static final int SCROLL_DRAG_NONE = 0;
	private static final int SCROLL_DRAG_ALLIES = 1;
	private static final int SCROLL_DRAG_ENEMIES = 2;
	private int scrollDragTarget = SCROLL_DRAG_NONE;

	public GameMenuScreen() {
		super(Component.translatable("gui.siegedempires.game_menu.title"));
	}

	@Override
	protected void init() {
		super.init();
		ClientGuiData.setGuiStatusListener(this::onGuiStatusUpdated);
		ClientGuiData.setDiplomacyListener(this::onDiplomacyUpdated);
		ClientGuiData.setMailListener(this::onMailUpdated);
		ClientNetworking.requestGuiStatus();
		ClientNetworking.requestDiplomacyData();
		ClientNetworking.requestMailData();

		layoutFromDemo();
		layoutHeader();

		Runnable openMail = () -> {
			if (minecraft != null) {
				minecraft.gui.setScreen(new MailScreen(this));
			}
		};
		mailBox.layout(this);
		if (mailButton != null) {
			this.removeWidget(mailButton);
		}
		mailButton = mailBox.createButton(openMail);
		mailButton.visible = true;
		this.addRenderableWidget(mailButton);

		// Reused GameMenu instances (e.g. after Mail) keep joinStarted; if the gate
		// still needs Join, reset and recreate the button so players are not stuck.
		if (SessionJoinCinematic.shouldShowJoinButton()) {
			joinStarted = false;
		}
		if (joinStarted || !SessionJoinCinematic.shouldShowJoinButton()) {
			return;
		}
		Component joinLabel = Component.translatable("gui.siegedempires.game_menu.join")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
		joinButton = new GameMenuAttentionButton(
				joinX, joinY, joinWidth, joinHeight,
				joinLabel, 1.5F,
				() -> true,
				button -> onJoin());
		this.addRenderableWidget(joinButton);
	}

	private void onGuiStatusUpdated() {
		layoutHeader();
		layoutFromDemo();
		repositionJoinButton();
	}

	private void onDiplomacyUpdated() {
		alliesScroll = 0;
		enemiesScroll = 0;
		layoutHeader();
		layoutFromDemo();
		repositionJoinButton();
	}

	private void onMailUpdated() {
		layoutFromDemo();
		if (mailBox != null) {
			mailBox.layout(this);
			if (mailButton != null) {
				mailBox.applyButtonBounds(mailButton);
			}
			repositionJoinButton();
		}
	}

	private void onJoin() {
		if (joinStarted) {
			return;
		}
		joinStarted = true;
		if (joinButton != null) {
			joinButton.active = false;
		}
		SessionJoinCinematic.startJoin();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.minecraft != null && SessionJoinCinematic.tryBreakGateOnKey(this.minecraft, event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		List<DiplomacyData.FactionInfo> allies = data != null && data.allies != null
				? data.allies : Collections.emptyList();
		List<DiplomacyData.FactionInfo> enemies = data != null && data.enemies != null
				? data.enemies : Collections.emptyList();

		if (DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, alliesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, allies);
			alliesScroll = DiplomacyPanelRenderer.scrollBy(alliesScroll, maxScroll, scrollY);
			return true;
		}
		if (DiplomacyPanelRenderer.isMouseOverPanel(
				mouseX, mouseY, enemiesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight)) {
			int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, enemies);
			enemiesScroll = DiplomacyPanelRenderer.scrollBy(enemiesScroll, maxScroll, scrollY);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && tryBeginScrollbarDrag(event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (event.button() == 0 && scrollDragTarget != SCROLL_DRAG_NONE) {
			applyScrollbarDrag(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && scrollDragTarget != SCROLL_DRAG_NONE) {
			scrollDragTarget = SCROLL_DRAG_NONE;
			return true;
		}
		return super.mouseReleased(event);
	}

	private boolean tryBeginScrollbarDrag(double mouseX, double mouseY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		List<DiplomacyData.FactionInfo> allies = data != null && data.allies != null
				? data.allies : Collections.emptyList();
		List<DiplomacyData.FactionInfo> enemies = data != null && data.enemies != null
				? data.enemies : Collections.emptyList();

		if (!allies.isEmpty()
				&& DiplomacyPanelRenderer.isMouseOverScrollbar(
				mouseX, mouseY, alliesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight, this.font)) {
			scrollDragTarget = SCROLL_DRAG_ALLIES;
			applyScrollbarDrag(mouseY);
			return true;
		}
		if (!enemies.isEmpty()
				&& DiplomacyPanelRenderer.isMouseOverScrollbar(
				mouseX, mouseY, enemiesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight, this.font)) {
			scrollDragTarget = SCROLL_DRAG_ENEMIES;
			applyScrollbarDrag(mouseY);
			return true;
		}
		return false;
	}

	private void applyScrollbarDrag(double mouseY) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		if (scrollDragTarget == SCROLL_DRAG_ALLIES) {
			List<DiplomacyData.FactionInfo> allies = data != null && data.allies != null
					? data.allies : Collections.emptyList();
			int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, allies);
			alliesScroll = DiplomacyPanelRenderer.scrollFromMouseY(
					mouseY, sidePanelTop, sidePanelHeight, this.font, maxScroll);
		} else if (scrollDragTarget == SCROLL_DRAG_ENEMIES) {
			List<DiplomacyData.FactionInfo> enemies = data != null && data.enemies != null
					? data.enemies : Collections.emptyList();
			int maxScroll = DiplomacyPanelRenderer.maxScroll(sidePanelHeight, enemies);
			enemiesScroll = DiplomacyPanelRenderer.scrollFromMouseY(
					mouseY, sidePanelTop, sidePanelHeight, this.font, maxScroll);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawBackground(graphics);

		layoutFromDemo();
		layoutHeader();
		repositionJoinButton();
		drawDiplomacySidePanels(graphics);
		drawTownEmpireHeader(graphics);
		drawPlayerPanel(graphics, mouseX, mouseY);
		if (mailBox != null) {
			mailBox.layout(this);
			if (mailButton != null) {
				mailBox.applyButtonBounds(mailButton);
			}
			mailBox.render(graphics, ClientGuiData.hasMail());
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawBlackOverlay(graphics);
	}

	/** Classic tiled cobblestone wall (replaces full-bleed {@code game_menu_background.png}). */
	private void drawBackground(GuiGraphicsExtractor graphics) {
		GameMenuBackground.draw(graphics, this.width, this.height);
	}

	/** Positions allies/enemies panels and the Join button in the center corridor. */
	private void layoutFromDemo() {
		sidePanelWidth = Math.max(80, Math.round(this.width * PANEL_WIDTH_FRAC));
		sidePanelTop = Math.round(this.height * PANEL_TOP_FRAC);
		alliesPanelX = Math.round(this.width * PANEL_LEFT_FRAC);
		if (mailBox != null) {
			mailBox.layout(this);
		}
		int gap = Math.max(8, Math.round(this.width * PANEL_GAP_FRAC));
		enemiesPanelX = alliesPanelX + sidePanelWidth + gap;

		int preferredHeight = Math.max(120, Math.round(this.height * PANEL_HEIGHT_FRAC));
		int maxPanelBottom = this.height - Math.max(12, Math.round(this.height * 0.08F));
		sidePanelHeight = Math.max(80, Math.min(preferredHeight, maxPanelBottom - sidePanelTop));

		joinWidth = Math.max(160, Math.round(this.width * JOIN_WIDTH_FRAC));
		joinHeight = Math.max(36, Math.round(this.height * JOIN_HEIGHT_FRAC));

		int enemiesRight = enemiesPanelX + sidePanelWidth;
		int playerLeft = playerPreviewLeft();
		int corridorGap = Math.max(12, Math.round(this.width * JOIN_CORRIDOR_GAP_FRAC));
		int corridorLeft = enemiesRight + corridorGap;
		int corridorRight = playerLeft - corridorGap;
		int corridorWidth = corridorRight - corridorLeft;
		if (joinWidth > corridorWidth) {
			joinWidth = Math.max(120, corridorWidth);
		}
		int corridorCenterX = (corridorLeft + corridorRight) / 2;
		joinX = corridorCenterX - joinWidth / 2;

		int sidePanelBottom = sidePanelTop + sidePanelHeight;
		int joinAreaBottom = this.height - Math.max(8, Math.round(this.height * 0.02F));
		int joinCenterY = (sidePanelBottom + joinAreaBottom) / 2;
		joinY = joinCenterY - joinHeight / 2 - JOIN_Y_OFFSET_UP;
	}

	private int playerPreviewLeft() {
		int skinWidth = Math.max(64, Math.round(this.width * PLAYER_SKIN_WIDTH_FRAC));
		int rightMargin = Math.max(12, Math.round(this.width * PLAYER_RIGHT_MARGIN_FRAC));
		return this.width - rightMargin - skinWidth;
	}

	private void layoutHeader() {
		int gap = Math.max(12, Math.round(this.width * FLAG_AFTER_PANELS_GAP_FRAC));
		headerContentX = enemiesPanelX + sidePanelWidth + gap;

		flagWidth = Math.max(40, Math.round(this.width * FLAG_WIDTH_FRAC));
		flagHeight = Math.max(80, Math.round(this.height * FLAG_HEIGHT_FRAC));
		int aspectHeight = flagWidth * 2;
		if (aspectHeight < flagHeight) {
			flagHeight = aspectHeight;
		}

		DiplomacyData data = ClientGuiData.getDiplomacyData();
		boolean inTown = isInTown(data);
		boolean drawFlag = inTown && data != null
				&& ((data.bannerBaseColor != null && !data.bannerBaseColor.isEmpty())
				|| (data.bannerPixels != null && !data.bannerPixels.isEmpty()));

		headerNameX = drawFlag ? headerContentX + flagWidth + TOWN_NAME_GAP : headerContentX;
		boolean hasEmpire = inTown && data != null
				&& data.empireName != null && !data.empireName.isEmpty();
		int textBlockHeight = hasEmpire
				? this.font.lineHeight * 2 + 4
				: this.font.lineHeight;
		if (drawFlag) {
			headerNameY = sidePanelTop + Math.min(flagHeight / 4, (flagHeight - textBlockHeight) / 2);
		} else {
			headerNameY = sidePanelTop;
		}
	}

	private void repositionJoinButton() {
		if (joinButton != null) {
			joinButton.setPosition(joinX, joinY);
			joinButton.setSize(joinWidth, joinHeight);
		}
	}

	private void drawDiplomacySidePanels(GuiGraphicsExtractor graphics) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		boolean inTown = isInTown(data);
		List<DiplomacyData.FactionInfo> allies = inTown && data != null && data.allies != null
				? data.allies : Collections.emptyList();
		List<DiplomacyData.FactionInfo> enemies = inTown && data != null && data.enemies != null
				? data.enemies : Collections.emptyList();

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				alliesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight,
				Component.translatable("gui.siegedempires.allies"),
				0xFF55FF55, allies, 0xFF55FF55, alliesScroll);

		DiplomacyPanelRenderer.drawPanel(graphics, this.font,
				enemiesPanelX, sidePanelTop, sidePanelWidth, sidePanelHeight,
				Component.translatable("gui.siegedempires.enemies"),
				0xFFFF5555, enemies, 0xFFFF5555, enemiesScroll);
	}

	private void drawTownEmpireHeader(GuiGraphicsExtractor graphics) {
		DiplomacyData data = ClientGuiData.getDiplomacyData();
		boolean inTown = isInTown(data);

		if (inTown && data != null
				&& ((data.bannerBaseColor != null && !data.bannerBaseColor.isEmpty())
				|| (data.bannerPixels != null && !data.bannerPixels.isEmpty()))) {
			BannerEditorWidgets.drawBannerFlag(graphics,
					data.bannerPatterns, data.bannerBaseColor, data.bannerPixels,
					headerContentX, sidePanelTop, flagWidth, flagHeight);
		}

		if (inTown && data != null) {
			String townName = resolveTownName(data);
			String empireName = data.empireName;
			boolean hasEmpire = empireName != null && !empireName.isEmpty();
			graphics.text(this.font, Component.literal(townName), headerNameX, headerNameY, 0xFFFFFFFF);
			if (hasEmpire) {
				graphics.text(this.font, Component.literal("[" + empireName + "]"),
						headerNameX, headerNameY + this.font.lineHeight + 4, GOLD);
			}
		} else {
			graphics.text(this.font,
					Component.translatable("gui.siegedempires.game_menu.not_in_town"),
					headerNameX, headerNameY, 0xFFFFFFFF);
		}
	}

	private void drawPlayerPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		Player player = this.minecraft.player;
		Component name = player.getName();
		int nameWidth = this.font.width(name);
		int skinWidth = Math.max(64, Math.round(this.width * PLAYER_SKIN_WIDTH_FRAC));
		int skinHeight = Math.max(120, Math.round(this.height * PLAYER_SKIN_HEIGHT_FRAC));
		int rightMargin = Math.max(12, Math.round(this.width * PLAYER_RIGHT_MARGIN_FRAC));
		int skinRight = this.width - rightMargin;
		int skinLeft = skinRight - skinWidth;
		int skinCenterX = (skinLeft + skinRight) / 2;
		int nameX = skinCenterX - nameWidth / 2;
		int nameY = Math.round(this.height * PLAYER_NAME_TOP_FRAC);
		if (mailBox != null) {
			mailBox.layout(this);
			// Keep the player name/skin below the always-visible top-right mail box.
			nameY = Math.max(nameY, mailBox.getBoxBottom() + 10);
		}
		graphics.text(this.font, name, nameX, nameY, 0xFFFFFFFF);

		int skinTop = nameY + this.font.lineHeight + PLAYER_NAME_GAP;
		int skinBottom = skinTop + skinHeight;
		int entityScale = Math.max(40, skinHeight * 2 / 5);
		// Session gate keeps the real player in spectator; only the GUI render
		// state is forced to a full opaque survival-style avatar.
		extractFullSkinFollowsMouse(
				graphics,
				skinLeft, skinTop, skinRight, skinBottom,
				entityScale,
				0.0625F,
				mouseX, mouseY,
				player
		);
	}

	/**
	 * Same as {@link net.minecraft.client.gui.screens.inventory.InventoryScreen#extractEntityInInventoryFollowsMouse},
	 * but forces a survival-style avatar: full body (not spectator head-only) and
	 * opaque (spectators are flagged invisible, which would tint the model translucent).
	 * Does not change the player's actual gamemode.
	 */
	private static void extractFullSkinFollowsMouse(
			GuiGraphicsExtractor graphics,
			int x0, int y0, int x1, int y1,
			int size,
			float offsetY,
			float mouseX, float mouseY,
			LivingEntity entity
	) {
		float centerX = (x0 + x1) / 2.0F;
		float centerY = (y0 + y1) / 2.0F;
		float xAngle = (float) Math.atan((centerX - mouseX) / 40.0F);
		float yAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
		rotation.mul(xRotation);

		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
		EntityRenderState renderState = renderer.createRenderState(entity, 1.0F);
		renderState.shadowPieces.clear();
		renderState.outlineColor = 0;
		// Spectator sets shared invisible flag → LivingEntityRenderer forceTransparent tint.
		renderState.isInvisible = false;

		if (renderState instanceof AvatarRenderState avatarRenderState) {
			avatarRenderState.isSpectator = false;
		}
		if (renderState instanceof LivingEntityRenderState livingRenderState) {
			livingRenderState.isInvisibleToPlayer = false;
			livingRenderState.bodyRot = 180.0F + xAngle * 20.0F;
			livingRenderState.yRot = xAngle * 20.0F;
			if (livingRenderState.pose != Pose.FALL_FLYING) {
				livingRenderState.xRot = -yAngle * 20.0F;
			} else {
				livingRenderState.xRot = 0.0F;
			}
			livingRenderState.boundingBoxWidth = livingRenderState.boundingBoxWidth / livingRenderState.scale;
			livingRenderState.boundingBoxHeight = livingRenderState.boundingBoxHeight / livingRenderState.scale;
			livingRenderState.scale = 1.0F;
		}

		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + offsetY, 0.0F);
		graphics.entity(renderState, size, translation, rotation, xRotation, x0, y0, x1, y1);
	}

	private static boolean isInTown(DiplomacyData data) {
		if (ClientGuiData.isInTown()) {
			return true;
		}
		if (data == null) {
			return false;
		}
		String town = resolveTownName(data);
		return town != null && !town.isEmpty();
	}

	private static String resolveTownName(DiplomacyData data) {
		if (data.townName != null && !data.townName.isEmpty()) {
			return data.townName;
		}
		// Fallback for older payloads / independent towns where entityName is the town.
		if (!"empire".equals(data.entityType) && data.entityName != null) {
			return data.entityName;
		}
		return "";
	}

	private void drawBlackOverlay(GuiGraphicsExtractor graphics) {
		float black = Math.max(WorldEntryFade.screenBlackAlpha(), SessionJoinCinematic.screenBlackAlpha());
		if (black <= 0.0F) {
			return;
		}
		int a = Mth.ceil(black * 255.0F);
		graphics.nextStratum();
		graphics.fill(0, 0, this.width, this.height, ARGB.color(a, 0, 0, 0));
	}

	@Override
	public void onClose() {
		ClientGuiData.setGuiStatusListener(null);
		ClientGuiData.setDiplomacyListener(null);
		ClientGuiData.clearMailListener();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
