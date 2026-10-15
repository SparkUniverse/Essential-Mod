/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.mixins.impl.client.gui;

import gg.essential.Essential;
import gg.essential.elementa.renderer.ElementaExtractor;
import gg.essential.elementa.utils.TriConsumer;
import gg.essential.event.gui.GuiDrawScreenEvent;
import gg.essential.event.gui.GuiMouseReleaseEvent;
import gg.essential.universal.UGraphics;
import gg.essential.universal.UMath;
import gg.essential.universal.UMatrixStack;
import gg.essential.universal.UResolution;
import gg.essential.universal.USound;
import gg.essential.universal.render.UGpuDevice;
import gg.essential.universal.render.UGpuFormat;
import gg.essential.universal.render.UGpuSampler;
import gg.essential.universal.render.UGpuTexture;
import gg.essential.universal.render.UGpuTextureView;
import gg.essential.util.EssentialGuiExtensionsKt;
import gg.essential.util.McElementaExtractor;
import gg.essential.util.UDrawContext;
import kotlin.Unit;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//#if MC>=12106
//$$ import net.minecraft.client.gui.render.state.GuiRenderState;
//$$ import static gg.essential.util.RenderGuiRenderStateToRenderTargetKt.renderGuiRenderStateToTexture;
//#else
import gg.essential.util.DrawFramebufferContext;
import static gg.essential.util.GuiRenderSetupKt.withDefaultMcGuiRenderingSetup;
//#endif

//#if MC>=12004
//$$ import gg.essential.mixins.transformers.client.gui.EntryListWidgetAccessor;
//#endif

//#if MC>=12000
//$$ import net.minecraft.client.gui.DrawContext;
//#endif

//#if MC>=11904
//$$ import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
//#endif

//#if MC>=11600
//$$ import net.minecraft.client.Minecraft;
//$$ import com.mojang.blaze3d.matrix.MatrixStack;
//$$ import net.minecraft.client.gui.widget.list.ResourcePackList;
//$$ import gg.essential.mixins.transformers.client.gui.AbstractListAccessor;
//$$ import gg.essential.mixins.transformers.client.gui.PackScreenAccessor;
//$$ import static gg.essential.util.HelpersKt.identifier;
//$$ import static gg.essential.util.HelpersKt.textLiteral;
//#else
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
//#endif

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static gg.essential.elementa.renderer.ElementaExtractorKt.fillMcScale;
import static kotlin.ranges.RangesKt.coerceIn;

