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

import com.jhlabs.image.ImageMath;
import pixelitor.filters.gui.IntChoiceParam;
import pixelitor.filters.gui.IntChoiceParam.Item;
import pixelitor.filters.gui.RangeParam;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.ProgressTracker;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.SplittableRandom;

import static pixelitor.gui.GUIText.OPACITY;
import static pixelitor.utils.ImageUtils.isGrayscale;

/**
 * Add Noise filter
 */
public class AddNoise extends ParametrizedFilter {
    public static final String NAME = "Add Noise";

    @Serial
    private static final long serialVersionUID = 3647024991920186929L;

    private static final int METHOD_FASTER = 1;
    private static final int METHOD_COVERAGE_ANIM = 2;

    private final RangeParam opacityParam = new RangeParam(OPACITY, 0, 100, 100);
    private final RangeParam coverageParam = new RangeParam("Coverage (%)", 0, 50, 100);
    private final RangeParam saturationParam = new RangeParam("Saturation (%)", 0, 100, 100);
    private final IntChoiceParam method = new IntChoiceParam("Method", new Item[]{
        new Item("Faster", METHOD_FASTER),
        new Item("Smooth Coverage Animation", METHOD_COVERAGE_ANIM),
    });

    private final float[] tmpHSV = new float[3];

    public AddNoise() {
        super(true);

        opacityParam.setPresetKey("Opacity (%)");

        initParams(
            coverageParam,
            saturationParam,
            opacityParam,
            method
        ).withReseedAction();
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        SplittableRandom rand = paramSet.getLastSeedSRandom();

        if (isGrayscale(src)) {
            return addNoiseToGray(src, dest, rand);
        }

        boolean coverageAnim = method.getValue() == METHOD_COVERAGE_ANIM;
        return addNoiseToRGB(src, dest, coverageAnim, rand);
    }

    private BufferedImage addNoiseToRGB(BufferedImage src, BufferedImage dest,
                                        boolean coverageAnim, SplittableRandom rand) {
        int[] srcPixels = ImageUtils.getPixels(src);
        int[] destPixels = ImageUtils.getPixels(dest);
        int numPixels = destPixels.length;

        boolean fullSaturation = saturationParam.getValue() == 100;
        boolean fullOpacity = opacityParam.getValue() == 100;

        float opacity = (float) opacityParam.getPercentage();
        float saturation = (float) saturationParam.getPercentage();
        double coverage = coverageParam.getPercentage();

        int workUnit = 100_000;
        int counter = 0;
        int numWorkUnits = numPixels / workUnit;
        var pt = ProgressTracker.createSafeTracker(numWorkUnits);

        for (int i = 0; i < numPixels; i++) {
            // count at the beginning of the loop because of early returns
            counter++;
            if (counter == workUnit) {
                counter = 0;
                pt.unitDone();
            }

            int randomInt = 0;
            if (coverageAnim) {
                // generate random numbers for the discarded pixels as well
                randomInt = rand.nextInt();
            }

            int srcRGB = srcPixels[i];
            if (rand.nextDouble() > coverage) {
                destPixels[i] = srcRGB;
                continue;
            }

            int sourceAlpha = 0xFF_00_00_00 & srcRGB;
            if (sourceAlpha == 0) {
                destPixels[i] = 0;
                continue;
            }

            if (!coverageAnim) {
                // If coverage animation isn't a requirement, then it is faster
                // to generate the random values only here, for the covered pixels.
                randomInt = rand.nextInt();
            }

            if (fullSaturation) {
                // make the alpha channel the same as for the source
                randomInt |= sourceAlpha;

                if (fullOpacity) {
                    // if we have full saturation (the default), then we can
                    // just use the random pixel as it is - if the opacity is also 100.
                    destPixels[i] = randomInt;
                } else {
                    // ...or mix the random pixel with the source according to
                    // the opacity
                    destPixels[i] = ImageMath.mixColors(opacity, srcRGB, randomInt);
                }
            } else { // desaturate the random pixel
                int r = (randomInt >>> 16) & 0xFF;
                int g = (randomInt >>> 8) & 0xFF;
                int b = randomInt & 0xFF;

                Color.RGBtoHSB(r, g, b, tmpHSV);
                float newSaturation = ImageMath.lerp(saturation, 0.0f, tmpHSV[1]);
                randomInt = Color.HSBtoRGB(tmpHSV[0], newSaturation, tmpHSV[2]);

                // make the alpha channel the same as for the source
                randomInt |= sourceAlpha;

                if (fullOpacity) {
                    destPixels[i] = randomInt;
                } else {
                    destPixels[i] = ImageMath.mixColors(opacity, srcRGB, randomInt);
                }
            }
        }
        pt.finished();

        return dest;
    }

    private BufferedImage addNoiseToGray(BufferedImage src, BufferedImage dest,
                                         SplittableRandom rand) {
        byte[] srcPixels = ImageUtils.getGrayPixels(src);
        byte[] destPixels = ImageUtils.getGrayPixels(dest);

        // fill the dest with random values
        rand.nextBytes(destPixels);

        boolean fullOpacity = opacityParam.getValue() == 100;
        boolean fullCoverage = coverageParam.getValue() == 100;
        double coveragePercentage = coverageParam.getPercentage();

        if (fullOpacity && fullCoverage) {
            return dest;
        }

        int length = destPixels.length;
        int workUnit = 100_000;
        int counter = 0;
        int numWorkUnits = length / workUnit;
        var pt = ProgressTracker.createSafeTracker(numWorkUnits);

        double destWeight = opacityParam.getPercentage();
        double srcWeight = 1.0 - destWeight;
        for (int i = 0; i < length; i++) {
            // count at the beginning of the loop because of early returns
            counter++;
            if (counter == workUnit) {
                counter = 0;
                pt.unitDone();
            }

            byte srcPixel = srcPixels[i];
            if (rand.nextDouble() > coveragePercentage) {
                destPixels[i] = srcPixel;
                continue;
            }

            int d = Byte.toUnsignedInt(destPixels[i]);
            int s = Byte.toUnsignedInt(srcPixel);
            int v = (int) (d * destWeight + s * srcWeight);

            destPixels[i] = (byte) v;
        }
        pt.finished();

        return dest;
    }
}
