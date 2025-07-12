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

package pixelitor.compactions;

import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.CopyType;
import pixelitor.gui.View;
import pixelitor.guides.Guides;
import pixelitor.history.CompositionReplacedEdit;
import pixelitor.history.History;
import pixelitor.selection.SelectionActions;
import pixelitor.utils.Messages;
import pixelitor.utils.ProgressHandler;
import pixelitor.utils.Utils;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static pixelitor.utils.Threads.callInfo;
import static pixelitor.utils.Threads.calledOnEDT;
import static pixelitor.utils.Threads.onEDT;
import static pixelitor.utils.Threads.onPool;

/**
 * Resizes all layers of a {@link Composition} to the given dimensions.
 * It can either stretch the content to exactly match the target
 * dimensions or maintain the aspect ratio while fitting within them.
 */
public class Resize implements CompAction {
    private final int targetWidth;
    private final int targetHeight;
    private final boolean preserveAspectRatio;

    public Resize(int targetWidth, int targetHeight) {
        this(targetWidth, targetHeight, false);
    }

    public Resize(int targetWidth, int targetHeight, boolean preserveAspectRatio) {
        assert targetWidth > 0 : "targetWidth = " + targetWidth;
        assert targetHeight > 0 : "targetHeight = " + targetHeight;

        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.preserveAspectRatio = preserveAspectRatio;
    }

    @Override
    public CompletableFuture<Composition> process(Composition srcComp) {
        Canvas srcCanvas = srcComp.getCanvas();
        if (srcCanvas.hasImSize(targetWidth, targetHeight)) {
            // no resize needed
            return CompletableFuture.completedFuture(srcComp);
        }

        var targetSize = calcTargetSize(srcCanvas);

        // The resizing runs outside the EDT to allow the progress bar animation
        // to update, and to enable the parallel resizing of multiple layers.
        var progressHandler = Messages.startProgress("Resizing", -1);
        return CompletableFuture
            .supplyAsync(() -> srcComp.copy(CopyType.UNDO, true), onPool)
            .thenCompose(newComp -> resizeLayersInParallel(newComp, targetSize))
            .thenApplyAsync(newComp -> afterResizeActions(srcComp, newComp, targetSize, progressHandler), onEDT)
            .handle((newComp, ex) -> {
                if (ex != null) {
                    Messages.showExceptionOnEDT(ex);
                }
                return newComp;
            });
    }

    private Dimension calcTargetSize(Canvas srcCanvas) {
        if (!preserveAspectRatio) {
            return new Dimension(targetWidth, targetHeight);
        }

        int srcWidth = srcCanvas.getWidth();
        int srcHeight = srcCanvas.getHeight();

        double heightScale = targetHeight / (double) srcHeight;
        double widthScale = targetWidth / (double) srcWidth;
        double scale = Math.min(heightScale, widthScale);

        return new Dimension(
            (int) (scale * srcWidth),
            (int) (scale * srcHeight)
        );
    }

    private static Composition afterResizeActions(Composition srcComp,
                                                  Composition newComp,
                                                  Dimension newCanvasSize,
                                                  ProgressHandler progressHandler) {
        assert calledOnEDT() : callInfo();

        View view = srcComp.getView();
        assert view != null;

        Canvas newCanvas = newComp.getCanvas();
        var canvasTransform = newCanvas.createImTransformToFit(newCanvasSize);
        newComp.imCoordsChanged(canvasTransform, false, view);
        newCanvas.resize(newCanvasSize.width, newCanvasSize.height, view, false);

        History.add(new CompositionReplacedEdit("Resize",
            view, srcComp, newComp, canvasTransform, false));
        view.replaceComp(newComp);

        // The view was active when the resizing started, but since the
        // resizing was asynchronous, this could have changed.
        if (view.isActive()) {
            SelectionActions.update(newComp);
        }

        Guides srcGuides = srcComp.getGuides();
        if (srcGuides != null) {
            // the guides don't need transforming,
            // just a correct canvas size
            Guides newGuides = srcGuides.copyIdentical(view);
            newComp.setGuides(newGuides);
        }

        // Only after the shared canvas size was updated.
        // The icon image could change if the proportions were
        // changed or if it was resized to a very small size
        newComp.updateAllIconImages();

        newComp.update(false, true);
        view.revalidate(); // make sure the scrollbars are OK

        progressHandler.stopProgress();
        Messages.showStatusMessage(format("<b>%s</b> was resized to %dx%d pixels.",
            newComp.getName(), newCanvasSize.width, newCanvasSize.height));

        return newComp;
    }

    private static CompletableFuture<Composition> resizeLayersInParallel(Composition comp, Dimension newSize) {
        // This could be called on the EDT or on another thread. The layers
        // themselves are resized in parallel using the thread pool's threads.
        List<CompletableFuture<Void>> layerResizeFutures = new ArrayList<>();
        comp.forEachNestedLayerAndMask(layer ->
            layerResizeFutures.add(layer.resize(newSize)));
        return Utils.allOf(layerResizeFutures).thenApply(v -> comp);
    }
}
