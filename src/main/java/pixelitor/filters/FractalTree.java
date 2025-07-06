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

package pixelitor.filters;

import net.jafama.FastMath;
import pixelitor.filters.gui.GradientParam;
import pixelitor.filters.gui.GroupedRangeParam;
import pixelitor.filters.gui.IntChoiceParam;
import pixelitor.filters.gui.IntChoiceParam.Item;
import pixelitor.filters.gui.RangeParam;
import pixelitor.utils.ProgressTracker;
import pixelitor.utils.StatusBarProgressTracker;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static java.awt.BasicStroke.CAP_ROUND;
import static java.awt.BasicStroke.JOIN_ROUND;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static pixelitor.filters.gui.RandomizeMode.IGNORE_RANDOMIZE;
import static pixelitor.gui.GUIText.ZOOM;

/**
 * Renders a fractal tree.
 */
public class FractalTree extends ParametrizedFilter {
    public static final String NAME = "Fractal Tree";

    @Serial
    private static final long serialVersionUID = 341738912127760736L;

    private static final Color BROWN = new Color(140, 100, 73);
    private static final Color GREEN = new Color(31, 125, 42);
    private static final int QUALITY_BETTER = 1;
    private static final int QUALITY_FASTER = 2;

    private final RangeParam iterations = new RangeParam("Age (Iterations)", 1, 10, 17);
    private final RangeParam angle = new RangeParam("Angle", 1, 20, 45);
    private final RangeParam randomnessParam = new RangeParam("Randomness", 0, 40, 100);
    private final GroupedRangeParam width = new GroupedRangeParam("Width",
        new RangeParam[]{
            new RangeParam("Overall", 50, 100, 300),
            new RangeParam("Trunk", 50, 200, 500),
        },
        false);

    private final RangeParam zoom = new RangeParam(ZOOM, 1, 100, 301);
    private final RangeParam curvedness = new RangeParam("Curvedness", 0, 10, 48);
    private final GroupedRangeParam physics = new GroupedRangeParam("Physics",
        "Gravity", "Wind", -100, 0, 100, false);
    private final IntChoiceParam quality = new IntChoiceParam("Quality", new Item[]{
        new Item("Better", QUALITY_BETTER),
        new Item("Faster", QUALITY_FASTER)
    }, IGNORE_RANDOMIZE);

    // precalculated objects for different depths of the tree
    private Stroke[] strokeLookup;
    private Color[] colorLookup;
    private Physics[] physicsLookup;

    private boolean doPhysics;
    private boolean leftFirst;
    private boolean hasRandomness;

    private final GradientParam colors = new GradientParam("Colors",
        new float[]{0.25f, 0.75f},
        new Color[]{BROWN, GREEN}, IGNORE_RANDOMIZE);
    private double defaultLength;
    private double lengthDeviation;
    private double angleDeviation;

    private ProgressTracker pt;

    public FractalTree() {
        super(false);

        zoom.setPresetKey("Zoom");

        initParams(
            iterations,
            zoom,
            randomnessParam,
            curvedness,
            angle,
            physics.notLinkable(),
            width.notLinkable().withAdjustedRange(0.5),
            colors,
            quality
        ).withReseedAction();
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        SplittableRandom rand = paramSet.getLastSeedSRandom();

        leftFirst = true;

        defaultLength = src.getHeight() * zoom.getPercentage() / 100.0;
        double randPercent = randomnessParam.getValue() / 100.0;
        hasRandomness = randomnessParam.getValue() > 0;
        lengthDeviation = defaultLength * randPercent;
        angleDeviation = 10.0 * randPercent;

        Graphics2D g = dest.createGraphics();
        if (quality.getValue() == QUALITY_BETTER) {
            g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        }

        int maxDepth = iterations.getValue();
        strokeLookup = new Stroke[maxDepth + 1];
        colorLookup = new Color[maxDepth + 1];

        int gravity = physics.getValue(0);
        int wind = physics.getValue(1);
        if (gravity != 0 || wind != 0) {
            doPhysics = true;
            physicsLookup = new Physics[maxDepth + 1];
        } else {
            doPhysics = false;
            physicsLookup = null;
        }

        for (int depth = 1; depth <= maxDepth; depth++) {
            double w1 = depth * width.getPercentage(0);
            double trunkWidth = width.getPercentage(1);
            double base = Math.pow(trunkWidth, 1.0 / (maxDepth - 1));
            double w2 = Math.pow(base, depth - 1);
            float strokeWidth = (float) (w1 * w2);
            float zoomedStrokeWidth = (strokeWidth * zoom.getValue()) / (float) zoom.getDefaultValue();
            strokeLookup[depth] = new BasicStroke(zoomedStrokeWidth, CAP_ROUND, JOIN_ROUND);

            // colors
            float where = ((float) depth) / iterations.getValue();
            int rgb = colors.getColorMap().getColor(1.0f - where);
            colorLookup[depth] = new Color(rgb);

            if (doPhysics) {
                physicsLookup[depth] = new Physics(gravity, wind, strokeWidth);
            }
        }

        double c = curvedness.getPercentage();
        if (rand.nextBoolean()) {
            c = -c;
        }

        int drawTreeCalls = 2;
        for (int i = 1; i < maxDepth; i++) {
            drawTreeCalls *= 2;
        }
        drawTreeCalls--;
        pt = new StatusBarProgressTracker(NAME, drawTreeCalls);

        drawTree(g, src.getWidth() / 2.0, src.getHeight(),
            270 + genAngleRandomness(rand), maxDepth, rand, c);

        g.dispose();
        pt.finished();

        return dest;
    }

