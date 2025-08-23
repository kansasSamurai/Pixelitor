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

package pixelitor.utils;

import net.jafama.FastMath;

import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.awt.geom.PathIterator.SEG_LINETO;
import static java.awt.geom.PathIterator.SEG_MOVETO;
import static pixelitor.utils.Geometry.add;
import static pixelitor.utils.Geometry.calcPerpendicularPoints;
import static pixelitor.utils.Geometry.normalize;
import static pixelitor.utils.Geometry.scale;
import static pixelitor.utils.Geometry.subtract;

/**
 * A {@link Stroke} implementation that creates a stroke that gradually tapers along its path.
 */
public class TaperingStroke implements Stroke {
    private final double maxStrokeWidth;
    private final boolean reverse;

    public TaperingStroke(double maxStrokeWidth) {
        this(maxStrokeWidth, false);
    }

    public TaperingStroke(double maxStrokeWidth, boolean reverse) {
        if (maxStrokeWidth <= 0) {
            throw new IllegalArgumentException("maxStrokeWidth = " + maxStrokeWidth);
        }
        this.maxStrokeWidth = maxStrokeWidth;
        this.reverse = reverse;
    }

    @Override
    public Shape createStrokedShape(Shape shape) {
        Path2D taperedOutline = new Path2D.Double();

        // collect points along the path before processing
        List<Point2D> points = new ArrayList<>();
        double[] coords = new double[6];
        var it = new FlatteningPathIterator(shape.getPathIterator(null), 1);
        while (!it.isDone()) {
            switch (it.currentSegment(coords)) {
                case SEG_MOVETO: // start of a new subpath
                    // process any existing points for the last subpath
                    if (!points.isEmpty()) {
                        if (reverse) {
                            Collections.reverse(points);
                        }
                        createSubpathOutline(points, taperedOutline);
                        points.clear();
                    }
                    // fallthrough: add the MOVETO point just like LINETO
                    // noinspection fallthrough
                case SEG_LINETO:
                    points.add(new Point2D.Double(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_CLOSE:
                    // check if it was closed before reaching the first
                    Point2D first = points.getFirst();
                    if (coords[0] != first.getX() || coords[1] != first.getY()) {
                        points.add(first);
                    }
                    break;
            }
            it.next();
        }

        // process any remaining points for the last subpath
        if (!points.isEmpty()) {
            if (reverse) {
                Collections.reverse(points);
            }
            createSubpathOutline(points, taperedOutline);
        }

        return taperedOutline;
    }

    // creates the tapered outline for a subpath,
    // always starting from the full width and going to zero
    private void createSubpathOutline(List<Point2D> points, Path2D result) {
        if (points.size() < 2) {
            return;
        }

        double[] segmentLengths = new double[points.size() - 1];
        double totalPathLength = 0;
        for (int i = 0; i < segmentLengths.length; i++) {
            Point2D a = points.get(i);
            Point2D b = points.get(i + 1);
            totalPathLength += segmentLengths[i] = FastMath.hypot(a.getX() - b.getX(), a.getY() - b.getY());
        }

        // handle the first segment of the subpath
        Point2D first = points.get(0);
        Point2D second = points.get(1);
        Point2D firstLeft = new Point2D.Double();
        Point2D firstRight = new Point2D.Double();
        double initialDist = maxStrokeWidth / 2;
        calcPerpendicularPoints(first, second, initialDist, firstLeft, firstRight);

        // start the outline path
        result.moveTo(firstLeft.getX(), firstLeft.getY());
        double distanceCovered = segmentLengths[0];

        // store points for the return path
        Point2D[] returnPath = new Point2D[points.size() - 1];
        returnPath[0] = firstRight;

        // process intermediate points
        for (int i = 1, s = points.size() - 1; i < s; i++) {
            Point2D prev = points.get(i - 1);
            Point2D current = points.get(i);
            Point2D next = points.get(i + 1);

            // temporary points for storing perpendicular vectors
            var prevPerp1 = new Point2D.Double();
            var prevPerp2 = new Point2D.Double();
            var nextPerp1 = new Point2D.Double();
            var nextPerp2 = new Point2D.Double();

            // the stroke width decreases linearly from the
            // starting point to the endpoint of the path
            double currentWidth = (maxStrokeWidth - maxStrokeWidth * distanceCovered / totalPathLength) / 2;
            distanceCovered += segmentLengths[i - 1];

            // calculate perpendicular points for both segments meeting at current point
            calcPerpendicularPoints(current, prev, prevPerp1, prevPerp2);
            calcPerpendicularPoints(current, next, nextPerp1, nextPerp2);

            // average the perpendicular vectors to create smooth transitions
            Point2D avgLeft = Geometry.midPoint(prevPerp1, nextPerp2);
            Point2D avgRight = Geometry.midPoint(prevPerp2, nextPerp1);

            // normalize and scale the vectors to current stroke width
            normalizeAndScale(avgLeft, current, currentWidth);
            normalizeAndScale(avgRight, current, currentWidth);

            // store for later
            returnPath[i] = avgLeft;

            // add to forward path
            result.lineTo(avgRight.getX(), avgRight.getY());
        }

        // complete the outline by going to the endpoint
        Point2D last = points.getLast();
        result.lineTo(last.getX(), last.getY());

        // draw the return path back to start
        for (int i = returnPath.length - 1; i >= 0; i--) {
            Point2D P = returnPath[i];
            result.lineTo(P.getX(), P.getY());
        }

        result.closePath();
    }

    private static void normalizeAndScale(Point2D avgLeft, Point2D currentPoint, double currentWidth) {
        // convert to vector from current point
        subtract(avgLeft, currentPoint, avgLeft);

        normalize(avgLeft);
        scale(avgLeft, currentWidth);

        // move back to absolute position
        add(avgLeft, currentPoint, avgLeft);
    }
}
