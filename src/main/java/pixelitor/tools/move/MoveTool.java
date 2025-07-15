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

package pixelitor.tools.move;

import pixelitor.Composition;
import pixelitor.Features;
import pixelitor.Views;
import pixelitor.filters.gui.UserPreset;
import pixelitor.gui.View;
import pixelitor.gui.utils.VectorIcon;
import pixelitor.layers.Layer;
import pixelitor.selection.Selection;
import pixelitor.tools.DragTool;
import pixelitor.tools.Tool;
import pixelitor.tools.transform.TransformBox;
import pixelitor.tools.util.ArrowKey;
import pixelitor.tools.util.OverlayType;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.utils.Cursors;

import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ResourceBundle;

/**
 * The move tool.
 */
public class MoveTool extends DragTool {
    private final JComboBox<MoveMode> modeSelector = new JComboBox<>(MoveMode.values());
    private MoveMode activeMode = (MoveMode) modeSelector.getSelectedItem();

    private final JCheckBox autoSelectCheckBox = new JCheckBox();
    private final JCheckBox freeTransformCheckBox = new JCheckBox();

    private TransformBox transformBox;

    public MoveTool() {
        super("Move", 'V',
            "<b>drag</b> to move the active layer. " +
                "<b>Alt-drag</b> or <b>right-mouse-drag</b> to duplicate and move the active layer. " +
                "<b>Shift-drag</b> to constrain movement.",
            Cursors.DEFAULT, true);
    }

    @Override
    public void initSettingsPanel(ResourceBundle resources) {
        String moveText = resources.getString("mt_move");
        settingsPanel.addComboBox(moveText, modeSelector, "modeSelector");
        modeSelector.addActionListener(e ->
            activeMode = (MoveMode) modeSelector.getSelectedItem());

        settingsPanel.addSeparator();
        settingsPanel.addWithLabel("Auto Select Layer:",
            autoSelectCheckBox, "autoSelectCheckBox");

        if (Features.enableFreeTransform) {
            settingsPanel.addWithLabel("Free Transform:",
                freeTransformCheckBox, "freeTransformCheckBox");
            freeTransformCheckBox.addActionListener(e ->
                setFreeTransformMode(freeTransformCheckBox.isSelected()));
        }
    }

    @Override
    public void mouseMoved(MouseEvent e, View view) {
        super.mouseMoved(e, view);

        if (activeMode.movesLayer()) {
            updateMoveCursor(view, e);
        }
        if (transformBox != null) {
            transformBox.mouseMoved(e);
        }
    }

    private void updateMoveCursor(View view, MouseEvent e) {
        if (isAutoSelecting()) {
            Point2D p = view.componentToImageSpace(e.getPoint());
            Layer movedLayer = view.getComp().findLayerAtPoint(p);
            if (movedLayer == null) {
                view.setCursor(Cursors.DEFAULT);
                return;
            }
        }
        view.setCursor(Cursors.MOVE);
    }

    @Override
    protected void dragStarted(PMouseEvent e) {
        if (activeMode.movesLayer() && isAutoSelecting()) {
            Point2D cursorPos = e.toImPoint2D();
            Layer targetLayer = e.getComp().findLayerAtPoint(cursorPos);

            if (targetLayer == null) {
                drag.cancel();
                return;
            }
            e.getComp().setActiveLayer(targetLayer);
        }

        if (transformBox != null) {
            if (transformBox.processMousePressed(e)) {
                return;
            }
        } else {
            e.getComp().prepareMovement(
                activeMode, e.isAltDown() || e.isRight());
        }
    }

    @Override
    protected void ongoingDrag(PMouseEvent e) {
        if (transformBox != null) {
            if (transformBox.processMouseDragged(e)) {
                return;
            }
        } else {
            e.getComp().moveActiveContent(activeMode, drag.getDX(), drag.getDY());
        }
    }

    @Override
    public void dragFinished(PMouseEvent e) {
        if (transformBox != null) {
            if (transformBox.processMouseReleased(e)) {
                Selection selection = e.getComp().getSelection();
                if (selection != null) {
                    selection.finalizeMovement(true);
                }
            }
            return;
        }

        if (!freeTransformCheckBox.isSelected()) {
            e.getComp().finalizeMovement(activeMode);
        }
    }

