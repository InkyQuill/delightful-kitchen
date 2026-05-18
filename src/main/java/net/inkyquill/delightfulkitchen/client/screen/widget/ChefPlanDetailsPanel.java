package net.inkyquill.delightfulkitchen.client.screen.widget;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanState;
import net.inkyquill.delightfulkitchen.kitchen.recipe.PlanningBlocker;
import net.inkyquill.delightfulkitchen.kitchen.utility.KitchenUtilityRequirement;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanDetails;
import net.inkyquill.delightfulkitchen.menu.chef.ChefPlanStepView;
import net.inkyquill.delightfulkitchen.menu.chef.ChefQueueResultView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ChefPlanDetailsPanel extends AbstractWidget {
    private static final int PANEL_BORDER = 0xFF777777;
    private static final int PANEL_COLOR = 0xFFE3E3E3;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int MUTED_TEXT_COLOR = 0xFF707070;
    private static final int WARNING_TEXT_COLOR = 0xFFAA3322;
    private static final int BUTTON_HEIGHT = 18;

    private final Supplier<Optional<ChefPlanDetails>> details;
    private final Supplier<Optional<ChefQueueResultView>> queueResult;
    private final BooleanSupplier waitingForServer;
    private final Runnable onConfirm;
    private final List<IconTooltip> iconTooltips = new ArrayList<>();
    private int stepScrollRow;
    private int lastStepCount;
    private int lastVisibleStepRows;
    private int lastStepListTop;
    private int lastStepListBottom;

    public ChefPlanDetailsPanel(int x, int y, int width, int height,
                                Supplier<Optional<ChefPlanDetails>> details,
                                Supplier<Optional<ChefQueueResultView>> queueResult,
                                BooleanSupplier waitingForServer,
                                Runnable onConfirm) {
        super(x, y, width, height, Component.translatable("gui.delightful_kitchen.chef_counter.details"));
        this.details = details;
        this.queueResult = queueResult;
        this.waitingForServer = waitingForServer;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        iconTooltips.clear();
        Font font = Minecraft.getInstance().font;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, PANEL_BORDER);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, PANEL_COLOR);

        int contentX = getX() + 5;
        int contentWidth = width - 10;
        int y = getY() + 5;
        graphics.drawString(font, getMessage(), contentX, y, TEXT_COLOR, false);
        y += 13;

        Optional<ChefPlanDetails> currentDetails = details.get();
        if (currentDetails.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.delightful_kitchen.chef_counter.no_selection"),
                    contentX, y, MUTED_TEXT_COLOR, false);
            Optional<ChefQueueResultView> result = queueResult.get();
            if (result.isPresent()) {
                renderQueueResult(graphics, font, result.orElseThrow(), contentX, y + 14, contentWidth);
            }
            return;
        }

        ChefPlanDetails selectedDetails = currentDetails.orElseThrow();
        ItemStack output = selectedDetails.output();
        graphics.renderFakeItem(output, contentX, y);
        graphics.renderItemDecorations(font, output, contentX, y);
        addIconTooltip(contentX, y, output);
        drawFit(graphics, font, output.getHoverName(), contentX + 20, y + 4, contentWidth - 20, TEXT_COLOR);
        y += 20;

        drawFit(graphics, font, Component.translatable("gui.delightful_kitchen.chef_counter.state",
                stateLabel(selectedDetails.state())), contentX, y, contentWidth, TEXT_COLOR);
        y += 10;
        drawFit(graphics, font, Component.translatable("gui.delightful_kitchen.chef_counter.expected_output",
                selectedDetails.expectedOutputCount()), contentX, y, contentWidth, TEXT_COLOR);
        y += 15;

        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.chef_counter.quantity"),
                contentX, y, TEXT_COLOR, false);
        y += 20;

        Optional<ChefQueueResultView> result = queueResult.get();
        int contentLimit = confirmButtonY() - (result.isPresent() ? 24 : 2);
        y = renderRequirements(graphics, font, selectedDetails, contentX, y, contentWidth);
        y = renderSteps(graphics, font, selectedDetails.steps(), contentX, y, contentWidth, contentLimit);
        if (selectedDetails.state() == PlanState.BLOCKED) {
            y = renderBlockers(graphics, font, selectedDetails.blockers(), contentX, y, contentWidth, contentLimit);
        }
        if (result.isPresent()) {
            renderQueueResult(graphics, font, result.orElseThrow(), contentX, y, contentWidth);
        }
        renderConfirmButton(graphics, font, mouseX, mouseY, selectedDetails);
    }

    private int renderRequirements(GuiGraphics graphics, Font font, ChefPlanDetails selectedDetails, int x, int y,
                                   int contentWidth) {
        List<Component> stations = selectedDetails.steps().stream()
                .map(ChefPlanStepView::stationType)
                .collect(LinkedHashSet<KitchenStationType>::new, LinkedHashSet::add, LinkedHashSet::addAll)
                .stream()
                .map(ChefPlanDetailsPanel::stationLabel)
                .toList();
        if (!stations.isEmpty()) {
            drawFit(graphics, font, joinedLabel("gui.delightful_kitchen.chef_counter.detail.stations", stations),
                    x, y, contentWidth, TEXT_COLOR);
            y += 10;
        }

        List<Component> utilities = selectedDetails.steps().stream()
                .map(ChefPlanStepView::utilityRequirement)
                .filter(requirement -> requirement != KitchenUtilityRequirement.NONE)
                .collect(LinkedHashSet<KitchenUtilityRequirement>::new, LinkedHashSet::add, LinkedHashSet::addAll)
                .stream()
                .map(ChefPlanDetailsPanel::utilityLabel)
                .toList();
        if (!utilities.isEmpty()) {
            drawFit(graphics, font, joinedLabel("gui.delightful_kitchen.chef_counter.detail.utilities", utilities),
                    x, y, contentWidth, TEXT_COLOR);
            y += 10;
        }

        List<ItemStack> fuel = uniqueStacks(selectedDetails.steps().stream()
                .map(ChefPlanStepView::fuel)
                .filter(stack -> !stack.isEmpty())
                .toList());
        List<ItemStack> containers = uniqueStacks(selectedDetails.steps().stream()
                .map(ChefPlanStepView::servingContainer)
                .filter(stack -> !stack.isEmpty())
                .toList());
        if (!fuel.isEmpty() || !containers.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.delightful_kitchen.chef_counter.detail.needs"),
                    x, y + 4, TEXT_COLOR, false);
            int iconX = x + 38;
            for (ItemStack stack : limitedStacks(fuel, 2)) {
                iconX = renderSmallStack(graphics, font, stack, iconX, y);
            }
            for (ItemStack stack : limitedStacks(containers, 2)) {
                iconX = renderSmallStack(graphics, font, stack, iconX, y);
            }
            y += 19;
        }
        return y;
    }

    private int renderSteps(GuiGraphics graphics, Font font, List<ChefPlanStepView> steps, int x, int y,
                            int contentWidth, int contentLimit) {
        lastStepCount = steps.size();
        if (steps.isEmpty() || y + 10 > contentLimit) {
            stepScrollRow = 0;
            lastVisibleStepRows = 0;
            return y;
        }
        graphics.drawString(font, Component.translatable("gui.delightful_kitchen.chef_counter.detail.steps"),
                x, y, TEXT_COLOR, false);
        y += 10;

        lastStepListTop = y;
        lastVisibleStepRows = Math.max(0, (contentLimit - y) / 17);
        stepScrollRow = Math.max(0, Math.min(stepScrollRow, maxStepScrollRow()));
        for (int row = 0; row < lastVisibleStepRows && stepScrollRow + row < steps.size(); row++) {
            ChefPlanStepView step = steps.get(stepScrollRow + row);
            ItemStack output = step.primaryOutput();
            graphics.renderFakeItem(output, x, y - 2);
            addIconTooltip(x, y - 2, output);
            Component stepLabel = Component.translatable("gui.delightful_kitchen.chef_counter.detail.step",
                    stationLabel(step.stationType()), output.getHoverName());
            drawFit(graphics, font, stepLabel, x + 19, y + 2, contentWidth - 24, TEXT_COLOR);
            y += 17;
        }
        lastStepListBottom = y;
        if (canScrollSteps()) {
            int barX = x + contentWidth - 3;
            int listHeight = Math.max(1, lastStepListBottom - lastStepListTop);
            int barHeight = Math.max(8, listHeight * lastVisibleStepRows / lastStepCount);
            int maxTravel = Math.max(0, listHeight - barHeight);
            int barY = lastStepListTop + (maxStepScrollRow() == 0 ? 0 : maxTravel * stepScrollRow / maxStepScrollRow());
            graphics.fill(barX, lastStepListTop, barX + 2, lastStepListBottom, 0xFF9A9A9A);
            graphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFF505050);
        }
        return y;
    }

    private int renderBlockers(GuiGraphics graphics, Font font, EnumSet<PlanningBlocker> blockers, int x, int y,
                               int contentWidth, int contentLimit) {
        if (blockers.isEmpty() || y + 10 > contentLimit) {
            return y;
        }
        drawFit(graphics, font, Component.translatable("gui.delightful_kitchen.chef_counter.detail.blockers"),
                x, y, contentWidth, WARNING_TEXT_COLOR);
        y += 10;

        int rendered = 0;
        for (PlanningBlocker blocker : blockers.stream().sorted(Comparator.comparing(Enum::ordinal)).toList()) {
            if (y + 10 > contentLimit) {
                return y;
            }
            if (rendered >= 2) {
                drawFit(graphics, font, Component.translatable("gui.delightful_kitchen.chef_counter.detail.more_blockers",
                        blockers.size() - rendered), x, y, contentWidth, WARNING_TEXT_COLOR);
                return y + 10;
            }
            drawFit(graphics, font, Component.literal("- ").append(blockerLabel(blocker)), x, y, contentWidth,
                    WARNING_TEXT_COLOR);
            y += 10;
            rendered++;
        }
        return y;
    }

    private void renderQueueResult(GuiGraphics graphics, Font font, ChefQueueResultView result, int x, int y,
                                   int contentWidth) {
        Component label;
        int color = TEXT_COLOR;
        if (result.isQueued()) {
            label = Component.translatable("gui.delightful_kitchen.chef_counter.result.queued");
        } else if (result.blockers().contains(PlanningBlocker.KITCHEN_CHANGED)) {
            label = Component.translatable("gui.delightful_kitchen.chef_counter.result.kitchen_changed");
            color = WARNING_TEXT_COLOR;
        } else {
            label = Component.translatable("gui.delightful_kitchen.chef_counter.result.blocked",
                    shortBlockerList(result.blockers()));
            color = WARNING_TEXT_COLOR;
        }
        drawFit(graphics, font, label, x, Math.min(y, confirmButtonY() - 11), contentWidth, color);
    }

    private void renderConfirmButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, ChefPlanDetails details) {
        if (details.state() == PlanState.BLOCKED) {
            return;
        }
        boolean enabled = !waitingForServer.getAsBoolean();
        int color = enabled && confirmButtonHovered(mouseX, mouseY) ? 0xFFEAEAEA : 0xFFC6C6C6;
        graphics.fill(confirmButtonX(), confirmButtonY(), confirmButtonX() + confirmButtonWidth(),
                confirmButtonY() + BUTTON_HEIGHT, 0xFF404040);
        graphics.fill(confirmButtonX() + 1, confirmButtonY() + 1, confirmButtonX() + confirmButtonWidth() - 1,
                confirmButtonY() + BUTTON_HEIGHT - 1, color);

        Component label = waitingForServer.getAsBoolean()
                ? Component.translatable("gui.delightful_kitchen.chef_counter.button.waiting")
                : Component.translatable("gui.delightful_kitchen.chef_counter.button.queue");
        graphics.drawCenteredString(font, label, confirmButtonX() + confirmButtonWidth() / 2,
                confirmButtonY() + 5, enabled ? TEXT_COLOR : MUTED_TEXT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !confirmButtonHovered(mouseX, mouseY) || waitingForServer.getAsBoolean()) {
            return false;
        }
        Optional<ChefPlanDetails> currentDetails = details.get();
        if (currentDetails.isEmpty() || currentDetails.orElseThrow().state() == PlanState.BLOCKED) {
            return false;
        }
        onConfirm.run();
        return true;
    }

    public Optional<List<Component>> tooltipAt(double mouseX, double mouseY) {
        for (IconTooltip tooltip : iconTooltips) {
            if (tooltip.contains(mouseX, mouseY)) {
                return Optional.of(List.of(tooltip.stack().getHoverName()));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || !canScrollSteps()) {
            return false;
        }
        stepScrollRow -= (int) Math.signum(scrollY);
        stepScrollRow = Math.max(0, Math.min(stepScrollRow, maxStepScrollRow()));
        return true;
    }

    private int renderSmallStack(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        graphics.renderFakeItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        addIconTooltip(x, y, stack);
        return x + 18;
    }

    private void addIconTooltip(int x, int y, ItemStack stack) {
        if (!stack.isEmpty()) {
            iconTooltips.add(new IconTooltip(x, y, stack));
        }
    }

    private static List<ItemStack> uniqueStacks(List<ItemStack> stacks) {
        Map<ResourceLocation, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            unique.putIfAbsent(key, stack);
        }
        return List.copyOf(unique.values());
    }

    private static List<ItemStack> limitedStacks(List<ItemStack> stacks, int limit) {
        return stacks.size() <= limit ? stacks : stacks.subList(0, limit);
    }

    private static Component joinedLabel(String key, List<Component> values) {
        String joined = values.stream().map(Component::getString).reduce((left, right) -> left + ", " + right)
                .orElse("");
        return Component.translatable(key).append(": ").append(joined);
    }

    private static Component stateLabel(PlanState state) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.state." + switch (state) {
            case READY -> "ready";
            case CRAFTABLE_WITH_INTERMEDIATES -> "intermediate";
            case BLOCKED -> "blocked";
        });
    }

    private static Component stationLabel(KitchenStationType stationType) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.station."
                + stationType.name().toLowerCase());
    }

    private static Component utilityLabel(KitchenUtilityRequirement requirement) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.utility."
                + requirement.name().toLowerCase());
    }

    private static Component blockerLabel(PlanningBlocker blocker) {
        return Component.translatable("gui.delightful_kitchen.chef_counter.blocker." + blocker.name().toLowerCase());
    }

    private static Component shortBlockerList(EnumSet<PlanningBlocker> blockers) {
        return Component.literal(blockers.stream()
                .sorted(Comparator.comparing(Enum::ordinal))
                .limit(2)
                .map(blocker -> blockerLabel(blocker).getString())
                .reduce((left, right) -> left + ", " + right)
                .orElse(""));
    }

    private void drawFit(GuiGraphics graphics, Font font, Component label, int x, int y, int maxWidth, int color) {
        String text = label.getString();
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    private int confirmButtonX() {
        return getX() + 5;
    }

    private int confirmButtonY() {
        return getY() + height - BUTTON_HEIGHT - 5;
    }

    private int confirmButtonWidth() {
        return width - 10;
    }

    private boolean confirmButtonHovered(double mouseX, double mouseY) {
        return mouseX >= confirmButtonX() && mouseX < confirmButtonX() + confirmButtonWidth()
                && mouseY >= confirmButtonY() && mouseY < confirmButtonY() + BUTTON_HEIGHT;
    }

    private boolean canScrollSteps() {
        return lastVisibleStepRows > 0 && lastStepCount > lastVisibleStepRows;
    }

    private int maxStepScrollRow() {
        return Math.max(0, lastStepCount - lastVisibleStepRows);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    private record IconTooltip(int x, int y, ItemStack stack) {
        private IconTooltip {
            stack = stack.copy();
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        }
    }
}
