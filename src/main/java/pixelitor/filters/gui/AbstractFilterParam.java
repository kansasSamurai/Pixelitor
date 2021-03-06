/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
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

package pixelitor.filters.gui;

import java.util.Objects;

import static pixelitor.filters.gui.RandomizePolicy.IGNORE_RANDOMIZE;

/**
 * An abstract parent class for {@link FilterParam} implementations.
 */
public abstract class AbstractFilterParam implements FilterParam {
    private final String name;
    protected ParamAdjustmentListener adjustmentListener;
    private boolean enabledByAnimationSetting = true;
    private boolean enabledByFilterLogic = true;
    protected ParamGUI paramGUI;
    protected RandomizePolicy randomizePolicy;

    // an extra action button to the right of the normal GUI,
    // typically some randomization, which will be enabled
    // only for certain values of this param
    protected FilterAction action;

    AbstractFilterParam(String name, RandomizePolicy randomizePolicy) {
        this.name = Objects.requireNonNull(name);
        this.randomizePolicy = randomizePolicy;
    }

    @Override
    public void setAdjustmentListener(ParamAdjustmentListener listener) {
        this.adjustmentListener = listener;
        if (action != null) {
            action.setAdjustmentListener(listener);
        }
    }

    public FilterParam withAction(FilterAction action) {
        this.action = action;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setEnabled(boolean b, EnabledReason reason) {
        switch (reason) {
            case APP_LOGIC:
                enabledByFilterLogic = b;
                break;
            case FINAL_ANIMATION_SETTING:
                if (canBeAnimated()) {
                    // ignore - the whole point of the final animation setting mode
                    // is to disable/enable the filter params that cannot be animated
                    return;
                }
                enabledByAnimationSetting = b;
                break;
        }

        setEnabled(shouldBeEnabled());
    }

    protected void setParamGUIEnabledState() {
        boolean b = shouldBeEnabled();
        paramGUI.setEnabled(b);
    }

    private boolean shouldBeEnabled() {
        return enabledByFilterLogic && enabledByAnimationSetting;
    }

    void setEnabled(boolean b) {
        if (paramGUI != null) {
            paramGUI.setEnabled(b);
        }
    }

    @Override
    public boolean ignoresRandomize() {
        return randomizePolicy == IGNORE_RANDOMIZE;
    }

    @Override
    public void setToolTip(String tip) {
        if(paramGUI != null) {
            paramGUI.setToolTip(tip);
        }
    }

    @Override
    public String getResetToolTip() {
        return "<html>Reset the value of <b>" + name + "</b>";
    }
}
