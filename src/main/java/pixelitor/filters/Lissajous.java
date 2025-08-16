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
import pixelitor.filters.gui.AngleParam;
import pixelitor.filters.gui.Help;
import pixelitor.filters.gui.RangeParam;

import java.awt.geom.Path2D;
import java.io.Serial;

/**
 * A shape filter rendering a Lissajous curve.
 */
public class Lissajous extends CurveFilter {
    @Serial
    private static final long serialVersionUID = 3307105281671382731L;

    public static final String NAME = "Lissajous Curve";
    private static final int NUM_STEPS = 5000;

    private final RangeParam a = new RangeParam("a", 1, 4, 41);
    private final RangeParam b = new RangeParam("b", 1, 5, 41);
    private final AngleParam delta = new AngleParam("delta", 0);
    private final RangeParam time = new RangeParam("Time", 0, 100, 100);

    public Lissajous() {
        addParamsToFront(
            a,
            b,
            delta,
            time
        );

        help = Help.fromWikiURL("https://en.wikipedia.org/wiki/Lissajous_curve");
    }

    @Override
    protected Path2D createCurve(int width, int height) {
        double aVal = a.getValueAsDouble();
        double bVal = b.getValueAsDouble();
        double deltaVal = delta.getValueInRadians();

        double cx = transform.getCx(width);
        double cy = transform.getCy(height);

        double w = width / 2.0;
        double h = height / 2.0;
        double dt = 2 * Math.PI / NUM_STEPS;

        double distThreshold = Math.sqrt(width * width + height * height) / 50;
        double angleThreshold = calcAngleThreshold(aVal, bVal);

        PathConnector connector = new PathConnector(NUM_STEPS + 1, distThreshold, angleThreshold);

        double maxT = Math.PI * time.getValueAsDouble() / 50.0;
        for (double t = 0; t < maxT; t += dt) {
            double x = w * FastMath.sin(aVal * t + deltaVal) + cx;
            double y = h * FastMath.sin(bVal * t) + cy;
            connector.add(x, y);
        }

        Path2D path = connector.getPath();
        if (time.isMaximum()) {
            path.closePath();
        }
        return path;
    }

    private static double calcAngleThreshold(double aVal, double bVal) {
        double angleThreshold;
        double maxRatio = Math.max(aVal / bVal, bVal / aVal);
        if (maxRatio > 10) {
            // sharp angles
            angleThreshold = 0.05;
        } else if (maxRatio > 5) {
            angleThreshold = 0.1;
        } else {
            angleThreshold = 0.2;
        }
        return angleThreshold;
    }
}