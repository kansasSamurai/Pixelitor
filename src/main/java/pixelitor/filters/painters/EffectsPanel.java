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

import org.jdesktop.swingx.painter.effects.GlowPathEffect;
import org.jdesktop.swingx.painter.effects.InnerGlowPathEffect;
import org.jdesktop.swingx.painter.effects.NeonBorderEffect;
import org.jdesktop.swingx.painter.effects.ShadowPathEffect;
import pixelitor.filters.gui.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.geom.Point2D;

import static java.awt.BorderLayout.CENTER;
import static java.awt.Color.BLACK;
import static java.awt.Color.GREEN;
import static java.awt.Color.RED;
import static java.awt.Color.WHITE;
import static java.awt.FlowLayout.LEFT;

/**
 * A GUI panel for configuring the effects in an {@link AreaEffects}.
 * This class acts as the {@link ParamGUI} for an {@link EffectsParam}.
 */
public class EffectsPanel extends JPanel implements Resettable, ParamGUI {
    // padded with spaces for consistent tab width
    public static final String GLOW_TAB_NAME = "Glow               ";
    public static final String INNER_GLOW_TAB_NAME = "Inner Glow     ";
    public static final String NEON_BORDER_TAB_NAME = "Neon Border ";
    public static final String DROP_SHADOW_TAB_NAME = "Drop Shadow";

    private EffectPanel glowPanel;
    private EffectPanel innerGlowPanel;
    private NeonBorderPanel neonBorderPanel;
    private DropShadowPanel dropShadowPanel;
    private final EffectPanel[] panels = new EffectPanel[4];

    private final JTabbedPane tabs;

    public EffectsPanel(AreaEffects initialEffects) {
        setLayout(new BorderLayout());

        setEffects(initialEffects);
        panels[0] = glowPanel;
        panels[1] = innerGlowPanel;
        panels[2] = neonBorderPanel;
        panels[3] = dropShadowPanel;

        tabs = new JTabbedPane();
        tabs.setTabPlacement(SwingConstants.LEFT);
        tabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);

        addTab(GLOW_TAB_NAME, glowPanel);
        addTab(INNER_GLOW_TAB_NAME, innerGlowPanel);
        addTab(NEON_BORDER_TAB_NAME, neonBorderPanel);
        addTab(DROP_SHADOW_TAB_NAME, dropShadowPanel);

        selectFirstEnabledTab();

