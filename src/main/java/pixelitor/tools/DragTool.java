/*
 * Copyright 2025 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.tools;

import pixelitor.Composition;
import pixelitor.gui.GlobalEvents;
import pixelitor.tools.util.Drag;
import pixelitor.tools.util.OverlayType;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.utils.debug.DebugNode;

import java.awt.Cursor;
import java.awt.Graphics2D;

/**
 * An abstract base class for tools that operate based on a "drag" gesture,
 * defined by a start point (mouse press) and an end point (mouse release).
 */
public abstract class DragTool extends Tool {
    // encapsulates the current start and end points of the drag
    protected Drag drag;

    // the current state of the drag tool (managed by concrete subclasses)
    protected DragToolState state = DragToolState.IDLE;

    // whether the end point inside the Drag object is initialized
    private boolean endPointInitialized = false;

    // whether the starting point is adjusted when space is pressed during drag
    protected boolean repositionOnSpace = false;

    // whether movement is constrained to multiples
    // of 45 degree angles when Shift is pressed
    private final boolean shiftConstrains;

    protected DragTool(String name, char hotkey, String toolMessage,
                       Cursor cursor, boolean shiftConstrains) {

        super(name, hotkey, toolMessage, cursor);

        this.shiftConstrains = shiftConstrains;
    }

    @Override
    public void mousePressed(PMouseEvent e) {
        drag = new Drag();
        drag.setStart(e);

        dragStarted(e);

        endPointInitialized = false;
    }

    @Override
    public void mouseDragged(PMouseEvent e) {
        // the drag could be null if this tool was activated while dragging
        if (drag == null) {
            mousePressed(e); // start drag as if mouse was just pressed
        }

        if (drag.isCanceled()) {
            return;
        }
        if (repositionOnSpace) {
            drag.saveEndValues();
        }
        if (shiftConstrains) {
            drag.setAngleConstrained(e.isShiftDown());
        }

        drag.setEnd(e);

        if (repositionOnSpace) {
            if (endPointInitialized && GlobalEvents.isSpaceDown()) {
                drag.panStartPoint(e.getView());
            }

            endPointInitialized = true;
        }

        ongoingDrag(e);
    }

    @Override
    public void mouseReleased(PMouseEvent e) {
        if (drag == null) { // can happen in a synthetic mouse released event
            return;
        }
        if (drag.isCanceled()) {
            return;
        }

        drag.setEnd(e);
        drag.mouseReleased();
        dragFinished(e);
        endPointInitialized = false;
    }

    /**
     * Called when a drag is started.
     */
    protected abstract void dragStarted(PMouseEvent e);

    /**
     * Called continuously while a drag is in progress.
     */
    protected abstract void ongoingDrag(PMouseEvent e);

    /**
     * Called when a drag is finished
     */
    protected abstract void dragFinished(PMouseEvent e);

    @Override
    public void paintOverCanvas(Graphics2D g2, Composition comp) {
        if (drag == null || !drag.isDragging()) {
            return;
        }

        getOverlayType().draw(g2, drag);
    }

    protected OverlayType getOverlayType() {
        return OverlayType.WIDTH_HEIGHT; // default to width/height display
    }

    public DragToolState getState() {
        return state;
    }

    @Override
    public DebugNode createDebugNode(String key) {
        DebugNode node = super.createDebugNode(key);

        node.addAsString("state", state);

        return node;
    }
}
