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

import pd.CannyEdgeDetector;
import pixelitor.filters.gui.BooleanParam;
import pixelitor.filters.gui.Help;
import pixelitor.filters.gui.RangeParam;
import pixelitor.utils.MemoryInfo;
import pixelitor.utils.Messages;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Serial;

import static pixelitor.utils.MemoryInfo.NUM_BYTES_IN_MEGABYTE;

/**
 * Canny edge detector - see http://en.wikipedia.org/wiki/Canny_edge_detector
 * based on CannyEdgeDetector by Tom Gibara - http://www.tomgibara.com/computer-vision/canny-edge-detector
 */
public class Canny extends ParametrizedFilter {
    public static final String NAME = "Canny";

    @Serial
    private static final long serialVersionUID = -9029134855407310420L;

    private final RangeParam lowThreshold = new RangeParam(
        "Low Threshold", 1, 250, 961);
    private final RangeParam highThreshold = new RangeParam(
        "High Threshold", 1, 750, 961);
    private final RangeParam gaussianKernelWidth = new RangeParam(
        "Gaussian Kernel Width", 2, 16, 50);
    private final RangeParam gaussianKernelRadius = new RangeParam(
        "Gaussian Kernel Radius", 0, 2, 10);
    private final BooleanParam contrastNormalized = new BooleanParam("Contrast Normalized");
    private final BooleanParam invert = new BooleanParam("Invert");

    public Canny() {
        super(true);

        initParams(
            lowThreshold,
            highThreshold,
            gaussianKernelWidth,
            gaussianKernelRadius.withDecimalPlaces(1),
            contrastNormalized,
            invert
        );
        highThreshold.ensureHigherValueThan(lowThreshold);

        help = Help.fromWikiURL("https://en.wikipedia.org/wiki/Canny_edge_detector");
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        if (gaussianKernelRadius.getValueAsFloat() <= 0.1f) {
            // return a black image to avoid exceptions
            Graphics2D g = dest.createGraphics();
            g.setColor(invert.isChecked() ? Color.WHITE : Color.BLACK);
            g.fillRect(0, 0, dest.getWidth(), dest.getHeight());
            g.dispose();
            return dest;
        }

        long estimatedMemoryMB = estimateNeededMemoryMB(src);
        System.gc(); // needed for the memory estimation
        var memoryInfo = new MemoryInfo();
        long availableMemoryMB = memoryInfo.getAvailableMemoryMB();

        if (estimatedMemoryMB > availableMemoryMB) {
            showNotEnoughMemoryDialog(estimatedMemoryMB, availableMemoryMB);
            dest = src;
            return dest;
        }

        // do not cache this object because it holds a lot of memory!
        var detector = createEdgeDetector(src);
        dest = detector.getEdgesImage();

        if (invert.isChecked()) {
            Invert.quickInvert(dest);
        }

        return dest;
    }

    private CannyEdgeDetector createEdgeDetector(BufferedImage src) {
        var detector = new CannyEdgeDetector();

        detector.setLowThreshold((float) lowThreshold.getPercentage());
        detector.setHighThreshold((float) highThreshold.getPercentage());
        detector.setContrastNormalized(contrastNormalized.isChecked());
        detector.setGaussianKernelRadius(gaussianKernelRadius.getValueAsFloat());
        detector.setGaussianKernelWidth(gaussianKernelWidth.getValue());

        detector.setSourceImage(src);
        detector.process();
        return detector;
    }

    private static void showNotEnoughMemoryDialog(long estimatedMemoryMB,
                                                  long availableMemoryMB) {
        String msg = "This image is too large for the Canny edge detection algorithm.\n" +
            "Press Cancel in the following dialog and try with smaller images.\n" +
            "Available memory is " + availableMemoryMB +
            " megabytes, memory needed for this image is " + estimatedMemoryMB
            + " megabytes.";
        Messages.showInfo("Not Enough Memory", msg);
    }

    private static long estimateNeededMemoryMB(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        long numPixels = (long) width * height;
        // 6 arrays with 4-byte data type
        long estimatedMemoryMB = 6 * numPixels * 4 / NUM_BYTES_IN_MEGABYTE;
        // 1.8 was found experimentally, this is still needed to prevent OutOfMemory errors
        estimatedMemoryMB = (long) (estimatedMemoryMB * 1.8);
        return estimatedMemoryMB;
    }

    @Override
    public boolean supportsGray() {
        return false;
    }
}