        add(tabs, CENTER);
    }

    /**
     * Sets the listener to be notified of parameter adjustments.
     */
    public void setAdjustmentListener(ParamAdjustmentListener listener) {
        assert listener != null;
        for (EffectPanel panel : panels) {
            panel.setAdjustmentListener(listener);
        }
    }

    private void selectFirstEnabledTab() {
        for (int i = 0; i < panels.length; i++) {
            EffectPanel panel = panels[i];
            if (panel.isEffectEnabled()) {
                tabs.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Updates the panel's controls to reflect the given effects configuration.
     */
    public void setEffects(AreaEffects effects) {
        initGlowPanel(effects);
        initInnerGlowPanel(effects);
        initNeonBorderPanel(effects);
        initDropShadowPanel(effects);

        if (tabs != null) {
            selectFirstEnabledTab();
        }
    }

    private void initGlowPanel(AreaEffects effects) {
        var effect = (effects == null) ? null : effects.getGlow();
        boolean enabled = (effect != null);
        Color color = enabled ? effect.getBrushColor() : WHITE;
        double width = enabled ? effect.getEffectWidth() : 10;
        float opacity = enabled ? effect.getOpacity() : 1.0f;
        
        if (glowPanel == null) { // first initialization
            glowPanel = new EffectWithWidthPanel(
                "Glow", enabled, color, width, opacity);
        } else {
            glowPanel.setEffectEnabled(enabled);
            glowPanel.setEffectWidth(width);
            glowPanel.setColor(color, false);
            glowPanel.setOpacity(opacity);
        }
    }

    private void initInnerGlowPanel(AreaEffects effects) {
        var effect = (effects == null) ? null : effects.getInnerGlow();
        boolean enabled = (effect != null);
        Color color = enabled ? effect.getBrushColor() : RED;
        double width = enabled ? effect.getEffectWidth() : 10;
        float opacity = enabled ? effect.getOpacity() : 1.0f;
        
        if (innerGlowPanel == null) { // first initialization
            innerGlowPanel = new EffectWithWidthPanel(
                "Inner Glow", enabled, color, width, opacity);
        } else {
            innerGlowPanel.setEffectEnabled(enabled);
            innerGlowPanel.setEffectWidth(width);
            innerGlowPanel.setColor(color, false);
            innerGlowPanel.setOpacity(opacity);
        }
    }

    private void initNeonBorderPanel(AreaEffects effects) {
        var effect = (effects == null) ? null : effects.getNeonBorder();
        boolean enabled = (effect != null);
        Color color = enabled ? effect.getEdgeColor() : GREEN;
        Color innerColor = enabled ? effect.getCenterColor() : WHITE;
        double width = enabled ? effect.getEffectWidth() : 10;
        float opacity = enabled ? effect.getOpacity() : 1.0f;
        
        if (neonBorderPanel == null) { // first initialization
            neonBorderPanel = new NeonBorderPanel(
                enabled, color, innerColor, width, opacity);
        } else {
            neonBorderPanel.setEffectEnabled(enabled);
            neonBorderPanel.setEffectWidth(width);
            neonBorderPanel.setColor(color, false);
            neonBorderPanel.setOpacity(opacity);
            neonBorderPanel.setInnerColor(innerColor, false);
        }
    }

    private void initDropShadowPanel(AreaEffects effects) {
        var effect = (effects == null) ? null : effects.getDropShadow();
        boolean enabled = (effect != null);
        Color color = enabled ? effect.getBrushColor() : BLACK;
        float opacity = enabled ? effect.getOpacity() : 1.0f;
        double spread = enabled ? effect.getEffectWidth() : 10;

        int distance = 10;
        double angle = 0.7;
        if (enabled) {
            Point2D offset = effect.getOffset();
            double x = offset.getX();
            double y = offset.getY();
            distance = (int) Math.sqrt(x * x + y * y);
            angle = Math.atan2(y, x);
        }

        if (dropShadowPanel == null) { // first initialization
            dropShadowPanel = new DropShadowPanel(
                enabled, color, distance, angle, spread, opacity);
        } else {
            dropShadowPanel.setEffectEnabled(enabled);
            dropShadowPanel.setEffectWidth(spread);
            dropShadowPanel.setColor(color, false);
            dropShadowPanel.setOpacity(opacity);
            dropShadowPanel.setAngle(angle);
            dropShadowPanel.setDistance(distance);
        }
    }

    /**
     * Updates the AreaEffects instance with the current GUI settings.
     */
    private void updateEffectsFromGUI(AreaEffects effects) {
        updateGlowFromGUI(effects);
        updateInnerGlowFromGUI(effects);
        updateNeonBorderFromGUI(effects);
        updateDropShadowFromGUI(effects);
    }

    private void updateGlowFromGUI(AreaEffects effects) {
        GlowPathEffect glowEffect = null;
        if (glowPanel.isEffectEnabled()) {
            glowEffect = new GlowPathEffect(glowPanel.getOpacity());
            glowPanel.updateEffectColorAndWidth(glowEffect);
        }
        effects.setGlow(glowEffect);
    }

    private void updateInnerGlowFromGUI(AreaEffects effects) {
        InnerGlowPathEffect innerGlowEffect = null;
        if (innerGlowPanel.isEffectEnabled()) {
            innerGlowEffect = new InnerGlowPathEffect(innerGlowPanel.getOpacity());
            innerGlowPanel.updateEffectColorAndWidth(innerGlowEffect);
        }
        effects.setInnerGlow(innerGlowEffect);
    }

    private void updateNeonBorderFromGUI(AreaEffects effects) {
        NeonBorderEffect neonBorderEffect = null;
        if (neonBorderPanel.isEffectEnabled()) {
            Color edgeColor = neonBorderPanel.getColor();
            Color centerColor = neonBorderPanel.getInnerColor();
            double effectWidth = neonBorderPanel.getEffectWidth();

            neonBorderEffect = new NeonBorderEffect(edgeColor, centerColor, effectWidth,
                neonBorderPanel.getOpacity());
        }
        effects.setNeonBorder(neonBorderEffect);
    }

    private void updateDropShadowFromGUI(AreaEffects effects) {
        ShadowPathEffect dropShadowEffect = null;
        if (dropShadowPanel.isEffectEnabled()) {
            dropShadowEffect = new ShadowPathEffect(dropShadowPanel.getOpacity());
            dropShadowPanel.updateEffectColorAndWidth(dropShadowEffect);
            dropShadowEffect.setOffset(dropShadowPanel.getOffset());
        }
        effects.setDropShadow(dropShadowEffect);
    }

    private void addTab(String name, EffectPanel configurator) {
        JPanel tabPanel = new JPanel(new FlowLayout(LEFT));
        JCheckBox tabCB = new JCheckBox();
        tabCB.setModel(configurator.getEnabledModel());
        tabCB.setName(name);
        tabPanel.add(tabCB);
        tabPanel.add(new JLabel(name));

        tabCB.addActionListener(e -> {
            if (tabCB.isSelected()) {
                tabs.setSelectedIndex(tabs.indexOfComponent(configurator));
            }
        });

        tabPanel.setOpaque(false);

        tabs.addTab(name, configurator);
        tabs.setTabComponentAt(tabs.getTabCount() - 1, tabPanel);
    }

    public void loadStateFrom(UserPreset preset) {
        AreaEffects newEffects = new AreaEffects();
        newEffects.loadStateFrom(preset);
        setEffects(newEffects);
    }

    public void saveStateTo(UserPreset preset) {
        AreaEffects effects = getEffects();
        effects.saveStateTo(preset);
    }

    /**
     * Returns an {@link AreaEffects} instance corresponding to the current GUI settings.
     */
    public AreaEffects getEffects() {
        AreaEffects effects = new AreaEffects();
        updateEffectsFromGUI(effects);
        return effects;
    }

    @Override
    public boolean isAtDefault() {
        for (EffectPanel panel : panels) {
            if (!panel.isAtDefault()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void reset(boolean trigger) {
        glowPanel.reset(false);
        innerGlowPanel.reset(false);
        neonBorderPanel.reset(false);
        dropShadowPanel.reset(trigger); // trigger only once
    }

    public void randomize() {
        for (EffectPanel panel : panels) {
            panel.randomize();
        }
    }

    public void setResetButton(ResetButton button) {
        for (EffectPanel panel : panels) {
            panel.setResetButton(button);
        }
    }

    @Override
    public String getResetToolTip() {
        return "Reset the default effect settings";
    }

    @Override
    public void updateGUI() {
    }

    @Override
    public void setToolTip(String tip) {
    }

    @Override
    public int getNumLayoutColumns() {
        return 1;
    }
}
