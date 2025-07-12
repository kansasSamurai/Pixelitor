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

package pixelitor.gui;

import pixelitor.gui.utils.GUIUtils;
import pixelitor.menus.view.ZoomControl;
import pixelitor.utils.ProgressHandler;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.EAST;
import static java.awt.FlowLayout.LEFT;
import static javax.swing.BorderFactory.createEtchedBorder;
import static pixelitor.utils.Threads.callInfo;
import static pixelitor.utils.Threads.calledOnEDT;

/**
 * The status bar of the app.
 */
public class StatusBar extends JPanel {
    private static final String INITIAL_MESSAGE = "Pixelitor started";
    private static final StatusBar INSTANCE = new StatusBar();

    private final JLabel messageLabel;
    private final JPanel progressContainer;

    private static int activeProgressBarCount = 0;

    private StatusBar() {
        super(new BorderLayout(0, 0));

        progressContainer = new JPanel(new FlowLayout(LEFT, 5, 0));
        messageLabel = new JLabel(INITIAL_MESSAGE);
        // messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        progressContainer.add(messageLabel);

        add(progressContainer, CENTER);
        add(ZoomControl.get(), EAST);

        setBorder(createEtchedBorder());
    }

    public void updateMessage(String msg) {
        assert msg != null;
        if (activeProgressBarCount > 0) {
            // ignore any messages
        } else {
            messageLabel.setText(msg);
        }
    }

    /**
     * Creates and starts a new progress bar in the status bar.
     */
    public ProgressHandler startProgress(String msg, int max) {
        assert calledOnEDT() : callInfo();
        assert msg != null;

        // each progress handler will add its own label 
        messageLabel.setText("");

        activeProgressBarCount++;
        return new StatusBarProgressHandler(progressContainer, msg, max);
    }

    public static StatusBar get() {
        return INSTANCE;
    }

    public static boolean isShown() {
        return INSTANCE.getParent() != null;
    }

    /**
     * A handler for managing a progress bar in the status bar.
     * If multiple handlers are active at the same time,
     * then each of them has its own progress bar.
     */
    static class StatusBarProgressHandler implements ProgressHandler {
        private final JLabel msgLabel;
        private final JPanel container;
        private final JProgressBar progressBar;
        private final boolean determinate;

        public StatusBarProgressHandler(JPanel container, String msg, int max) {
            assert calledOnEDT() : callInfo();

            this.container = container;
            determinate = max > 0;

            if (determinate) {
                progressBar = new JProgressBar(0, max);
            } else {
                progressBar = new JProgressBar(0, 100);
                progressBar.setIndeterminate(true);
            }
            msgLabel = new JLabel(msg + ":");

            container.add(msgLabel);
            container.add(progressBar);

            // call these instead of revalidate()/repaint()
            // because the EDT will be blocked
            container.validate(); // otherwise the panel width/height are 0
            GUIUtils.paintImmediately(container);
        }

        @Override
        public void updateProgress(int currentValue) {
            assert calledOnEDT() : callInfo();
            assert determinate;

            progressBar.setValue(currentValue);
            container.paintImmediately(progressBar.getBounds());
        }

        @Override
        public void stopProgress() {
            assert calledOnEDT() : callInfo();

            if (!determinate) {
                progressBar.setValue(100);
                progressBar.setIndeterminate(false);
            }

            container.remove(progressBar);
            container.remove(msgLabel);

            container.revalidate();
            container.repaint();
            activeProgressBarCount--;
        }
    }
}
