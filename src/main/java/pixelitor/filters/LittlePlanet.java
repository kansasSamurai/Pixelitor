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

import pixelitor.filters.gui.*;
import pixelitor.filters.impl.LittlePlanetFilter;

import java.awt.image.BufferedImage;
import java.io.Serial;

import static pixelitor.gui.GUIText.ZOOM;
import static pixelitor.utils.AngleUnit.INTUITIVE_DEGREES;
import static pixelitor.utils.Texts.i18n;

/**
 * "Little Planet" filter
 */
public class LittlePlanet extends ParametrizedFilter {
    public static final String NAME = i18n("little_planet");

    @Serial
    private static final long serialVersionUID = 3658391628537896082L;

    private final ImagePositionParam center = new ImagePositionParam("Center");
    private final AngleParam rotateResult = new AngleParam("Rotate Result", 90, INTUITIVE_DEGREES);
    private final RangeParam zoom = new RangeParam(ZOOM + " (%)", 1, 100, 501);
    private final RangeParam innerZoom = new RangeParam("Inner Zoom (%)", 30, 100, 170);
    private final BooleanParam invert = new BooleanParam("Invert");
    private final IntChoiceParam edgeAction = IntChoiceParam.forEdgeAction(true);
    private final IntChoiceParam interpolation = IntChoiceParam.forInterpolation();

    private LittlePlanetFilter filter;

    public LittlePlanet() {
        super(true);

        zoom.setPresetKey("Zoom (%)");

        initParams(
            rotateResult,
            zoom,
            innerZoom,
            center,
            invert,
            edgeAction,
            interpolation
        );
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        if (filter == null) {
            filter = new LittlePlanetFilter();
        }

        filter.setZoom(zoom.getPercentage());
        filter.setInnerZoom(innerZoom.getPercentage());
        filter.setRotationAngle(rotateResult.getValueInIntuitiveRadians());
        filter.setInverted(invert.isChecked());

        filter.setCenter(center.getAbsolutePoint(src));
        filter.setEdgeAction(edgeAction.getValue());
        filter.setInterpolation(interpolation.getValue());

        return filter.filter(src, dest);
    }
}