    private void drawTree(Graphics2D g, double startX, double startY,
                          double angle, int depth,
                          RandomGenerator rand, double curvature) {
        if (depth == 0) {
            return;
        }

        // alternate the direction of curvature in each iteration
        curvature = -curvature;

        if (doPhysics) {
            angle = adjustPhysics(angle, depth);
        }

        double angleRad = Math.toRadians(angle);
        double endX = startX + FastMath.cos(angleRad) * depth * genRandomLength(rand);
        double endY = startY + FastMath.sin(angleRad) * depth * genRandomLength(rand);

        g.setStroke(strokeLookup[depth]);
        int nextDepth = depth - 1;
        if (quality.getValue() == QUALITY_BETTER) {
            if (depth == 1) {
                g.setColor(colorLookup[depth]);
            } else {
                g.setPaint(new GradientPaint(
                    (float) startX, (float) startY, colorLookup[depth],
                    (float) endX, (float) endY, colorLookup[nextDepth]));
            }
        } else {
            g.setColor(colorLookup[depth]);
        }

        connectPoints(g, curvature, startX, startY, endX, endY);

        int split = this.angle.getValue();

        double leftBranchAngle = angle - split + genAngleRandomness(rand);
        double rightBranchAngle = angle + split + genAngleRandomness(rand);

        pt.unitDone();

        leftFirst = !leftFirst;
        if (leftFirst) {
            drawTree(g, endX, endY, leftBranchAngle, nextDepth, rand, curvature);
            drawTree(g, endX, endY, rightBranchAngle, nextDepth, rand, curvature);
        } else {
            drawTree(g, endX, endY, rightBranchAngle, nextDepth, rand, curvature);
            drawTree(g, endX, endY, leftBranchAngle, nextDepth, rand, curvature);
        }
    }

    private double adjustPhysics(double angle, int depth) {
        assert doPhysics;

        // ensure the angle is within the range 0-360
        angle += 720;
        angle = angle % 360;

        Physics p = physicsLookup[depth];

        if (angle < 90) {
            angle += (90 - angle) * p.gravityStrength;
            angle -= (angle / 90.0) * p.windStrength;
        } else if (angle < 180) {
            angle -= (angle - 90) * p.gravityStrength;
            angle -= (180 - angle) * p.windStrength;
        } else if (angle < 270) {
            angle -= (270 - angle) * p.gravityStrength;
            angle += (angle - 180) * p.windStrength;
        } else if (angle <= 360) {
            angle += (angle - 270) * p.gravityStrength;
            angle += (360 - angle) * p.windStrength;
        } else {
            throw new IllegalStateException("angle = " + angle);
        }

        return angle;
    }

    private static void connectPoints(Graphics2D g, double curvature,
                                      double startX, double startY,
                                      double endX, double endY) {
        if (curvature == 0) {
            g.draw(new Line2D.Double(startX, startY, endX, endY));
        } else {
            Path2D path = new Path2D.Double();
            path.moveTo(startX, startY);

            double dx = endX - startX;
            double dy = endY - startY;

            double midX = startX + dx / 2.0;
            double midY = startY + dy / 2.0;

            // The normal vector is (-dy, dx).
            double ctrlX = midX - dy * curvature;
            double ctrlY = midY + dx * curvature;

            path.quadTo(ctrlX, ctrlY, endX, endY);
            g.draw(path);
        }
    }

    private double genAngleRandomness(RandomGenerator rand) {
        if (!hasRandomness) {
            return 0;
        }

        return -angleDeviation + rand.nextDouble() * 2 * angleDeviation;
    }

    private double genRandomLength(RandomGenerator rand) {
        if (!hasRandomness) {
            return defaultLength;
        }

        double minLength = defaultLength - lengthDeviation;

        return (minLength + 2 * lengthDeviation * rand.nextDouble());
    }

    private static class Physics {
        public final double gravityStrength;
        public final double windStrength;

        private Physics(int gravity, int wind, float strokeWidth2) {
            double effectStrength = 0.02 / strokeWidth2;

            gravityStrength = effectStrength * gravity;
            windStrength = effectStrength * wind;
        }
    }
}