public class
        //#if MC>=11904
        //$$ GuiDragDropEntryHandler<E extends AlwaysSelectedEntryListWidget.Entry<E>> {
        //#elseif MC>=11600
        //$$ GuiDragDropEntryHandler<E extends AbstractList.AbstractListEntry<E>> {
        //#else
        GuiDragDropEntryHandler<E extends GuiListExtended.IGuiListEntry> {
        //#endif

    private final E indicatorEntry;
    private final GuiScreen screen;

    private UGpuTextureView tmpColorTextureView;
    //#if MC < 1.21.6
    private UGpuTextureView tmpDepthTextureView;
    //#endif

    // actions sent by screen mixins
    private final Consumer<ScreenPosition> indicatorPositionUpdater;
    private final Consumer<GuiDrawScreenEvent> draggedEntryDrawAction;
    private final Runnable mouseReleaseAction;
    private final Runnable runForDrawChange;
    private final BiConsumer<E, Integer> onRevertedDrag;
    public final Runnable runForDataChange;
    public final Runnable runToUnselectEntries;

    // state of the current dragged entry
    private ResourceEntryDragState<E> draggedEntryState = null;

    // state of a yet to be started drag, only exists between mouse click and mouse move (init pending drag) or release (cancel pending drag)
    private ResourceEntryDragState<E> pendingDraggedEntryState = null;

    // frame time tracking for scroll nudging
    private long prevTime = System.currentTimeMillis();
    private float deltaTimeLimitedByDragTime = 0;


    public GuiDragDropEntryHandler(GuiScreen screen, Runnable runForDataChange, Runnable runForDrawChange,
                                   Runnable mouseReleaseAction, Consumer<ScreenPosition> indicatorPositionUpdater,
                                   Consumer<GuiDrawScreenEvent> draggedEntryDrawAction, BiConsumer<E, Integer> onRevertedDrag,
                                    Runnable runToUnselectEntries, E indicatorEntry) {
        this.indicatorEntry = indicatorEntry;
        this.runForDataChange = runForDataChange;
        this.runForDrawChange = runForDrawChange;
        this.screen = screen;
        this.onRevertedDrag = onRevertedDrag;
        this.runToUnselectEntries = runToUnselectEntries;

        // actions for event listeners
        this.draggedEntryDrawAction = draggedEntryDrawAction;
        this.indicatorPositionUpdater = indicatorPositionUpdater;
        this.mouseReleaseAction = mouseReleaseAction;
        Essential.EVENT_BUS.register(this);
    }

    @Subscribe
    private void onGuiMouseReleaseEvent(GuiMouseReleaseEvent event) {
        if (screen == event.getScreen()) {
            if (isDraggingEntry()) {
                mouseReleaseAction.run();
            } else if (isPendingDrag()) {
                pendingDraggedEntryState = null;
            }
        }
    }

    // use the Priority subclass to ensure we render the dragged entry after everything else in the screen, vanilla or otherwise
    // this is especially important for 1.20.2 & 1.20.4 as some vanilla screen objects render after the non-priority event fires
    @Subscribe
    private void onGuiDrawScreenEvent(GuiDrawScreenEvent.Priority event) {
        if (event.isPost() && screen == event.getScreen()){
            if (isPendingDrag() && pendingDraggedEntryState.hasMouseMoved(event.getMouseX(), event.getMouseY())) {
                promotePendingDrag();
            }

            if(isDraggingEntry()) {
                processDeltaTimeForScrolling();
                draggedEntryDrawAction.accept(event);
            }
        }
    }

    // used by logic in drawScrollIndicatorsAndScrollIfNeeded() however that method may be called twice in a frame
    public void processDeltaTimeForScrolling(){
        // record delta time, for scroll nudging, over entire period of drag
        long currentTime = System.currentTimeMillis();
        // calculate factoring in drag time as a way to zero out any first frame weirdness and add 1.25 seconds of scroll speed ramp up
        deltaTimeLimitedByDragTime = (currentTime - prevTime) / 1000F * UMath.clampFloat((draggedEntryState.getDragTimeSeconds()) / 1.25F, 0, 1F);
        prevTime = currentTime;
    }

    public void close(@Nullable List<?> list, @Nullable List<?> otherList) {
        if (isDraggingEntry()) {
            revertDraggedEntryToOriginalContainer(list, otherList);
        }
        Essential.EVENT_BUS.unregister(this);

        closeTmpTextures();
    }

    private void closeTmpTextures() {
        try {
            if (tmpColorTextureView != null) {
                tmpColorTextureView.close();
                tmpColorTextureView.getTexture().close();
                tmpColorTextureView = null;
            }
            //#if MC < 1.21.6
            if (tmpDepthTextureView != null) {
                tmpDepthTextureView.close();
                tmpDepthTextureView.getTexture().close();
                tmpDepthTextureView = null;
            }
            //#endif
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isIndicatorEntry(Object entry) {
        return indicatorEntry == entry;
    }


    public void setPendingDraggedEntryState(E entry, List<E> originalContainer, double mouseXOffset, double mouseYOffset, int originalIndex, double mouseX, double mouseY, boolean mustReturnToOriginalList) {
        pendingDraggedEntryState = new ResourceEntryDragState<>(entry, originalContainer, mouseXOffset, mouseYOffset, originalIndex, mouseX, mouseY, mustReturnToOriginalList);
    }

    private void promotePendingDrag() {
        draggedEntryState = pendingDraggedEntryState;
        pendingDraggedEntryState = null;
        draggedEntryState.setStartTime();
        //#if MC>=12109
        //$$ indicatorEntry.setHeight(draggedEntryState.entry.getHeight());
        //#endif
        draggedEntryState.originalContainer.set(draggedEntryState.originalIndex, indicatorEntry);
        runToUnselectEntries.run();
        runForDrawChange.run();
    }

    /**
     * @return true if an entry is currently being dragged
     */
    public boolean isDraggingEntry() {
        return draggedEntryState != null;
    }

    private boolean isPendingDrag() {
        return pendingDraggedEntryState != null;
    }


    /**
     * Places the dragged entry in the list that contains the indicator entry, or releases the dragged entry to its original container.
     * Additionally, runs consumers to reflect these changes in potentially separate data objects and also select the dropped entry after
     * all processing is complete.
     */
    public void placeDraggedEntryAtIndicatorOrReleaseToOrigin(@NotNull List<E> list, @Nullable List<E> otherList, // first list is considered "left" for the data and selection actions
                                                              @NotNull GuiDragDropEntryHandler.DataChangeReflector<E> dataChangeReflector, // consumer to reflect drag changes in possibly separate data objects
                                                              @NotNull TriConsumer<Boolean, E, Integer> setSelected) { // consumer to set the selected list item after all drag processing has completed
        List<E> destination;
        if (list.contains(indicatorEntry)) {
            destination = list;
        } else if (otherList != null && otherList.contains(indicatorEntry)) {
            destination = otherList;
        } else { // no empty indicators present
            draggedEntryState.revertToOriginalContainer(onRevertedDrag);

            // captures for selection, these will be modified after endDrag()
            boolean toLeftList = draggedEntryState.originalContainer == list;
            E entry = draggedEntryState.entry;
            int index = draggedEntryState.originalIndex;

            endDrag(false);

            // select the entry if necessary
            setSelected.accept(toLeftList, entry, index);
            return;
        }

        int index = destination.indexOf(indicatorEntry);
        destination.set(index, draggedEntryState.entry);

        // if separate data objects require modification to reflect the change in the rendered lists
        dataChangeReflector.reflectChanges(draggedEntryState.entry, draggedEntryState.originalIndex, index,
                    draggedEntryState.originalContainer == list, destination == list);

        // capture for selection, this will be modified after endDrag()
        E entry = draggedEntryState.entry;

        endDrag(destination != draggedEntryState.originalContainer || index != draggedEntryState.originalIndex);

        // select the entry if necessary
        setSelected.accept(destination == list, entry, index);
    }

    public void revertDraggedEntryToOriginalContainer(@Nullable List<?> list, @Nullable List<?> otherList) {
        draggedEntryState.revertToOriginalContainer(onRevertedDrag);
        clearListIndicators(list, otherList);
        endDrag(false);
    }

    private void endDrag(boolean dataChangesMade) {
        draggedEntryState.playDropSoundIfAllowed();
        draggedEntryState = null;

        if (dataChangesMade) {
            runForDataChange.run();
        }
        runForDrawChange.run();
    }

    public void drawDraggedEntryWithinBounds(GuiDrawScreenEvent event, int width, int height, int padUIBlockWidth, int padEntryRenderWidth, int xBound, int yBound, int x2Bound, int y2Bound) {
        // positions calculated as int to align elementa components with the vanilla rendering of the entry itself

        // we must cast int before the subtraction to align correctly with the original entry position
        // otherwise we might be off by -1,-1 e.g. (int)(1.4d - 0.6d) == 0 and (int) 1.4 - (int) 0.6d == 1
        int x = event.getMouseX() - (int) draggedEntryState.mouseHoldOffset.x();
        int y = event.getMouseY() - (int) draggedEntryState.mouseHoldOffset.y();
        if (x < xBound) x = xBound;
        if (y < yBound) y = yBound;

        int x2 = x + width - 8 + padUIBlockWidth;
        int y2 = y + height + 2;
        if (x2 > x2Bound) {
            x = x2Bound - width + 8 - padUIBlockWidth;
            x2 = x2Bound;
        }
        if (y2 > y2Bound) {
            y = y2Bound - height - 2;
            y2 = y2Bound;
        }

        drawDraggedEntry(event, width, height, padEntryRenderWidth, x, y, x2, y2);
    }

    private void drawDraggedEntry(GuiDrawScreenEvent event, int width, int height, int padEntryRenderWidth, int x, int y, int x2, int y2) {
        ScreenPosition newDragCenter = new ScreenPosition(x + width / 2.0, y + height / 2.0);
        boolean posChanged = draggedEntryState.updatePos(newDragCenter);

        UGpuTextureView textureView = doDrawDraggedEntryToTexture(event.getMatrixStack(), event, width + padEntryRenderWidth, height, x, y, x2, y2);

        McElementaExtractor extractor = new McElementaExtractor(event.getDrawContext());
        extractor.blit(
            0,
            0,
            UResolution.getViewportWidth(),
            UResolution.getViewportHeight(),
            0f,
            1f,
            1f,
            0f,
            textureView,
            EssentialGuiExtensionsKt.getNEAREST(UGpuSampler.Companion),
            false,
            true,
            new Color(0.7f, 0.7f, 0.7f, 0.7f)
        );
        extractor.close();

        // trigger indicator update if necessary, ready for next draw
        if (posChanged) {
            // indicators are set relative to center pos as we want them to visually line up with where the drag is, not where the mouse is
            indicatorPositionUpdater.accept(getDragCenterPos());
        }
    }

    private UGpuTextureView doDrawDraggedEntryToTexture(UMatrixStack matrixStack, GuiDrawScreenEvent event, int width, int height, int x, int y, int x2, int y2) {
        UGpuDevice device = UGraphics.getDevice();
        int textureWidth = UResolution.getViewportWidth();
        int textureHeight = UResolution.getViewportHeight();
        if (tmpColorTextureView != null && (tmpColorTextureView.getTexture().getWidth() != textureWidth || tmpColorTextureView.getTexture().getHeight() != textureHeight)) {
            closeTmpTextures();
        }
        if (tmpColorTextureView == null) {
            tmpColorTextureView = device.createTextureView(device.createTexture(
                "Drag&Drop Entry - Color",
                UGpuTexture.Usage.Companion.getCOPY_DST().plus(UGpuTexture.Usage.Companion.getRENDER_ATTACHMENT()).plus(UGpuTexture.Usage.Companion.getTEXTURE_BINDING()),
                UGpuFormat.Companion.getDEFAULT_RGBA(),
                textureWidth,
                textureHeight,
                1
            ), 0, 1);
        }
        device.clearColor(tmpColorTextureView.getTexture(), 0f, 0f, 0f, 0f);
        //#if MC < 1.21.6
        if (tmpDepthTextureView == null) {
            tmpDepthTextureView = device.createTextureView(device.createTexture(
                "Drag&Drop Entry - Depth",
                UGpuTexture.Usage.Companion.getCOPY_DST().plus(UGpuTexture.Usage.Companion.getRENDER_ATTACHMENT()).plus(UGpuTexture.Usage.Companion.getTEXTURE_BINDING()),
                UGpuFormat.Companion.getDEFAULT_DEPTH(),
                textureWidth,
                textureHeight,
                1
            ), 0, 1);
        }
        device.clearDepth(tmpDepthTextureView.getTexture(), 1f);
        //#endif

        //#if MC >= 1.21.6
        //$$ GuiRenderState guiRenderState = new GuiRenderState();
        //#else
        DrawFramebufferContext drawFramebufferContext = new DrawFramebufferContext();
        drawFramebufferContext.withDrawFramebuffer(tmpColorTextureView, tmpDepthTextureView, () -> {
            withDefaultMcGuiRenderingSetup(textureWidth, textureHeight, UResolution.getScaleFactor(), () -> {
        //#endif

        //#if MC>=12106
        //$$ DrawContext context = new DrawContext(MinecraftClient.getInstance(), guiRenderState
            //#if MC>=12111
            //$$ , event.getMouseX(), event.getMouseY()
            //#endif
        //$$ );
        //#elseif MC >= 1.20
        //$$ DrawContext context = event.getDrawContext().getMc();
        //#endif

        //#if MC >= 1.20
        //$$ McElementaExtractor extractor = new McElementaExtractor(new UDrawContext(context, new UMatrixStack(context.getMatrices())));
        //#else
        McElementaExtractor extractor = new McElementaExtractor(new UDrawContext(matrixStack));
        //#endif
        renderBackgroundWithBorder(extractor, x, y, x2, y2);
        extractor.close();

        //#if MC>=1.21.6
        //#if MC>=12109
        //$$ int orgX = draggedEntryState.entry.getX();
        //$$ int orgY = draggedEntryState.entry.getY();
        //$$ int orgWidth = draggedEntryState.entry.getWidth();
        //$$ int orgHeight = draggedEntryState.entry.getHeight();
        //$$ draggedEntryState.entry.setX(x - 2);
        //$$ draggedEntryState.entry.setY(y - 2);
        //$$ draggedEntryState.entry.setWidth(width + 4);
        //$$ draggedEntryState.entry.setHeight(height + 4);
        //$$ draggedEntryState.entry.render(context, event.getMouseX(), event.getMouseY(), true, event.getPartialTicks());
        //$$ draggedEntryState.entry.setX(orgX);
        //$$ draggedEntryState.entry.setY(orgY);
        //$$ draggedEntryState.entry.setWidth(orgWidth);
        //$$ draggedEntryState.entry.setHeight(orgHeight);
        //#else
        //$$ draggedEntryState.entry.render(context, 0, y, x, width, height, event.getMouseX(), event.getMouseY(), true, event.getPartialTicks());
        //#endif
        //#elseif MC>=12000
        //$$ draggedEntryState.entry.render(context, 0, y, x, width, height, event.getMouseX(), event.getMouseY(), true, event.getPartialTicks());
        //#elseif MC>=11600
        //$$ // added matrixStack and also x and y are swapped
        //$$ draggedEntryState.entry.render(matrixStack.toMC(), 0, y, x, width, height, event.getMouseX(), event.getMouseY(), true, event.getPartialTicks());
        //#elseif MC>=11200
        draggedEntryState.entry.drawEntry(0, x, y, width, height, event.getMouseX(), event.getMouseY(), true, event.getPartialTicks());
        //#else
        //$$ // no partial ticks
        //$$ draggedEntryState.entry.drawEntry(0, x, y, width, height, event.getMouseX(), event.getMouseY(), true);
        //#endif

        //#if MC >= 1.21.6
        //#if MC >= 26.1
        //$$ context.extractDeferredElements(event.getMouseX(), event.getMouseY(), event.getPartialTicks());
        //#elseif MC >= 1.21.11
        //$$ context.drawDeferredElements();
        //#endif
        //$$ renderGuiRenderStateToTexture(guiRenderState, tmpColorTextureView);
        //#else
                //#if MC >= 1.20
                //$$ // flush the vanilla vertex buffer to ensure the actual OpenGL call is made
                //$$ context.draw();
                //#endif
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
        //#endif
        return tmpColorTextureView;
    }

    private static void renderBackgroundWithBorder(ElementaExtractor extractor, final int x, final int y, final int x2, final int y2) {
        Color backgroundColor = new Color(138, 178, 255, 72);
        Color outlineColor = new Color(229, 229, 229, 255);

        // background
        fillMcScale(extractor, x - 1, y - 1, x2 - 1, y2 - 1, backgroundColor);

        // outline
        fillMcScale(extractor, x - 1, y - 2, x2 - 1, y - 1, outlineColor);
        fillMcScale(extractor, x - 1, y2 - 1, x2 - 1, y2, outlineColor);
        fillMcScale(extractor, x - 2, y - 2, x - 1, y2, outlineColor);
        fillMcScale(extractor, x2 - 1, y - 2, x2, y2, outlineColor);
    }

    /**
     * Processes the scroll nudge effect for the dragged entry.
     * Also updates the indicator position if a scroll occurs.
     */
    public void scrollIfDraggingNearTopOrBottom(final double yPos, final GuiSlot list) {
        //#if MC>=11600
        //$$ boolean canScrollUp = list.getScrollAmount() > 0;
        //$$ boolean canScrollDown = list.getScrollAmount() <
        //#if MC>=12004
        //$$        list.getMaxScroll();
        //#else
        //$$        ((AbstractListAccessor)list).essential$getMaxScroll();
        //#endif
        //#else
        boolean canScrollUp = list.getAmountScrolled() > 0;
        boolean canScrollDown = list.getAmountScrolled() < list.getMaxScroll();
        //#endif

        if (!canScrollUp && !canScrollDown) return;

        //#if MC>=12004
        //$$ int top = list.getY();
        //$$ int bottom = list.getBottom();
        //$$ int itemHeight = ((EntryListWidgetAccessor)list).essential$getItemHeight();
        //#elseif MC>=11600
        //$$ int top = ((AbstractListAccessor) list).essential$getTop();
        //$$ int bottom = ((AbstractListAccessor) list).essential$getBottom();
        //$$ int itemHeight = ((AbstractListAccessor)list).essential$getItemHeight();
        //#else
        int top = list.top;
        int bottom = list.bottom;
        int itemHeight = list.slotHeight;
        //#endif

        // tie scroll area to item height with a cap of 0.5 of the list height
        float scrollAreaSize = (int) (itemHeight * 1.3F);
        float scrollAreaBottom = UMath.clampFloat(scrollAreaSize / (bottom - top), 0, 0.5F);
        float scrollAreaTop = 1 - scrollAreaBottom;

        // gradient of dragY between top and bottom of list
        float gradientTopToBottom = (float) coerceIn((yPos - top) / (float) (bottom - top), 0, 1);

        // process scrolling
        if ((gradientTopToBottom > scrollAreaTop || gradientTopToBottom < scrollAreaBottom)) {
            if (canScrollUp && gradientTopToBottom < scrollAreaBottom) {
                float edgeProximityScaledAmount = ((gradientTopToBottom - scrollAreaBottom) * 8) - 1; // < 1 to ensure squaring will increase
                edgeProximityScaledAmount *= edgeProximityScaledAmount // square for more pronounced effect also is now positive
                        * deltaTimeLimitedByDragTime // scale by timeDelta
                        * 64; // arbitrary final scaling for speed
                //#if MC>=11600
                //$$ list.setScrollAmount(list.getScrollAmount() - edgeProximityScaledAmount);
                //#else
                list.scrollBy(applyScrollFractional(-edgeProximityScaledAmount));
                //#endif

                // update indicator position
                // indicators are set relative to center pos as we want them to visually line up with where the drag is, not where the mouse is
                indicatorPositionUpdater.accept(getDragCenterPos());
            } else if (canScrollDown && gradientTopToBottom > scrollAreaTop) {
                float edgeProximityScaledAmount = ((gradientTopToBottom - scrollAreaTop) * 8) + 1; // > 1 to ensure squaring will increase
                edgeProximityScaledAmount *= edgeProximityScaledAmount// square for more pronounced effect
                        * deltaTimeLimitedByDragTime // scale by timeDelta
                        * 64; // arbitrary final scaling for speed
                //#if MC>=11600
                //$$ list.setScrollAmount(list.getScrollAmount() + edgeProximityScaledAmount);
                //#else
                list.scrollBy(applyScrollFractional(edgeProximityScaledAmount));
                //#endif

                // update indicator position
                // indicators are set relative to center pos as we want them to visually line up with where the drag is, not where the mouse is
                indicatorPositionUpdater.accept(getDragCenterPos());
            }
        }
    }

    //#if MC<11300
    private int applyScrollFractional(float scrollAmount) {
        float frac = scrollAmount - ((int) scrollAmount);
        scrollFractionalTally += frac;
        int truncated = (int) (scrollFractionalTally);
        scrollFractionalTally -= truncated;
        return ((int) scrollAmount) + truncated;
    }

    private float scrollFractionalTally = 0;
    //#endif

    /**
     * if the dragged entry originated from the non reordering list simply leave its original slot waiting for it
     * otherwise only ever place the indicator at 0, matching vanilla behaviour
     */
    public void handleIndicatorForNonReorderingList(List<E> listThatWontReorder, @Nullable List<E> otherList) {
        // do not place indicator if the dragged entry must only return to its original list and this is not the original list
        if (draggedEntryState.mustReturnToOriginalList && listThatWontReorder != draggedEntryState.originalContainer) {
            if (clearListIndicators(listThatWontReorder, otherList)) runForDrawChange.run();
            // allow sounds as we must have moved the drag, should have already been set by placeIndicatorOutsideOfLists(),
            // but may have been missed if the user has really low fps
            draggedEntryState.allowDropSound();
            return;
        }

        if (listThatWontReorder.contains(indicatorEntry)) return; // nothing to do

        draggedEntryState.allowDropSound();
        clearListIndicators(listThatWontReorder, otherList);
        if (listThatWontReorder == draggedEntryState.originalContainer) {
            listThatWontReorder.add(draggedEntryState.originalIndex, indicatorEntry);
        } else {
            listThatWontReorder.add(0, indicatorEntry);
        }
        runForDrawChange.run();
    }



    public void placeIndicatorInListAtIndex(List<E> list, int index, @Nullable List<E> otherList,
                                            int immovableEntryCountStart, int immovableEntryCountEnd) {
        // do not place indicator if the dragged entry must only return to its original list and this is not the original list
        if (draggedEntryState.mustReturnToOriginalList && list != draggedEntryState.originalContainer) {
            if (clearListIndicators(list, otherList)) runForDrawChange.run();
            // allow sounds as we must have moved the drag, should have already been set by placeIndicatorOutsideOfLists(),
            // but may have been missed if the user has really low fps
            draggedEntryState.allowDropSound();
            return;
        }

        // set the index to add to the end of the list considering immovable entries and a potential indicator entry
        if (index == -1 // we are below the lowest entry
                || index >= list.size() - immovableEntryCountEnd){ // we are hovering over an immovable entry
            index = list.size()
                    - (list.contains(indicatorEntry) ? 1 : 0) // factor in the indicator that might be removed, if present
                    - immovableEntryCountEnd; // snap to the last valid index before immovable entries
        }

        if (index >= immovableEntryCountStart && index <= list.size()) {
            if (index < list.size() && indicatorEntry.equals(list.get(index))) {
                return; // already present
            }
            // clear existing list indicators
            clearListIndicators(list, otherList);
            draggedEntryState.allowDropSound(); // allow as we must have moved the drag

            // ensure we don't add the indicator to an invalid end index
            if (index <= list.size() - immovableEntryCountEnd) {
                list.add(index, indicatorEntry);
            }
            runForDrawChange.run();
        } else {
            // clear list indicators
            if (clearListIndicators(list, otherList)) runForDrawChange.run();
            draggedEntryState.allowDropSound(); // allow as we must have moved the drag
        }
    }


    public void placeIndicatorWhenOutsideOfLists(@Nullable List<?> list, @Nullable List<?> otherList) {
        // if the drag has left the lists then we will allow the drop sound even if it returns to its original spot
        draggedEntryState.allowDropSound();

        // place the indicator entry in the original location to indicate where it will snap back too
        if (draggedEntryState.originalContainer.size() == draggedEntryState.originalIndex // was the last entry and there is no indicator replacing it yet
                || draggedEntryState.originalContainer.get(draggedEntryState.originalIndex) != indicatorEntry) {
            clearListIndicators(list, otherList);
            draggedEntryState.originalContainer.add(draggedEntryState.originalIndex, indicatorEntry);
            runForDrawChange.run();
        }
    }

    /**
     * Removes the indicator entry from the lists if it is present.
     *
     * @return true if the indicator was removed from either list
     */
    private boolean clearListIndicators(@Nullable List<?> list, @Nullable List<?> otherList) {
        return (list != null && list.remove(indicatorEntry))
                | (otherList != null && otherList.remove(indicatorEntry));
    }

    public ScreenPosition getDragCenterPos() {
        return draggedEntryState.getDragCenterPos();
    }

    @SuppressWarnings("unused") // used in >=12002
    public boolean isThisListTheDragOrigin(List<E> list) {
        return draggedEntryState != null && draggedEntryState.originalContainer == list;
    }

    //#if MC>=11600
    //$$ public static ServerSelectionList.Entry initServerIndicator(MultiplayerScreen screen, ServerSelectionList list) {
    //$$    return list.new NormalEntry(screen, new ServerData("nil", "nil",
    //#if MC>=12002
    //$$            ServerInfo.ServerType.OTHER
    //#else
    //$$            false
    //#endif
    //$$        )) {
    //$$
    //#if MC>=12109
    //$$        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
    //#elseif MC>=12000
    //$$        @Override public void render(final DrawContext context, final int index, final int y, final int x, final int entryWidth, final int entryHeight, final int mouseX, final int mouseY, final boolean hovered, final float tickDelta) {
    //#else
    //$$        @Override public void render(MatrixStack arg, int m, int n, int o, int p, int q, int r, int t, boolean bl, float f) {
    //#endif
    //$$            // no drawing as this is an "empty" entry
    //$$        }
    //$$    };
    //$$ }
    //#else
    public static ServerListEntryNormal initServerIndicator(GuiMultiplayer screen, @SuppressWarnings("unused") ServerSelectionList list) {
        return new ServerListEntryNormal(screen, new ServerData("nil", "nil", false)) {
            //#if MC>=11200
            @Override public void drawEntry(final int slotIndex, final int x, final int y, final int listWidth, final int slotHeight, final int mouseX, final int mouseY, final boolean isSelected, final float partialTicks) {
            //#else
            //$$ @Override public void drawEntry(final int slotIndex, final int x, final int y, final int listWidth, final int slotHeight, final int mouseX, final int mouseY, final boolean isSelected) {
            //#endif
                // no drawing as this is an "empty" entry
            }
        };
    }
    //#endif

    //#if MC>=11600
    //$$ public static ResourcePackList.ResourcePackEntry initResourcePackIndicator(final PackScreen screen) {
    //$$    ResourcePackList list = ((PackScreenAccessor)screen).essential$getAvailablePackList();
    //#if MC>=12109
    //$$    return list.new ResourcePackEntry(
    //#else
    //$$    return new ResourcePackList.ResourcePackEntry(
    //#endif
    //$$            Minecraft.getInstance(),
    //$$            list,
    //#if MC<11904
    //$$            screen,
    //#endif
    //$$            new FakeIPack()){
    //$$
    //#if MC>=12109
    //$$        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
    //#elseif MC>=12000
    //$$        @Override public void render(final DrawContext context, final int index, final int y, final int x, final int entryWidth, final int entryHeight, final int mouseX, final int mouseY, final boolean hovered, final float tickDelta) {
    //#else
    //$$        @Override public void render(MatrixStack arg, int m, int n, int o, int p, int q, int r, int t, boolean bl, float f) {
    //#endif
    //$$            // no drawing as this is an "empty" entry
    //$$        }
    //$$    };
    //$$ }
    //#else
    public static ResourcePackListEntryDefault initResourcePackIndicator(final GuiScreenResourcePacks screen) {
        return new ResourcePackListEntryDefault(screen) {
            //#if MC>=11200
            @Override public void drawEntry(final int slotIndex, final int x, final int y, final int listWidth, final int slotHeight, final int mouseX, final int mouseY, final boolean isSelected, final float partialTicks) {
            //#else
            //$$ @Override public void drawEntry(final int slotIndex, final int x, final int y, final int listWidth, final int slotHeight, final int mouseX, final int mouseY, final boolean isSelected) {
            //#endif
                // no drawing as this is an "empty" entry
            }
        };
    }
    //#endif


    public interface DataChangeReflector<E extends
            //#if MC>=11904
            //$$ AlwaysSelectedEntryListWidget.Entry<E>
            //#elseif MC>=11600
            //$$ AbstractList.AbstractListEntry<E>
            //#else
            GuiListExtended.IGuiListEntry
            //#endif
            > {
        void
        reflectChanges(E entry, int originalIndex, int placedIndex, boolean fromFirstList, boolean toFirstList);
    }

    // record for current dragged entry
    private static class ResourceEntryDragState<E extends
            //#if MC>=11904
            //$$ AlwaysSelectedEntryListWidget.Entry<E>
            //#elseif MC>=11600
            //$$ AbstractList.AbstractListEntry<E>
            //#else
            GuiListExtended.IGuiListEntry
            //#endif
            > {
        private final E entry;
        private final ScreenPosition mouseHoldOffset;
        private final ScreenPosition mouseStartPos;
        private final List<E> originalContainer;
        private final int originalIndex;
        private long dragStartTime = -1;
        private final boolean mustReturnToOriginalList;
        private boolean playDropSound = false;

        public ScreenPosition getDragCenterPos() {
            return dragCenterPos;
        }

        // store last middle points of dragged entry used for determining drag position as mouse can move outside of drag bounds
        private ScreenPosition dragCenterPos;

        private ResourceEntryDragState(E entry, List<E> originalContainer, double mouseXHoldOffset, double mouseYHoldOffset, int originalIndex, double mouseX, double mouseY, boolean mustReturnToOriginalList) {
            this.entry = entry;
            this.originalContainer = originalContainer;
            this.mouseHoldOffset = new ScreenPosition(mouseXHoldOffset, mouseYHoldOffset);
            this.originalIndex = originalIndex;
            this.dragCenterPos = new ScreenPosition(mouseX, mouseY);
            this.mouseStartPos = new ScreenPosition(mouseX, mouseY);
            this.mustReturnToOriginalList = mustReturnToOriginalList;
        }

        public void allowDropSound() {
            playDropSound = true;
        }

        public void playDropSoundIfAllowed(){
            if (playDropSound) USound.INSTANCE.playButtonPress();
        }

        private void setStartTime(){
            this.dragStartTime = System.currentTimeMillis();
        }

        /**
         * @return true if the position was updated
         */
        private boolean updatePos(ScreenPosition newPos){
            if (!dragCenterPos.equals(newPos)) {
                dragCenterPos = newPos;
                return true;
            }
            return false;
        }

        private void revertToOriginalContainer(BiConsumer<E, Integer> onRevertedDrag) {
            originalContainer.add(originalIndex, entry);
            onRevertedDrag.accept(entry, originalIndex);
        }


        private boolean hasMouseMoved(double mouseX, double mouseY) {
            // use int position as we won't consider any micro movement that does not visually move the entry, which is locked to an int grid
            return  !mouseStartPos.equalsInts((int) mouseX, (int) mouseY);
        }

        private float getDragTimeSeconds() {
            return (System.currentTimeMillis() - dragStartTime) / 1000f;
        }
    }

    /**
     * Represents a position on the screen.
     * Encapsulated for relation of the two values, ease of comparison, and finality.
     */
    public static class ScreenPosition {
        private final double x;
        private final double y;

        public ScreenPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ScreenPosition that = (ScreenPosition) o;
            return x == that.x && y == that.y;
        }

        public boolean equalsInts(final int x, final int y) {
            return (int) this.x == x && (int) this.y == y;
        }
    }

//#if MC>=11600
//$$    // dummy pack interface instance for empty entry in 1.16
//$$    private static class FakeIPack implements net.minecraft.client.gui.screen.PackLoadingManager.IPack{
//$$        @Override public net.minecraft.util.ResourceLocation func_241868_a(){return identifier("essential_dummy:drag_indicator");}
//$$        @Override public net.minecraft.resources.PackCompatibility func_230460_a_(){return net.minecraft.resources.PackCompatibility.COMPATIBLE;}
//$$        @Override public net.minecraft.util.text.ITextComponent func_230462_b_(){return textLiteral("essential$indicator");}
//$$        @Override public net.minecraft.util.text.ITextComponent func_230463_c_(){return textLiteral("essential$indicator");}
//$$
//#if MC>=11904
//$$        @Override public String getName() {return "nil";}
//#endif
//$$
//#if MC>=11903
//$$        @Override public net.minecraft.resource.ResourcePackSource getSource() {return net.minecraft.resource.ResourcePackSource.NONE;}
//#else
//$$        @Override public net.minecraft.resources.IPackNameDecorator func_230464_d_() {return (a)->a;}
//#endif
//$$
//$$        @Override public boolean func_230465_f_() {return false;}
//$$        @Override public boolean func_230466_g_() {return false;}
//$$        @Override public void func_230471_h_() {}
//$$        @Override public void func_230472_i_() {}
//$$        @Override public void func_230467_j_() {}
//$$        @Override public void func_230468_k_() {}
//$$        @Override public boolean func_230473_l_() {return false;}
//$$        @Override public boolean func_230469_o_() {return false;}
//$$        @Override public boolean func_230470_p_() {return false;}
//$$    }
//#endif
}
