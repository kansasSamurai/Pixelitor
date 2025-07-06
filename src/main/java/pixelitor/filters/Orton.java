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

import com.jhlabs.composite.MultiplyComposite;
import com.jhlabs.image.BoxBlurFilter;
import pixelitor.filters.gui.RangeParam;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.StatusBarProgressTracker;
import pixelitor.utils.Texts;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Serial;

/**
 * Orton effect - based on http://pcin.net/update/2006/11/01/the-orton-effect-digital-photography-tip-of-the-week/
 */
public class Orton extends ParametrizedFilter {
    @Serial
    private static final long serialVersionUID = -3777096018326312847L;

    public static final String NAME = Texts.i18n("orton_effect");

    private final RangeParam blurRadius = new RangeParam("Blur Radius", 0, 3, 10);
    private final RangeParam amount = new RangeParam("Amount (%)", 0, 100, 100);

    public Orton() {
        super(true);

        initParams(
            blurRadius.withAdjustedRange(0.01),
            amount
        );

        helpURL = "https://en.wikipedia.org/wiki/Orton_(photography)";
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        float radius = blurRadius.getValueAsFloat();
        float opacity = (float) amount.getPercentage();

        int width = src.getWidth();
        int height = src.getHeight();

        // the blur takes approx the same time as the screen + multiply together
        int blurWorkUnits = 3 * (width + height);
        int totalWorkUnits = 2 * blurWorkUnits;

        var pt = new StatusBarProgressTracker(NAME, totalWorkUnits);

        dest = ImageUtils.copyImage(src);
        ImageUtils.screenWithItself(dest, opacity);
        BufferedImage blurredMultiplied = ImageUtils.copyImage(dest);

        pt.unitsDone(blurWorkUnits / 2);

        if (radius > 0) {
            if (width == 1 || height == 1) {
                // otherwise we get ArrayIndexOutOfBoundsException in BoxBlurFilter
                return src;
            }

            var boxBlur = new BoxBlurFilter(radius, radius, 3, NAME);
            boxBlur.setProgressTracker(pt);
            blurredMultiplied = boxBlur.filter(blurredMultiplied, blurredMultiplied);
        }

        Graphics2D g = dest.createGraphics();
        g.setComposite(new MultiplyComposite(opacity));
        g.drawImage(blurredMultiplied, 0, 0, null);
        g.dispose();

        pt.finished();

        return dest;
    }

    @Override
    public boolean supportsGray() {
        return false;
    }
}
