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

package pixelitor.filters.jhlabsproxies;

import com.jhlabs.image.BoxBlurFilter;
import com.jhlabs.image.VariableBlurFilter;
import pixelitor.filters.ParametrizedFilter;
import pixelitor.filters.gui.*;
import pixelitor.utils.BlurredShape;
import pixelitor.utils.ImageUtils;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.Serial;

/**
 * Focus filter based on the JHLabs VariableBlurFilter
 */
public class JHFocus extends ParametrizedFilter {
    public static final String NAME = "Focus";

    @Serial
    private static final long serialVersionUID = 6331340888057548063L;

    private final ImagePositionParam center = new ImagePositionParam("Focused Area Center");
    private final GroupedRangeParam radius = new GroupedRangeParam("Focused Area Radius (Pixels)", 0, 200, 1000, false);
    private final RangeParam softness = new RangeParam("Transition Softness", 0, 20, 100);
    private final GroupedRangeParam blurRadius = new GroupedRangeParam("Blur Radius", 0, 10, 48);
    private final RangeParam numIterations = new RangeParam("Blur Iterations (Quality)", 1, 3, 10);
    private final BooleanParam invert = new BooleanParam("Invert");
    private final BooleanParam hpSharpening = BooleanParam.forHPSharpening();
    private final IntChoiceParam shape = BlurredShape.getChoices();

    private FocusImpl filter;

    public JHFocus() {
        super(true);

        initParams(
            center.withDecimalPlaces(0),
            radius,
            softness,
            shape,
            blurRadius,
            numIterations,
            invert,
            hpSharpening
        );
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        int hRadius = blurRadius.getValue(0);
        int vRadius = blurRadius.getValue(1);
        if (hRadius == 0 && vRadius == 0) {
            return src;
        }
        if (radius.getValue(0) == 0 || radius.getValue(1) == 0) {
            if (invert.isChecked()) {
                return src;
            }
            return new BoxBlurFilter(hRadius, vRadius, numIterations.getValue(), getName()).filter(src, dest);
        }

        if (src.getWidth() == 1 || src.getHeight() == 1) {
            // otherwise we can get ArrayIndexOutOfBoundsException
            // in VariableBlurFilter.blur
            return src;
        }

        if (filter == null) {
            filter = new FocusImpl(NAME);
        }

        filter.setCenter(
            src.getWidth() * center.getRelativeX(),
            src.getHeight() * center.getRelativeY()
        );

        double radiusX = radius.getValueAsDouble(0);
        double radiusY = radius.getValueAsDouble(1);
        double softnessFactor = softness.getPercentage();
        filter.setRadius(radiusX, radiusY, softnessFactor);

        filter.setInverted(invert.isChecked());

        filter.setHRadius(blurRadius.getValueAsFloat(0));
        filter.setVRadius(blurRadius.getValueAsFloat(1));

        filter.setIterations(numIterations.getValue());
        filter.setPremultiplyAlpha(!src.isAlphaPremultiplied() && ImageUtils.hasPackedIntArray(src));
        filter.setShape(shape.getValue());

        dest = filter.filter(src, dest);

        if (hpSharpening.isChecked()) {
            dest = ImageUtils.toHighPassSharpenedImage(src, dest);
        }

        return dest;
    }

    @Override
    public boolean supportsGray() {
        return !hpSharpening.isChecked();
    }

    private static class FocusImpl extends VariableBlurFilter {
        private Point2D center;
        private double innerRadiusX;
        private double innerRadiusY;
        private double outerRadiusX;
        private double outerRadiusY;
        private boolean inverted;

        private BlurredShape shape;

        public FocusImpl(String filterName) {
            super(filterName);
        }

        public void setCenter(double cx, double cy) {
            center = new Point2D.Double(cx, cy);
        }

        public void setRadius(double radiusX, double radiusY, double softness) {
            innerRadiusX = radiusX - radiusX * softness;
            innerRadiusY = radiusY - radiusY * softness;

            outerRadiusX = radiusX + radiusX * softness;
            outerRadiusY = radiusY + radiusY * softness;
        }

        @Override
        protected float blurRadiusAt(int x, int y) {
            double outside = shape.isOutside(x, y);
            if (inverted) {
                return (float) (1 - outside);
            }
            return (float) outside;
        }

        public void setInverted(boolean inverted) {
            this.inverted = inverted;
        }

        // must be called after the shape arguments!
        public void setShape(int type) {
            shape = BlurredShape.create(type, center,
                innerRadiusX, innerRadiusY,
                outerRadiusX, outerRadiusY);
        }
    }
}