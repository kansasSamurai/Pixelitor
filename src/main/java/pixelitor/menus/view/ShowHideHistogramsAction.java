/*
 * Copyright 2019 Laszlo Balazs-Csiki and Contributors
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
package pixelitor.menus.view;

import pixelitor.gui.HistogramsPanel;
import pixelitor.utils.AppPreferences;

/**
 * The action that either shows or hides the histogram,
 * depending on the current visibility
 */
public class ShowHideHistogramsAction extends ShowHideAction {
    public static final ShowHideAction INSTANCE = new ShowHideHistogramsAction();

    private ShowHideHistogramsAction() {
        super("Show Histograms", "Hide Histograms");
    }

    @Override
    public boolean getCurrentVisibility() {
        return HistogramsPanel.INSTANCE.isShown();
    }

    @Override
    public boolean getStartupVisibility() {
        return AppPreferences.WorkSpace.getHistogramsVisibility();
    }

    @Override
    public void setVisibility(boolean value) {
        AppPreferences.WorkSpace.setHistogramsVisibility(value, true);
    }
}