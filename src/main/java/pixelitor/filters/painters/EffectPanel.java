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

import com.bric.swing.ColorSwatch;
import org.jdesktop.swingx.painter.effects.AbstractAreaEffect;
import pixelitor.colors.ColorHistory;
import pixelitor.colors.Colors;
import pixelitor.filters.gui.ParamAdjustmentListener;
import pixelitor.filters.gui.RangeParam;
import pixelitor.filters.gui.ResetButton;
import pixelitor.filters.gui.Resettable;
import pixelitor.gui.utils.GUIUtils;
import pixelitor.gui.utils.GridBagHelper;
import pixelitor.gui.utils.SliderSpinner;
import pixelitor.utils.Rnd;

import javax.swing.*;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.util.Objects;

import static javax.swing.BorderFactory.createTitledBorder;
import static pixelitor.gui.GUIText.OPACITY;

/**
 * A base class for configuring individual area effects in the {@link EffectsPanel}.
 */
public abstract class EffectPanel extends JPanel implements Resettable {
    private final JCheckBox enabledCB;
    private ActionListener enabledCBListener;

    private final ColorSwatch colorSwatch;

    private final boolean defaultEnabled;
    private final Color defaultColor;
    private final int defaultOpacityPercent; // opacity as a 0..100 int value

    private Color color;
    static final int BUTTON_SIZE = 20;
    ParamAdjustmentListener adjustmentListener;

    private ResetButton resetButton;

    private final RangeParam opacityRange;

    protected final GridBagHelper gbh;

    EffectPanel(String effectName, boolean defaultEnabled,
                Color defaultColor, float defaultOpacity) {
        this.defaultEnabled = defaultEnabled;
        this.defaultColor = defaultColor;
        this.defaultOpacityPercent = (int) (100 * defaultOpacity);

        setBorder(createTitledBorder('"' + effectName + "\" Configuration"));

        opacityRange = new RangeParam(OPACITY, 1, defaultOpacityPercent, 100);
        var opacitySlider = SliderSpinner.from(opacityRange);

        enabledCB = new JCheckBox();
        enabledCB.setName("enabledCB");
        setEffectEnabled(defaultEnabled);
        enabledCB.addActionListener(e -> updateResetButtonIcon());

        colorSwatch = new ColorSwatch(defaultColor, BUTTON_SIZE);
        color = defaultColor;

        GUIUtils.addClickAction(colorSwatch, this::showColorDialog);

        Colors.setupFilterColorsPopupMenu(this, colorSwatch,
            this::getColor, c -> setColor(c, true));

        setLayout(new GridBagLayout());

        gbh = new GridBagHelper(this);
        gbh.addLabelAndControl("Enabled:", enabledCB);
        gbh.addLabelAndControlNoStretch("Color:", colorSwatch);
        gbh.addLabelAndControl(opacityRange.getName() + ":", opacitySlider);
    }

    private void showColorDialog() {
        Colors.selectColorWithDialog(this,
            "Select Color", color, true,
            c -> setColor(c, true));
    }

    public void setColor(Color newColor, boolean trigger) {
        if (color.equals(newColor)) {
            return;
        }
        color = newColor;
        colorSwatch.setForeground(color);
        colorSwatch.paintImmediately(0, 0, BUTTON_SIZE, BUTTON_SIZE);

        updateResetButtonIcon();

        if (trigger && adjustmentListener != null) {
            adjustmentListener.paramAdjusted();
        }
        ColorHistory.remember(newColor);
    }

    public void setEffectEnabled(boolean enabled) {
        enabledCB.setSelected(enabled);
    }

    ButtonModel getEnabledModel() {
        return enabledCB.getModel();
    }

    public boolean isEffectEnabled() {
        return enabledCB.isSelected();
    }

    public Color getColor() {
        return color;
    }

    public float getOpacity() {
        return (float) opacityRange.getPercentage();
    }

    public void setOpacity(float opacity) {
        opacityRange.setValueNoTrigger(100 * opacity);
    }

    private void setOpacityAsPercent(int opacity) {
        opacityRange.setValueNoTrigger(opacity);
    }

    public abstract double getEffectWidth();

    public abstract void setEffectWidth(double value);

    public void setAdjustmentListener(ParamAdjustmentListener listener) {
        this.adjustmentListener = listener;

        if (enabledCBListener != null) {
            // avoid accumulating action listeners on the checkbox
            enabledCB.removeActionListener(enabledCBListener);
        }
        enabledCBListener = e -> listener.paramAdjusted();
        enabledCB.addActionListener(enabledCBListener);

        opacityRange.setAdjustmentListener(listener);
    }

    public void updateEffectColorAndWidth(AbstractAreaEffect effect) {
        effect.setBrushColor(getColor());
        effect.setEffectWidth(getEffectWidth());
        effect.setAutoBrushSteps();
    }

    @Override
    public boolean isAtDefault() {
        return enabledCB.isSelected() == defaultEnabled
            && Objects.equals(color, defaultColor)
            && opacityRange.isAtDefault();
    }

    @Override
    public void reset(boolean trigger) {
        setEffectEnabled(defaultEnabled);
        setColor(defaultColor, trigger);
        setOpacityAsPercent(defaultOpacityPercent);
    }

    public boolean randomize() {
        // each effect is enabled with 25% probability
        boolean enable = Rnd.nextFloat() < 0.25f;
        setEffectEnabled(enable);
        if (enable) {
            setColor(Rnd.createRandomColor(), false);
            opacityRange.randomize();
        }
        return enable;
    }

    public void setResetButton(ResetButton resetButton) {
        this.resetButton = resetButton;
    }

    protected void updateResetButtonIcon() {
        if (resetButton != null) {
            resetButton.updateState();
        }
    }

    @Override
    public String getResetToolTip() {
        return "Reset the default effect settings";
    }
}
