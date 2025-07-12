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
package pixelitor.filters.animation;

import pixelitor.filters.ParametrizedFilter;
import pixelitor.filters.gui.FilterState;
import pixelitor.gui.utils.Dialogs;

import java.awt.Component;
import java.io.File;

import static java.nio.file.Files.isWritable;
import static pixelitor.utils.Threads.callInfo;
import static pixelitor.utils.Threads.calledOnEDT;

/**
 * A tweening animation that interpolates filter parameters between two states.
 */
public class TweenAnimation {
    private ParametrizedFilter filter;
    private FilterState initialState;
    private FilterState finalState;

    private int numFrames;
    private int millisBetweenFrames;
    private TimeInterpolation interpolation;
    private boolean pingPong;

    private TweenOutputType outputType;
    private File outputLocation; // file or directory

    public TweenAnimation() {
    }

    public ParametrizedFilter getFilter() {
        return filter;
    }

    public void setFilter(ParametrizedFilter filter) {
        this.filter = filter;
    }

    public void captureInitialState() {
        initialState = filter.getParamSet().copyState(true);
    }

    public void captureFinalState() {
        finalState = filter.getParamSet().copyState(true);
    }

    public FilterState tween(double time) {
        double progress = interpolation.time2progress(time);
        return initialState.interpolate(finalState, progress);
    }

    public AnimationWriter createWriter() {
        return outputType.createWriter(outputLocation, millisBetweenFrames);
    }

    public void setInterpolation(TimeInterpolation interpolation) {
        this.interpolation = interpolation;
    }

    public void setMillisBetweenFrames(int millisBetweenFrames) {
        this.millisBetweenFrames = millisBetweenFrames;
    }

    public int getNumFrames() {
        return numFrames;
    }

    public void setNumFrames(int numFrames) {
        this.numFrames = numFrames;
    }

    public void setOutputLocation(File outputLocation) {
        this.outputLocation = outputLocation;
    }

    public void setOutputType(TweenOutputType outputType) {
        this.outputType = outputType;
    }

    public void setPingPong(boolean pingPong) {
        this.pingPong = pingPong;
    }

    public boolean isPingPong() {
        return pingPong;
    }

    /**
     * Warns the user about overwriting existing files and returns whether to proceed.
     */
    public boolean checkOverwrite(Component dialogParent) {
        assert calledOnEDT() : callInfo();

        if (outputType.needsDirectory()) {
            return checkOverwriteForDirectory(dialogParent);
        } else { // file
            return checkOverwriteForFile(dialogParent);
        }
    }

    private boolean checkOverwriteForDirectory(Component dialogParent) {
        assert outputLocation.isDirectory() : outputLocation.getAbsolutePath();

        String[] files = outputLocation.list();
        if (files == null || files.length == 0) {
            return true; // empty directory: OK
        } else {
            return showFolderNotEmptyDialog(dialogParent);
        }
    }

    private boolean checkOverwriteForFile(Component dialogParent) {
        if (outputLocation.exists()) {
            assert outputLocation.isFile() : outputLocation.getAbsolutePath();

            boolean overwrite = showFileExistsDialog(dialogParent);
            if (overwrite) {
                if (isWritable(outputLocation.toPath())) {
                    return true;
                } else {
                    Dialogs.showFileNotWritableDialog(outputLocation);
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return true; // the output file doesn't exist: OK
        }
    }

    private boolean showFolderNotEmptyDialog(Component dialogParent) {
        return Dialogs.showYesNoWarningDialog(dialogParent, "Folder Not Empty",
            String.format("<html>The folder <b>%s</b> is not empty. " +
                    "<br>Some files might be overwritten. Are you sure you want to continue?",
                outputLocation.getAbsolutePath()));
    }

    private boolean showFileExistsDialog(Component dialogParent) {
        return Dialogs.showYesNoWarningDialog(dialogParent, "File exists",
            outputLocation.getAbsolutePath() + " exists already. Overwrite?");
    }
}
