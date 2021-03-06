/*
 * Copyright 2019 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.gui;

import org.jdesktop.swingx.painter.CheckerboardPainter;
import pixelitor.Canvas;
import pixelitor.colors.ColorUtils;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.gui.utils.GUIUtils;
import pixelitor.menus.view.ZoomLevel;
import pixelitor.menus.view.ZoomMenu;
import pixelitor.utils.CompActivationListener;
import pixelitor.utils.Cursors;
import pixelitor.utils.ImageUtils;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;

/**
 * The navigator component that allows the user to pan a zoomed-in image.
 */
public class Navigator extends JComponent
    implements MouseListener, MouseMotionListener, CompActivationListener {

    private static final int DEFAULT_NAVIGATOR_SIZE = 300;
    private static final BasicStroke VIEW_BOX_STROKE = new BasicStroke(3);
    private static final CheckerboardPainter checkerBoardPainter
            = ImageUtils.createCheckerboardPainter();

    private View view; // can be null if all images are closed
    private boolean dragging = false;
    private double imgScalingRatio;
    private Rectangle viewBoxRect;
    private Point dragStartPoint;
    private Point origRectLoc; // the view box rectangle location before starting the drag
    private int thumbWidth;
    private int thumbHeight;
    private int viewWidth;
    private int viewHeight;
    private JScrollPane scrollPane;
    private final AdjustmentListener adjListener;
    private static JDialog dialog;
    private JPopupMenu popup;
    private static Color viewBoxColor = Color.RED;

    private int preferredWidth;
    private int preferredHeight;

    // it not null, the scaling factor should be calculated
    // based on this instead of the navigator size
    private ZoomLevel exactZoom = null;

    private Navigator(View view) {
        adjListener = e ->
                SwingUtilities.invokeLater(this::updateViewBoxPosition);

        recalculateSize(view, true, true, true);

        addMouseListener(this);
        addMouseMotionListener(this);
        OpenComps.addActivationListener(this);

        addNavigatorResizedListener();
        addMouseWheelZoomingSupport();

        ZoomMenu.setupZoomKeys(this);
        addPopupMenu();
    }

    private void addPopupMenu() {
        popup = new JPopupMenu();
        ZoomLevel[] levels = {ZoomLevel.Z100, ZoomLevel.Z50, ZoomLevel.Z25, ZoomLevel.Z12};
        for (ZoomLevel level : levels) {
            popup.add(new AbstractAction("Navigator Zoom: " + level.toString()) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setNavigatorSizeFromZoom(level);
                }
            });
        }
        popup.addSeparator();
        popup.add(new AbstractAction("View Box Color...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ColorUtils.selectColorWithDialog(Navigator.this,
                        "View Box Color", viewBoxColor, true,
                        Navigator.this::setNewViewBoxColor);
            }
        });
    }

    private void setNewViewBoxColor(Color newColor) {
        viewBoxColor = newColor;
        repaint();
    }

    private void setNavigatorSizeFromZoom(ZoomLevel zoom) {
        Canvas canvas = view.getCanvas();
        double scale = zoom.getViewScale();
        preferredWidth = (int) (scale * canvas.getImWidth());
        preferredHeight = (int) (scale * canvas.getImHeight());

        JDialog ancestor = GUIUtils.getDialogAncestor(this);
        ancestor.setTitle("Navigator - " + zoom.toString());

        exactZoom = zoom; // set the the exact zoom only temporarily

        // force pack() to use the current preferred
        // size instead of some cached value
        invalidate();

        ancestor.pack();
    }

    private void showPopup(MouseEvent e) {
        popup.show(this, e.getX(), e.getY());
    }

    private void addNavigatorResizedListener() {
        // if the navigator is resized, then the size calculations
        // have to be refreshed
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (Navigator.this.view != null) { // it is null if all images are closed
                    recalculateSize(Navigator.this.view, false, false, true);
                }
            }
        });
    }

    private void addMouseWheelZoomingSupport() {
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) { // up, away from the user
                    // this.view will be always the active image...
                    if (view != null) { // ...and it is null if all images are closed
                        view.increaseZoom();
                    }
                } else {  // down, towards the user
                    if (view != null) {
                        view.decreaseZoom();
                    }
                }
            }
        });
    }

    public static void showInDialog(PixelitorWindow pw) {
        View view = OpenComps.getActiveView();
        Navigator navigator = new Navigator(view);

        if (dialog != null && dialog.isVisible()) {
            dialog.setVisible(false);
            dialog.dispose();
        }

        dialog = new DialogBuilder()
                .title("Navigator")
                .owner(pw)
                .content(navigator)
                .notModal()
                .noOKButton()
                .noCancelButton()
                .noGlobalKeyChange()
                .cancelAction(navigator::dispose) // when it is closed with X
                .show();
    }

    public void recalculateSize(View view,
                                boolean newCV,
                                boolean cvSizeChanged,
                                boolean navigatorResized) {
        assert newCV || cvSizeChanged || navigatorResized : "why did you call me?";

        if (newCV) {
            if (this.view != null) {
                releaseImage();
            }

            this.view = view;
            scrollPane = view.getViewContainer().getScrollPane();
        }

        if (exactZoom == null) {
            JDialog ancestor = GUIUtils.getDialogAncestor(this);
            if (ancestor != null) { // is null during the initial construction
                ancestor.setTitle("Navigator");
            }
        }

        if (newCV) {
            recalculateScaling(view, DEFAULT_NAVIGATOR_SIZE, DEFAULT_NAVIGATOR_SIZE);
        } else if (cvSizeChanged || navigatorResized) {
            recalculateScaling(view, getWidth(), getHeight());
        } else {
            throw new IllegalStateException();
        }

        preferredWidth = thumbWidth;
        preferredHeight = thumbHeight;

        updateViewBoxPosition();

        if (newCV) {
            view.setNavigator(this);
            scrollPane.getHorizontalScrollBar().addAdjustmentListener(adjListener);
            scrollPane.getVerticalScrollBar().addAdjustmentListener(adjListener);
        }

        if (cvSizeChanged) {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.pack();
            }
        }
        repaint();
    }

    private void releaseImage() {
        view.setNavigator(null);
        scrollPane.getHorizontalScrollBar().removeAdjustmentListener(adjListener);
        scrollPane.getVerticalScrollBar().removeAdjustmentListener(adjListener);

        view = null;
    }

    // updates the view box rectangle position based on the view
    private void updateViewBoxPosition() {
        if (dragging) {
            // no need to update the rectangle if the change
            // was caused by this navigator
            return;
        }

        JViewport viewport = scrollPane.getViewport();
        Rectangle viewRect = viewport.getViewRect();

        Dimension d = viewport.getViewSize();
        viewWidth = d.width;
        viewHeight = d.height;

        double scaleX = (double) thumbWidth / viewWidth;
        double scaleY = (double) thumbHeight / viewHeight;

        int boxX = (int) (viewRect.x * scaleX);
        int boxY = (int) (viewRect.y * scaleY);
        int boxWidth = (int) (viewRect.width * scaleX);
        int boxHeight = (int) (viewRect.height * scaleY);

        viewBoxRect = new Rectangle(boxX, boxY, boxWidth, boxHeight);
        repaint();
    }

    // scrolls the main composition view based on the view box position
    private void scrollView() {
        double scaleX = (double) viewWidth / thumbWidth;
        double scaleY = (double) viewHeight / thumbHeight;

        int bigX = (int) (viewBoxRect.x * scaleX);
        int bigY = (int) (viewBoxRect.y * scaleY);
        int bigWidth = (int) (viewBoxRect.width * scaleX);
        int bigHeight = (int) (viewBoxRect.height * scaleY);

        view.scrollRectToVisible(new Rectangle(bigX, bigY, bigWidth, bigHeight));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (view == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g;

        checkerBoardPainter.paint(g2, null, thumbWidth, thumbHeight);

        AffineTransform origTX = g2.getTransform();

        g2.scale(imgScalingRatio, imgScalingRatio);
        g2.drawImage(view.getComp().getCompositeImage(), 0, 0, null);
        g2.setTransform(origTX);

        g2.setStroke(VIEW_BOX_STROKE);
        g2.setColor(viewBoxColor);
        g2.draw(viewBoxRect);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e);
        } else {
            Point point = e.getPoint();
            if (viewBoxRect.contains(point) && view != null) {
                dragStartPoint = point;
                origRectLoc = viewBoxRect.getLocation();
                dragging = true;
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e);
        }
        dragStartPoint = null;
        dragging = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragStartPoint != null) {
            assert dragging;

            Point mouseNow = e.getPoint();
            int dx = mouseNow.x - dragStartPoint.x;
            int dy = mouseNow.y - dragStartPoint.y;

            int newBoxX = origRectLoc.x + dx;
            int newBoxY = origRectLoc.y + dy;

            // make sure that the view box does not leave the thumb
            if (newBoxX < 0) {
                newBoxX = 0;
            }
            if (newBoxY < 0) {
                newBoxY = 0;
            }
            if (newBoxX + viewBoxRect.width > thumbWidth) {
                newBoxX = thumbWidth - viewBoxRect.width;
            }
            if (newBoxY + viewBoxRect.height > thumbHeight) {
                newBoxY = thumbHeight - viewBoxRect.height;
            }

            updateViewBoxLocation(newBoxX, newBoxY);
        }
    }

    private void updateViewBoxLocation(int newBoxX, int newBoxY) {
        if (newBoxX != viewBoxRect.x || newBoxY != viewBoxRect.y) {
            viewBoxRect.setLocation(newBoxX, newBoxY);
            repaint();
            scrollView();
        }
    }

    private void recalculateScaling(View view, int width, int height) {
        Canvas canvas = view.getCanvas();
        int canvasWidth = canvas.getImWidth();
        int canvasHeight = canvas.getImHeight();

        if (exactZoom != null) {
            imgScalingRatio = exactZoom.getViewScale();

            exactZoom = null; // was set only temporarily
        } else {
            double xScaling = width / (double) canvasWidth;
            double yScaling = height / (double) canvasHeight;

            imgScalingRatio = Math.min(xScaling, yScaling);
        }

        thumbWidth = (int) (canvasWidth * imgScalingRatio);
        thumbHeight = (int) (canvasHeight * imgScalingRatio);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // not interested
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // not interested
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // not interested
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        resetCursor(e.getX(), e.getY());
    }

    private void resetCursor(int x, int y) {
        if (viewBoxRect.contains(x, y)) {
            setCursor(Cursors.HAND);
        } else {
            setCursor(Cursors.DEFAULT);
        }
    }

    @Override
    public void allCompsClosed() {
        releaseImage();
        repaint();
    }

    @Override
    public void compActivated(View oldView, View newView) {
        recalculateSize(newView, true, true, false);
    }

    // called when the dialog is closed - then this
    // navigator instance is no longer needed
    private void dispose() {
        OpenComps.removeActivationListener(this);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(preferredWidth, preferredHeight);
    }
}


