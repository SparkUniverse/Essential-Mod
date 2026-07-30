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
/*
 *       Copyright (C) 2018-present Hyperium <https://hyperium.cc/>
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU Lesser General Public License as published
 *       by the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU Lesser General Public License for more details.
 *
 *       You should have received a copy of the GNU Lesser General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package gg.essential.event.gui;

import gg.essential.universal.UMatrixStack;
import gg.essential.util.UDrawContext;
import net.minecraft.client.gui.GuiScreen;

public class GuiDrawScreenEvent {

    private final GuiScreen screen;
    private final UDrawContext drawContext;
    private final int originalMouseX;
    private final int originalMouseY;
    private int mouseX;
    private int mouseY;
    private final float partialTicks;
    private final boolean post;

    /**
     * Called whenever the screen is drawn.
     *
     * @param screen GUI that is doing the drawing
     * @param matrixStack Matrix stack at time of drawing
     * @param mouseX X position of the mouse
     * @param mouseY Y position of the mouse
     * @param partialTicks Render partial ticks, used to make things smoother
     * @param post Whether the event is posted before (f) or after (t) the gui has been drawn
     */
    public GuiDrawScreenEvent(GuiScreen screen, UDrawContext drawContext, int mouseX, int mouseY, float partialTicks, boolean post) {
        this.screen = screen;
        this.drawContext = drawContext;
        this.originalMouseX = mouseX;
        this.originalMouseY = mouseY;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.post = post;
    }

    public GuiScreen getScreen() {
        return screen;
    }

    public UDrawContext getDrawContext() {
        return drawContext;
    }

    public UMatrixStack getMatrixStack() {
        return drawContext.getMatrixStack();
    }

    public int getOriginalMouseX() {
        return originalMouseX;
    }

    public int getOriginalMouseY() {
        return originalMouseY;
    }

    public int getMouseX() {
        return mouseX;
    }

    /**
     * Only supported for non-priority pre event.
     */
    public void setMouseX(int mouseX) {
        this.mouseX = mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    /**
     * Only supported for non-priority pre event.
     */
    public void setMouseY(int mouseY) {
        this.mouseY = mouseY;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public boolean isPost() {
        return post;
    }

    public boolean isPre() {
        return !post;
    }

    /** Similar to the parent class but before (for pre) / after (for post) other mods did their thing. */
    public static class Priority extends GuiDrawScreenEvent {
        public Priority(GuiScreen screen, UDrawContext drawContext, int mouseX, int mouseY, float partialTicks, boolean post) {
            super(screen, drawContext, mouseX, mouseY, partialTicks, post);
        }
    }

    public static class Background {
        private final GuiScreen screen;

        public Background(GuiScreen screen) {
            this.screen = screen;
        }

        public GuiScreen getScreen() {
            return screen;
        }
    }
}
