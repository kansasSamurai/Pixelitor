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

package pixelitor.tools.brushes;

import pixelitor.Composition;
import pixelitor.filters.gui.RangeParam;
import pixelitor.gui.View;
import pixelitor.tools.util.PPoint;
import pixelitor.utils.debug.DebugNode;

import java.awt.Graphics2D;

/**
 * A brush with the "lazy mouse" feature enabled is
 * a decorator for a delegate brush
 */
public class LazyMouseBrush implements Brush {
    private static final int MIN_DIST = 10;
    private static final int DEFAULT_DIST = 30;
    private static final int MAX_DIST = 200;

    private static final int MIN_SPACING = 1;
    private static final int DEFAULT_SPACING = 3;
    private static final int MAX_SPACING = 20;

    private final Brush delegate;
    private double mouseX;
    private double mouseY;
    private double drawX;
    private double drawY;
    private View view;
    private double spacing;
    private static int defaultSpacing = DEFAULT_SPACING;

    // the lazy mouse distance is shared between the tools
    private static int minDist = DEFAULT_DIST;
    private static double minDist2 = DEFAULT_DIST * DEFAULT_DIST;

    public LazyMouseBrush(Brush delegate) {
        this.delegate = delegate;

        // copy the previous position of the delegate so that
        // if this object starts with shift-clicked lines, the
        // old positions are continued
        PPoint previous = delegate.getPrevious();
        if (previous != null) {
            drawX = previous.getImX();
            drawY = previous.getImY();
        }

        calcSpacing();
    }

    private static void setDist(int value) {
        minDist = value;
        minDist2 = value * value;
    }

    private static void setDefaultSpacing(int value) {
        defaultSpacing = value;
    }

    private void calcSpacing() {
        spacing = delegate.getPreferredSpacing();
        if (spacing == 0) {
            spacing = defaultSpacing;
        }
    }

    @Override
    public void setTarget(Composition comp, Graphics2D g) {
        delegate.setTarget(comp, g);

        view = comp.getView();
    }

    @Override
    public void setRadius(double radius) {
        delegate.setRadius(radius);
    }

    @Override
    public void setPrevious(PPoint previous) {
        delegate.setPrevious(previous);
    }

    @Override
    public double getEffectiveRadius() {
        return delegate.getEffectiveRadius();
    }

    @Override
    public PPoint getPrevious() {
        return delegate.getPrevious();
    }

    @Override
    public boolean isDrawing() {
        return delegate.isDrawing();
    }

    @Override
    public void initDrawing(PPoint p) {
        delegate.initDrawing(p);
    }

    @Override
    public void startAt(PPoint p) {
        delegate.startAt(p);

        mouseX = p.getImX();
        mouseY = p.getImY();

        drawX = mouseX;
        drawY = mouseY;

        calcSpacing();
    }

    @Override
    public void continueTo(PPoint p) {
        advanceTo(p);
    }

    private void advanceTo(PPoint p) {
        mouseX = p.getImX();
        mouseY = p.getImY();

        double dx = mouseX - drawX;
        double dy = mouseY - drawY;

        double dist2 = dx * dx + dy * dy;

        double angle = Math.atan2(dy, dx);
        double advanceDX = spacing * Math.cos(angle);
        double advanceDY = spacing * Math.sin(angle);

        // It is important to consider here the spacing in order to avoid
        // infinite loops for large spacings (shape brush + large radius).
        // The math might not be 100% correct, but it looks OK.
        double minValue = minDist2 + spacing * spacing;

        while (dist2 > minValue) {
            drawX += advanceDX;
            drawY += advanceDY;

            PPoint drawPoint = PPoint.eagerFromIm(drawX, drawY, view);
//            if (lineConnect) {
//                delegate.lineConnectTo(drawPoint);
//            } else {
                delegate.continueTo(drawPoint);
//            }

            dx = mouseX - drawX;
            dy = mouseY - drawY;
            dist2 = dx * dx + dy * dy;
        }
    }

    @Override
    public void lineConnectTo(PPoint p) {
        assert !isDrawing();
        assert hasPrevious();
        initDrawing(p);

        advanceTo(p);
    }

    @Override
    public void finish() {
        delegate.finish();
    }

    @Override
    public DebugNode getDebugNode() {
        return delegate.getDebugNode();
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public double getPreferredSpacing() {
        return delegate.getPreferredSpacing();
    }

    public static RangeParam createDistParam() {
        RangeParam param = new RangeParam(
            "Distance (px)", MIN_DIST, minDist, MAX_DIST);
        param.setAdjustmentListener(() ->
                setDist(param.getValue()));
        return param;
    }

    public static RangeParam createSpacingParam() {
        RangeParam param = new RangeParam(
            "Spacing (px)", MIN_SPACING, defaultSpacing, MAX_SPACING);
        param.setAdjustmentListener(() ->
                setDefaultSpacing(param.getValue()));
        return param;
    }

    public PPoint getDrawPoint() {
        return PPoint.eagerFromIm(drawX, drawY, view);
    }
}
