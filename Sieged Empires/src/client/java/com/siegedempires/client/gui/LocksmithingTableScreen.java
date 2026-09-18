package com.siegedempires.client.gui;

import com.siegedempires.Siegedempires;
import com.siegedempires.block.LocksmithingTableMenu;
import com.siegedempires.item.KeyItem;
import com.siegedempires.item.LockItem;
import com.siegedempires.network.payload.LocksmithingUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;

/**
 * Locksmithing Table screen — anvil-style rename + password fields.
 * Layout matches {@code textures/gui/locksmithingtablegui.png} (176×186).
 */
public class LocksmithingTableScreen extends AbstractContainerScreen<LocksmithingTableMenu>
	implements ContainerListener {

	private static final Identifier TEXTURE =
		Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "textures/gui/locksmithingtablegui.png");

	/** Black rename well: x=40,y=10,w=128,h=16 → inset text field */
	private static final int RENAME_X = 42;
	private static final int RENAME_Y = 12;
	private static final int RENAME_W = 124;

	/** Black password well: x=8,y=74,w=160,h=16 → inset text field */
	private static final int PASSWORD_X = 10;
	private static final int PASSWORD_Y = 76;
	private static final int PASSWORD_W = 156;

	private static final int FIELD_H = 12;

	private EditBox renameField;
	private EditBox passwordField;
	private String lastSyncedRename = "";
	private String lastSyncedPassword = "";

	public LocksmithingTableScreen(LocksmithingTableMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 186);
		this.titleLabelY = -10000;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		this.renameField = new EditBox(this.font, leftPos + RENAME_X, topPos + RENAME_Y, RENAME_W, FIELD_H,
			Component.translatable("gui.siegedempires.rename"));
		this.renameField.setTextColor(-1);
		this.renameField.setTextColorUneditable(-1);
		this.renameField.setBordered(false);
		this.renameField.setMaxLength(50);
		this.renameField.setValue("");
		this.renameField.setResponder(this::onRenameChanged);
		this.addRenderableWidget(this.renameField);

		this.passwordField = new EditBox(this.font, leftPos + PASSWORD_X, topPos + PASSWORD_Y, PASSWORD_W, FIELD_H,
			Component.translatable("gui.siegedempires.key_password"));
		this.passwordField.setTextColor(-1);
		this.passwordField.setTextColorUneditable(-1);
		this.passwordField.setBordered(false);
		this.passwordField.setMaxLength(40);
		this.passwordField.setValue("");
		this.passwordField.setResponder(this::onPasswordChanged);
		this.addRenderableWidget(this.passwordField);

		this.menu.addSlotListener(this);
		syncFieldsFromInputSlot();
		this.setInitialFocus(this.renameField);
	}

	@Override
	public void removed() {
		super.removed();
		this.menu.removeSlotListener(this);
	}

	@Override
	public void resize(int width, int height) {
		String rename = this.renameField.getValue();
		String password = this.passwordField.getValue();
		this.init(width, height);
		this.renameField.setValue(rename);
		this.passwordField.setValue(password);
	}

	private void onRenameChanged(String text) {
		sendUpdateIfChanged();
	}

	private void onPasswordChanged(String text) {
		sendUpdateIfChanged();
	}

	private void sendUpdateIfChanged() {
		String rename = renameField.getValue();
		String password = passwordField.getValue();
		if (rename.equals(lastSyncedRename) && password.equals(lastSyncedPassword)) {
			return;
		}
		lastSyncedRename = rename;
		lastSyncedPassword = password;
		this.menu.applyClientFields(rename, password);
		ClientPlayNetworking.send(new LocksmithingUpdatePayload(rename, password));
	}

	private void syncFieldsFromInputSlot() {
		ItemStack input = this.menu.getInputStack();
		boolean hasItem = !input.isEmpty()
			&& (input.getItem() instanceof KeyItem || input.getItem() instanceof LockItem);

		this.renameField.setEditable(hasItem);
		this.passwordField.setEditable(hasItem);

		if (!hasItem) {
			setFieldsSilent("", "");
			this.menu.applyClientFields("", "");
			ClientPlayNetworking.send(new LocksmithingUpdatePayload("", ""));
			return;
		}

		String existingPw = input.getItem() instanceof KeyItem
			? KeyItem.getPassword(input)
			: LockItem.getPassword(input);
		String name = input.has(DataComponents.CUSTOM_NAME)
			? input.getHoverName().getString()
			: "";

		setFieldsSilent(name, existingPw);
		this.menu.applyClientFields(name, existingPw);
		ClientPlayNetworking.send(new LocksmithingUpdatePayload(name, existingPw));
	}

	private void setFieldsSilent(String rename, String password) {
		this.renameField.setResponder(s -> {});
		this.passwordField.setResponder(s -> {});
		this.renameField.setValue(rename);
		this.passwordField.setValue(password);
		this.renameField.setResponder(this::onRenameChanged);
		this.passwordField.setResponder(this::onPasswordChanged);
		this.lastSyncedRename = rename;
		this.lastSyncedPassword = password;
	}

	@Override
	public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack stack) {
		if (slotIndex == LocksmithingTableMenu.INPUT_SLOT_ID) {
			syncFieldsFromInputSlot();
		}
	}

	@Override
	public void dataChanged(AbstractContainerMenu container, int id, int value) {
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F,
			imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("gui.siegedempires.item_name"),
			this.leftPos + RENAME_X, this.topPos + 2, 0x404040, false);
		graphics.text(this.font, Component.translatable("gui.siegedempires.password"),
			this.leftPos + PASSWORD_X, this.topPos + 64, 0x404040, false);
		graphics.text(this.font, this.playerInventoryTitle,
			this.leftPos + this.inventoryLabelX, this.topPos + this.inventoryLabelY, 0x404040, false);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			this.minecraft.player.closeContainer();
			return true;
		}
		// Match AnvilScreen: while a field can consume input, do not let inventory keys close the GUI.
		if (this.renameField.keyPressed(event) || this.passwordField.keyPressed(event)) {
			return true;
		}
		if (this.renameField.canConsumeInput() || this.passwordField.canConsumeInput()) {
			return true;
		}
		return super.keyPressed(event);
	}
}