    /**
     * Programmatically moves the active layer and/or the selection.
     */
    public static void move(Composition comp, MoveMode mode, int imDx, int imDy) {
        comp.prepareMovement(mode, false);
        comp.moveActiveContent(mode, imDx, imDy);
        comp.finalizeMovement(mode);
    }

    @Override
    public void paintOverCanvas(Graphics2D g2, Composition comp) {
        if (transformBox != null) {
            transformBox.paint(g2);
            return;
        }

        if (freeTransformCheckBox.isSelected()) {
            return;
        }

        if (drag == null || !drag.isDragging()) {
            return;
        }

        comp.drawMovementContours(g2, activeMode);
        OverlayType.REL_MOUSE_POS.draw(g2, drag);
    }

    @Override
    protected OverlayType getDragDisplayType() {
        return OverlayType.REL_MOUSE_POS;
    }

    @Override
    public void coCoordsChanged(View view) {
        if (transformBox != null) {
            transformBox.coCoordsChanged(view);
        }
    }

    @Override
    public void imCoordsChanged(AffineTransform at, View view) {
        if (transformBox != null) {
            transformBox.imCoordsChanged(at, view);
        }
    }

    @Override
    public boolean arrowKeyPressed(ArrowKey key) {
        View view = Views.getActive();
        if (view == null) {
            return false;
        }

        if (transformBox != null) {
            transformBox.arrowKeyPressed(key, view);
            return true;
        }

        move(view.getComp(), activeMode, key.getDeltaX(), key.getDeltaY());
        return true;
    }

    private void setFreeTransformMode(boolean enabled) {
        if (enabled) {
            createTransformBox();
        } else {
            transformBox = null;
        }
    }

    @Override
    protected void toolActivated(View view) {
        super.toolActivated(view);
        if (freeTransformCheckBox.isSelected()) {
            createTransformBox();
        }
    }

    @Override
    protected void toolDeactivated(View view) {
        super.toolDeactivated(view);
        transformBox = null;
    }

    private void createTransformBox() {
        View view = Views.getActive();
        Selection sel = view.getComp().getSelection();
        if (sel != null && transformBox == null) {
            // create a transform box around the selection
            Rectangle boxSize = view.imageToComponentSpace(sel.getShapeBounds2D());
            boxSize.grow(10, 10); // make sure the rectangular selections are visible
            transformBox = new TransformBox(boxSize, view, sel, true);
            sel.prepareMovement();
        }
    }

    private boolean isAutoSelecting() {
        return autoSelectCheckBox.isSelected();
    }

    @Override
    public boolean isDirectDrawing() {
        return false;
    }

    @Override
    public void saveStateTo(UserPreset preset) {
        preset.put("Mode", modeSelector.getSelectedItem().toString());
        preset.putBoolean("AutoSelect", isAutoSelecting());
    }

    @Override
    public void loadUserPreset(UserPreset preset) {
        MoveMode mode = preset.getEnum("Mode", MoveMode.class);
        modeSelector.setSelectedItem(mode);

        autoSelectCheckBox.setSelected(preset.getBoolean("AutoSelect"));
    }

    @Override
    public VectorIcon createIcon() {
        return new MoveToolIcon();
    }

    private static class MoveToolIcon extends Tool.ToolIcon {
        @Override
        public void paintIcon(Graphics2D g) {
            // the shape is based on move_tool.svg
            Path2D shape = new Path2D.Double();
            // start at the top
            shape.moveTo(14, 0);
            shape.lineTo(18, 5);
            shape.lineTo(15, 5);
            shape.lineTo(15, 13);

            // east arrow
            shape.lineTo(23, 13);
            shape.lineTo(23, 10);
            shape.lineTo(28, 14);
            shape.lineTo(23, 18);
            shape.lineTo(23, 15);
            shape.lineTo(15, 15);

            // south arrow
            shape.lineTo(15, 23);
            shape.lineTo(18, 23);
            shape.lineTo(14, 28);
            shape.lineTo(10, 23);
            shape.lineTo(13, 23);
            shape.lineTo(13, 15);

            // west arrow
            shape.lineTo(5, 15);
            shape.lineTo(5, 18);
            shape.lineTo(0, 14);
            shape.lineTo(5, 10);
            shape.lineTo(5, 13);
            shape.lineTo(13, 13);

            // finish north arrow
            shape.lineTo(13, 5);
            shape.lineTo(10, 5);
            shape.closePath();

            g.fill(shape);
        }
    }
}