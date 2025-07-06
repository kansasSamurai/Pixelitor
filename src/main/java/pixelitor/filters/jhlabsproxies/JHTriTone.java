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

package pixelitor.filters.jhlabsproxies;

import com.jhlabs.image.TritoneFilter;
import pixelitor.filters.ParametrizedFilter;
import pixelitor.filters.gui.ColorParam;

import java.awt.image.BufferedImage;
import java.io.Serial;

import static java.awt.Color.BLACK;
import static java.awt.Color.RED;
import static java.awt.Color.YELLOW;
import static pixelitor.filters.gui.TransparencyMode.OPAQUE_ONLY;

/**
 * Tritone filter based on the JHLabs TritoneFilter
 */
public class JHTriTone extends ParametrizedFilter {
    public static final String NAME = "Tritone";

    @Serial
    private static final long serialVersionUID = 7244953260858845197L;

    private final ColorParam shadowColor = new ColorParam("Shadow Color", BLACK, OPAQUE_ONLY);
    private final ColorParam midtonesColor = new ColorParam("Midtones Color", RED, OPAQUE_ONLY);
    private final ColorParam highlightsColor = new ColorParam("Highlights Color", YELLOW, OPAQUE_ONLY);

    private TritoneFilter filter;

    public JHTriTone() {
        super(true);

        initParams(shadowColor, midtonesColor, highlightsColor);
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        if (filter == null) {
            filter = new TritoneFilter(NAME);
        }

        filter.setShadowColor(shadowColor.getColor().getRGB());
        filter.setHighColor(highlightsColor.getColor().getRGB());
        filter.setMidColor(midtonesColor.getColor().getRGB());

        return filter.filter(src, dest);
    }
}