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

package pixelitor.filters.painters;

import pixelitor.utils.debug.DebugNode;
import pixelitor.utils.debug.Debuggable;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A rectangle rotated/scaled/sheared around its center.
 */
public class TransformedRectangle implements Debuggable {
    // Original corner coordinates of the rectangle
    private final double origTopLeftX;
    private final double origTopLeftY;
    private final double origTopRightX;
    private final double origTopRightY;
    private final double origBottomRightX;
    private final double origBottomRightY;
    private final double origBottomLeftX;
    private final double origBottomLeftY;

    // Transformed corner coordinates of the rectangle
    private double topLeftX;
    private double topLeftY;
    private double topRightX;
    private double topRightY;
    private double bottomRightX;
    private double bottomRightY;
    private double bottomLeftX;
    private double bottomLeftY;

    // Cached shape and bounding box of the transformed rectangle
    private Path2D cachedShape;
    private Rectangle cachedBox;

    public TransformedRectangle(Rectangle r,
                                double angle,
                                double scaleX, double scaleY,
                                double shearX, double shearY) {
        this(r.getX(), r.getY(), r.getWidth(), r.getHeight(), angle, scaleX, scaleY, shearX, shearY);
    }

    public TransformedRectangle(double x, double y,
                                double width, double height,
                                double angle,
                                double scaleX, double scaleY,
                                double shearX, double shearY) {
        origTopLeftX = x;
        origTopLeftY = y;

        origTopRightX = x + width;
        origTopRightY = y;

        origBottomRightX = origTopRightX;
        origBottomRightY = y + height;

        origBottomLeftX = x;
        origBottomLeftY = origBottomRightY;

        double cx = x + width / 2.0;
        double cy = y + height / 2.0;

        AffineTransform transform = AffineTransform.getTranslateInstance(cx, cy);
        if (angle != 0) {
            transform.rotate(angle);
        }
        if (scaleX != 1.0 || scaleY != 1.0) {
            transform.scale(scaleX, scaleY);
        }
        if (shearX != 0 || shearY != 0) {
            // the opposite direction seems to be more intuitive
            transform.shear(-shearX, -shearY);
        }
        transform.translate(-cx, -cy);

        // Transform each corner point
        Point2D topLeft = new Point2D.Double(origTopLeftX, origTopLeftY);
        Point2D bottomLeft = new Point2D.Double(origBottomLeftX, origBottomLeftY);
        Point2D topRight = new Point2D.Double(origTopRightX, origTopRightY);
        Point2D bottomRight = new Point2D.Double(origBottomRightX, origBottomRightY);

        transform.transform(topLeft, topLeft);
        transform.transform(bottomLeft, bottomLeft);
        transform.transform(topRight, topRight);
        transform.transform(bottomRight, bottomRight);

        // Update transformed coordinates
        topLeftX = topLeft.getX();
        topLeftY = topLeft.getY();
        bottomLeftX = bottomLeft.getX();
        bottomLeftY = bottomLeft.getY();
        topRightX = topRight.getX();
        topRightY = topRight.getY();
        bottomRightX = bottomRight.getX();
        bottomRightY = bottomRight.getY();
    }

    public double getTopLeftX() {
        return topLeftX;
    }

    public double getTopLeftY() {
        return topLeftY;
    }

    /**
     * Returns the transformed rectangle as a Shape.
     */
    public Shape asShape() {
        if (cachedShape != null) {
            return cachedShape;
        }

        cachedShape = new Path2D.Double();
        cachedShape.moveTo(topLeftX, topLeftY);
        cachedShape.lineTo(topRightX, topRightY);
        cachedShape.lineTo(bottomRightX, bottomRightY);
        cachedShape.lineTo(bottomLeftX, bottomLeftY);
        cachedShape.closePath();

        return cachedShape;
    }

    /**
     * Returns the bounding box of the transformed rectangle.
     */
    public Rectangle getBoundingBox() {
        if (cachedBox != null) {
            return cachedBox;
        }
        double minX = min(min(topLeftX, topRightX), min(bottomRightX, bottomLeftX));
        double minY = min(min(topLeftY, topRightY), min(bottomRightY, bottomLeftY));
        double maxX = max(max(topLeftX, topRightX), max(bottomRightX, bottomLeftX));
        double maxY = max(max(topLeftY, topRightY), max(bottomRightY, bottomLeftY));

        int width = (int) Math.ceil(maxX - minX);
        int height = (int) Math.ceil(maxY - minY);
        cachedBox = new Rectangle((int) minX, (int) minY, width, height);
        return cachedBox;
    }

    /**
     * Aligns the transformed rectangle with the given layout rectangle.
     */
    public void align(Rectangle layout, Rectangle transformedBounds) {
        int dx = layout.x - transformedBounds.x;
        int dy = layout.y - transformedBounds.y;
        translate(dx, dy);
    }

    public void translate(double dx, double dy) {
        topLeftX += dx;
        topLeftY += dy;

        topRightX += dx;
        topRightY += dy;

        bottomRightX += dx;
        bottomRightY += dy;

        bottomLeftX += dx;
        bottomLeftY += dy;

        cachedShape = null;
        cachedBox = null;
    }

    /**
     * Paints the original and transformed corners (for debugging).
     */
    public void paintCorners(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillOval((int) topLeftX - 5, (int) topLeftY - 5, 10, 10);
        g.fillOval((int) origTopLeftX - 5, (int) origTopLeftY - 5, 10, 10);

        g.setColor(Color.BLUE);
        g.fillOval((int) topRightX - 5, (int) topRightY - 5, 10, 10);
        g.fillOval((int) origTopRightX - 5, (int) origTopRightY - 5, 10, 10);

        g.setColor(Color.YELLOW);
        g.fillOval((int) bottomRightX - 5, (int) bottomRightY - 5, 10, 10);
        g.fillOval((int) origBottomRightX - 5, (int) origBottomRightY - 5, 10, 10);

        g.setColor(new Color(0, 100, 0));
        g.fillOval((int) bottomLeftX - 5, (int) bottomLeftY - 5, 10, 10);
        g.fillOval((int) origBottomLeftX - 5, (int) origBottomLeftY - 5, 10, 10);
    }

    @Override
    public DebugNode createDebugNode(String key) {
        DebugNode node = new DebugNode(key, this);

        node.addDouble("origTopLeftX", origTopLeftX);
        node.addDouble("origTopLeftY", origTopLeftY);
        node.addDouble("topLeftX", topLeftX);
        node.addDouble("topLeftY", topLeftY);

        return node;
    }
}

