package net.inkyquill.delightfulkitchen.client.screen;

import net.inkyquill.delightfulkitchen.client.screen.widget.ChefPlanDetailsPanel;
import net.inkyquill.delightfulkitchen.client.screen.widget.ChefQuantityControl;
import net.inkyquill.delightfulkitchen.client.screen.widget.ChefRecipeFilterButton;
import net.inkyquill.delightfulkitchen.client.screen.widget.ChefRecipeGrid;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.menu.ChefCounterMenu;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeFilter;
import net.inkyquill.delightfulkitchen.menu.chef.ChefRecipeSummary;
import net.inkyquill.delightfulkitchen.network.ModPayloads;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterConfirmPayload;
import net.inkyquill.delightfulkitchen.network.payload.ServerboundChefCounterSelectPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class ChefCounterScreen extends AbstractContainerScreen<ChefCounterMenu> {
    private static final Component STATUS_UNLINKED =
            Component.translatable("container.delightful_kitchen.chef_counter.status_unlinked");
    private static final Component SEARCH_PLACEHOLDER =
            Component.translatable("gui.delightful_kitchen.chef_counter.search");
    private static final Component RECIPES_LABEL =
            Component.translatable("gui.delightful_kitchen.chef_counter.recipes_heading");
    private static final Component EMPTY_LABEL =
            Component.translatable("gui.delightful_kitchen.chef_counter.empty");
    private static final int PANEL_COLOR = 0xFFE3E3E3;
    private static final int PANEL_BORDER = 0xFF777777;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int DETAILS_RESPONSE_TIMEOUT_TICKS = 100;

    private EditBox searchBox;
    private ChefRecipeGrid recipeGrid;
    private ChefPlanDetailsPanel detailsPanel;
    private ChefRecipeFilter selectedFilter = ChefRecipeFilter.ALL;
    private int selectedRecipeIndex = -1;
    private int requestedQuantity = 1;
    private boolean awaitingServerDetails;
    private int awaitingServerDetailsTicks;
    private int pendingSelectedRecipeIndex;
    private int pendingRequestedQuantity;
    private ResourceLocation selectedRecipeId;
    private ResourceLocation pendingSelectedRecipeId;
    private boolean awaitingConfirm;
    private Optional<ChefPlanDetails> confirmBaselineDetails = Optional.empty();
    private Optional<ChefQueueResultView> confirmBaselineResult = Optional.empty();
    private Optional<ChefQueueResultView> visibleQueueResult = Optional.empty();

    public ChefCounterScreen(ChefCounterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 352;
        imageHeight = 222;
        inventoryLabelX = 8;
        inventoryLabelY = 118;
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(font, leftPos + 8, topPos + 17, 132, 16, SEARCH_PLACEHOLDER);
        searchBox.setMaxLength(64);
        searchBox.setHint(SEARCH_PLACEHOLDER);
        addRenderableWidget(searchBox);

        int filterY = topPos + 37;
        addRenderableWidget(new ChefRecipeFilterButton(leftPos + 8, filterY, 28, 14, ChefRecipeFilter.ALL,
                () -> selectedFilter, this::selectFilter));
        addRenderableWidget(new ChefRecipeFilterButton(leftPos + 39, filterY, 38, 14, ChefRecipeFilter.READY,
                () -> selectedFilter, this::selectFilter));
        addRenderableWidget(new ChefRecipeFilterButton(leftPos + 80, filterY, 54, 14, ChefRecipeFilter.INTERMEDIATE,
                () -> selectedFilter, this::selectFilter));
        addRenderableWidget(new ChefRecipeFilterButton(leftPos + 137, filterY, 42, 14, ChefRecipeFilter.BLOCKED,
                () -> selectedFilter, this::selectFilter));

        recipeGrid = new ChefRecipeGrid(leftPos + 8, topPos + 57, this::filteredRecipes,
                this::selectedRecipeIndex, this::selectRecipe);
        addRenderableWidget(recipeGrid);

        detailsPanel = new ChefPlanDetailsPanel(leftPos + 188, topPos + 17, 156, 188,
                () -> menu.clientState().details(), () -> visibleQueueResult, this::waitingForDetailsOrConfirm,
                this::confirmSelection);
        addRenderableWidget(detailsPanel);

        addRenderableWidget(new ChefQuantityControl(leftPos + 286, topPos + 71, 54, 18,
                this::currentQuantity, this::selectQuantity));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF8B8B8B);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFEFEFEF);

        panel(graphics, 7, 52, 134, 46);
        graphics.fill(leftPos + 7, topPos + 127, leftPos + 169, topPos + 185, 0xFFD6D6D6);
        graphics.fill(leftPos + 7, topPos + 185, leftPos + 169, topPos + 205, 0xFFD6D6D6);
    }

    private void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, PANEL_BORDER);
        graphics.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + width - 1, topPos + y + height - 1,
                PANEL_COLOR);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        pollServerResponses();
        syncLocalSelectionFromDetails();
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOR, false);
        graphics.drawString(font, RECIPES_LABEL, 145, 43, TEXT_COLOR, false);
        renderKitchenStatus(graphics);
        renderEmptyState(graphics);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    private void renderKitchenStatus(GuiGraphics graphics) {
        graphics.drawString(font,
                Component.translatable("gui.delightful_kitchen.chef_counter.ready_short", menu.getReadyCount(),
                        menu.getIntermediateCount(), menu.getBlockedCount()),
                145, 57, 0x606060, false);
        graphics.drawString(font,
                Component.translatable("gui.delightful_kitchen.chef_counter.floor_short", menu.getFloorCount(),
                        menu.getInventoryCount()),
                145, 67, 0x606060, false);

        if (menu.hasMultipleCounters()) {
            List<FormattedCharSequence> warningLines = font.split(
                    Component.translatable("gui.delightful_kitchen.chef_counter.multiple_counters"), 38);
            for (int i = 0; i < Math.min(warningLines.size(), 2); i++) {
                graphics.drawString(font, warningLines.get(i), 145, 79 + i * 10, 0xAA3322, false);
            }
        } else if (menu.getFloorCount() == 0) {
            graphics.drawString(font, font.plainSubstrByWidth(STATUS_UNLINKED.getString(), 38), 145, 79,
                    TEXT_COLOR, false);
        }
    }

    private void renderEmptyState(GuiGraphics graphics) {
        if (!filteredRecipes().isEmpty()) {
            return;
        }
        graphics.drawCenteredString(font, EMPTY_LABEL, 74, 84, 0x707070);
    }

    private List<ChefRecipeSummary> filteredRecipes() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return menu.clientState().summaries().stream()
                .filter(summary -> selectedFilter == ChefRecipeFilter.ALL || matchesFilter(summary))
                .filter(summary -> query.isEmpty() || matchesSearch(summary, query))
                .toList();
    }

    private boolean matchesFilter(ChefRecipeSummary summary) {
        return switch (selectedFilter) {
            case ALL -> true;
            case READY -> summary.state() == PlanState.READY;
            case INTERMEDIATE -> summary.state() == PlanState.CRAFTABLE_WITH_INTERMEDIATES;
            case BLOCKED -> summary.state() == PlanState.BLOCKED;
        };
    }

    private boolean matchesSearch(ChefRecipeSummary summary, String query) {
        ResourceLocation id = summary.recipeId();
        String outputName = summary.output().getHoverName().getString().toLowerCase(Locale.ROOT);
        return outputName.contains(query)
                || id.getNamespace().toLowerCase(Locale.ROOT).contains(query)
                || id.getPath().toLowerCase(Locale.ROOT).contains(query);
    }

    private void selectFilter(ChefRecipeFilter filter) {
        selectedFilter = filter;
    }

    private void selectRecipe(ChefRecipeSummary summary) {
        selectedRecipeIndex = summary.index();
        selectedRecipeId = summary.recipeId();
        visibleQueueResult = Optional.empty();
        sendSelection();
    }

    private void selectQuantity(int quantity) {
        requestedQuantity = Math.max(1, Math.min(64, quantity));
        visibleQueueResult = Optional.empty();
        sendSelection();
    }

    private void sendSelection() {
        awaitingServerDetails = true;
        awaitingServerDetailsTicks = 0;
        pendingSelectedRecipeIndex = selectedRecipeIndex;
        pendingRequestedQuantity = requestedQuantity;
        pendingSelectedRecipeId = selectedRecipeId;
        ModPayloads.sendToServer(new ServerboundChefCounterSelectPayload(menu.containerId, selectedRecipeIndex,
                requestedQuantity));
    }

    private void confirmSelection() {
        Optional<ChefPlanDetails> details = menu.clientState().details();
        if (!canConfirmDetails(details, awaitingServerDetails, awaitingConfirm, pendingSelectedRecipeIndex,
                pendingRequestedQuantity, pendingSelectedRecipeId)) {
            return;
        }

        ChefPlanDetails selectedDetails = details.orElseThrow();
        awaitingConfirm = true;
        confirmBaselineDetails = details;
        confirmBaselineResult = menu.clientState().queueResult();
        ModPayloads.sendToServer(new ServerboundChefCounterConfirmPayload(menu.containerId,
                selectedDetails.selectedIndex(), selectedDetails.quantity(), selectedDetails.fingerprint()));
    }

    private int currentQuantity() {
        return requestedQuantity;
    }

    private int selectedRecipeIndex() {
        return selectedRecipeIndex;
    }

    private boolean waitingForDetailsOrConfirm() {
        return awaitingServerDetails || awaitingConfirm;
    }

    private void syncLocalSelectionFromDetails() {
        Optional<ChefPlanDetails> currentDetails = menu.clientState().details();
        if (currentDetails.isEmpty()) {
            awaitingServerDetails = false;
            awaitingServerDetailsTicks = 0;
            selectedRecipeIndex = -1;
            selectedRecipeId = null;
            return;
        }
        currentDetails.ifPresent(details -> {
            if (awaitingServerDetails && !matchesPendingDetails(details)) {
                return;
            }
            awaitingServerDetails = false;
            awaitingServerDetailsTicks = 0;
            selectedRecipeIndex = details.selectedIndex();
            selectedRecipeId = details.targetRecipeId();
            requestedQuantity = details.quantity();
        });
    }

    private boolean matchesPendingDetails(ChefPlanDetails details) {
        return matchesPendingDetails(details, pendingSelectedRecipeIndex, pendingRequestedQuantity,
                pendingSelectedRecipeId);
    }

    static boolean matchesPendingDetails(ChefPlanDetails details, int pendingIndex, int pendingQuantity,
                                         ResourceLocation pendingRecipeId) {
        if (details.quantity() != pendingQuantity) {
            return false;
        }
        if (pendingRecipeId != null) {
            return details.targetRecipeId().equals(pendingRecipeId);
        }
        return details.selectedIndex() == pendingIndex;
    }

    static boolean canConfirmDetails(Optional<ChefPlanDetails> details, boolean awaitingServerDetails,
                                     boolean awaitingConfirm, int pendingIndex, int pendingQuantity,
                                     ResourceLocation pendingRecipeId) {
        if (awaitingConfirm || details.isEmpty() || details.orElseThrow().state() == PlanState.BLOCKED) {
            return false;
        }
        return matchesPendingDetails(details.orElseThrow(), pendingIndex, pendingQuantity, pendingRecipeId);
    }

    private void pollServerResponses() {
        if (!awaitingConfirm) {
            return;
        }
        Optional<ChefQueueResultView> currentResult = menu.clientState().queueResult();
        Optional<ChefPlanDetails> currentDetails = menu.clientState().details();
        if (!Objects.equals(currentResult, confirmBaselineResult)
                || !Objects.equals(currentDetails, confirmBaselineDetails)) {
            awaitingConfirm = false;
            visibleQueueResult = currentResult;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!awaitingServerDetails) {
            return;
        }
        awaitingServerDetailsTicks++;
        if (awaitingServerDetailsTicks >= DETAILS_RESPONSE_TIMEOUT_TICKS) {
            awaitingServerDetails = false;
            awaitingServerDetailsTicks = 0;
            selectedRecipeIndex = -1;
            selectedRecipeId = null;
            pendingSelectedRecipeId = null;
            menu.clientState().clearDetails();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailsPanel != null && detailsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return recipeGrid != null && recipeGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.canConsumeInput()) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (isSearchTextKey(keyCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isSearchTextKey(int keyCode, int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0
                && (keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL
                || keyCode == GLFW.GLFW_KEY_WORLD_1
                || keyCode == GLFW.GLFW_KEY_WORLD_2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (recipeGrid != null) {
            recipeGrid.tooltipAt(mouseX, mouseY).ifPresent(tooltip ->
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY));
        }
        if (detailsPanel != null) {
            detailsPanel.tooltipAt(mouseX, mouseY).ifPresent(tooltip ->
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY));
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
