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

import net.jafama.FastMath;
import pixelitor.colors.Colors;
import pixelitor.filters.gui.*;
import pixelitor.filters.gui.IntChoiceParam.Item;
import pixelitor.utils.StatusBarProgressTracker;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static pixelitor.filters.gui.RandomizeMode.IGNORE_RANDOMIZE;

/**
 * The "Abstract Lights" filter.
 * The algorithm is based on <a href="https://codepen.io/tsuhre/pen/BYbjyg">this codepen by Ben Matthews</a>.
 */
public class AbstractLights extends ParametrizedFilter {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final float MIN_VISIBLE_ALPHA = 0.002f;

    public static final String NAME = "Abstract Lights";

    private static final int TYPE_CHAOS = 1;
    private static final int TYPE_STAR = 2;
    private static final int TYPE_FRAME = 3;

    private final IntChoiceParam typeParam = new IntChoiceParam("Type", new Item[]{
        new Item("Chaos", TYPE_CHAOS),
        new Item("Star", TYPE_STAR),
        new Item("Frame", TYPE_FRAME),
    });
    private final GroupedRangeParam starSizeParam = new GroupedRangeParam("Size", 0, 20, 100);
    private final ImagePositionParam starCenterParam = new ImagePositionParam("Center");

    private final RangeParam iterationsParam = new RangeParam("Iterations", 1, 1000, 5000);
    private final RangeParam complexityParam = new RangeParam("Complexity", 1, 10, 20);
    private final RangeParam brightnessParam = new RangeParam("Brightness", 1, 6, 10);
    private final AngleParam hueParam = new AngleParam("Hue", 0);
    private final RangeParam hueRandomnessParam = new RangeParam("Hue Variability", 0, 25, 100);
    private final RangeParam whiteBlendParam = new RangeParam("Mix White", 0, 0, 100);
    private final RangeParam blurParam = new RangeParam("Blur", 0, 0, 7);
    private final RangeParam speedParam = new RangeParam("Particle Speed", 1, 1, 10);
    private final BooleanParam bounceParam = new BooleanParam("Edge Bounce", true);

    public AbstractLights() {
        super(false);

        // disable hue variation when complexity is 1 (single particle)
        complexityParam.setupDisableOtherIf(hueRandomnessParam, value -> value == 1);

        // disable hue controls when fully white blend is selected
        whiteBlendParam.setupDisableOtherIf(hueParam, value -> value == 100);
        whiteBlendParam.setupDisableOtherIf(hueRandomnessParam, value -> value == 100);

        CompositeParam advancedParam = new CompositeParam("Advanced",
            hueRandomnessParam, whiteBlendParam, blurParam, speedParam, bounceParam);
        advancedParam.setRandomizePolicy(IGNORE_RANDOMIZE);

        CompositeParam starSettingsParam = new CompositeParam("Star Settings",
            starSizeParam, starCenterParam);
        // show star settings only for star type
        typeParam.setupEnableOtherIf(starSettingsParam, type -> type.valueIs(TYPE_STAR));
        // disable bounce for frame type
        typeParam.setupDisableOtherIf(bounceParam, type -> type.valueIs(TYPE_FRAME));

        initParams(
            typeParam,
            starSettingsParam,
            iterationsParam,
            complexityParam,
            brightnessParam,
            hueParam,
            advancedParam
        ).withReseedAction();
    }

    @Override
    public BufferedImage transform(BufferedImage src, BufferedImage dest) {
        Random random = paramSet.getLastSeedRandom();
        int iterations = iterationsParam.getValue();

        var pt = new StatusBarProgressTracker(NAME, iterations);
        int width = dest.getWidth();
        int height = dest.getHeight();

        Graphics2D g2 = dest.createGraphics();

        double lineWidth = blurParam.getValueAsDouble() + 1.0;
        g2.setStroke(new BasicStroke((float) lineWidth));

        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, width, height);

        float darkening = 1.0f;
        // the sqrt is an attempt to use linear light calculations
        float alpha = (float) (brightnessParam.getValueAsDouble() / (200.0 * Math.sqrt(lineWidth)));
        if (alpha < MIN_VISIBLE_ALPHA) {
            // if a smaller alpha is used, nothing is drawn, therefore
            // use this alpha and compensate by darkening the color.
            darkening = MIN_VISIBLE_ALPHA / alpha;
            alpha = MIN_VISIBLE_ALPHA;
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

        float colorBri = 1.0f / darkening;
        List<Particle> particles = createParticles(width, height, random, colorBri);

        for (int i = 0; i < iterations; i++) {
            for (Particle particle : particles) {
                particle.update(width, height);
                particle.draw(g2);
            }
            pt.unitDone();
        }
        pt.finished();

        return dest;
    }

    private List<Particle> createParticles(int width, int height, Random random, float bri) {
        int numParticles = complexityParam.getValue() + 1;
        int baseHue = hueParam.getValueInNonIntuitiveDegrees();
        int hueRandomness = (int) (hueRandomnessParam.getValue() * 3.6);

        boolean bounce = bounceParam.isChecked();
        double speed = speedParam.getValueAsDouble();
        int type = typeParam.getValue();

        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < numParticles; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            Color color = generateParticleColor(random, bri, baseHue, hueRandomness);
            double angle = 2 * random.nextDouble() * Math.PI;

            if (type == TYPE_FRAME) {
                particles.add(new FrameParticle(i % 4, speed, angle, color, bounce, x, y, width, height));
            } else {
                particles.add(new Particle(x, y, speed, angle, color, bounce));
            }
        }

        connectParticles(width, height, numParticles, speed, particles);

        return particles;
    }

    private void connectParticles(int width, int height, int numParticles, double speed, List<Particle> particles) {
        int connect = typeParam.getValue();
        switch (connect) {
            case TYPE_CHAOS, TYPE_FRAME -> connectInChain(particles, numParticles);
            case TYPE_STAR -> connectToStar(particles, width, height, numParticles, speed);
            default -> throw new IllegalStateException("connect = " + connect);
        }
    }

    private static void connectInChain(List<Particle> particles, int numParticles) {
        for (int i = 0; i < numParticles; i++) {
            int siblingIndex = (i == 0) ? numParticles - 1 : i - 1;
            particles.get(i).sibling = particles.get(siblingIndex);
        }
    }

    private void connectToStar(List<Particle> particles, int width, int height, int numParticles, double speed) {
        // replace the first particle with a star particle
        Color c = particles.getFirst().color;
        StarParticle centerStar = new StarParticle(0, 0, speed, c, starSizeParam, width, height, starCenterParam);
        particles.set(0, centerStar);

        for (int i = 1; i < numParticles; i++) {
            particles.get(i).sibling = centerStar;
        }
    }

    private Color generateParticleColor(Random random, float bri, int baseHue, int hueRandomness) {
        int hue;
        if (hueRandomness > 0) {
            hue = (baseHue + random.nextInt(hueRandomness) - hueRandomness / 2) % 360;
        } else {
            hue = baseHue;
            random.nextInt(); // maintain random sequence
        }

        Color color = Color.getHSBColor(hue / 360.0f, 1.0f, bri);
        if (whiteBlendParam.getValue() > 0) {
            color = Colors.interpolateRGB(color, Color.WHITE, whiteBlendParam.getPercentage());
        }
        return color;
    }

    private static class Particle {
        protected double x;
        protected double y;
        protected final double speed;
        protected double vx, vy;
        private final Color color;
        public Particle sibling;
        private final boolean bounce;

        public Particle(int x, int y, double speed, double angle, Color color, boolean bounce) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.vx = speed * FastMath.cos(angle);
            this.vy = speed * FastMath.sin(angle);
            this.color = color;
            this.bounce = bounce;
        }

        public void update(int width, int height) {
            x += vx;
            y += vy;

            if (bounce) {
                if (x < 0 || x >= width) {
                    vx = -vx;
                }
                if (y < 0 || y >= height) {
                    vy = -vy;
                }
            }

        }

        public void draw(Graphics2D g) {
            if (sibling == null) {
                return;
            }
            g.setColor(color);
            g.draw(new Line2D.Double(x, y, sibling.x, sibling.y));
        }
    }

    private static class StarParticle extends Particle {
        double angle = 0;
        private final double cx, cy;
        private final double radiusX;
        private final double radiusY;
        private final double angleIncrement;

        public StarParticle(int x, int y, double speed, Color color, GroupedRangeParam starSize, int width, int height, ImagePositionParam starCenterParam) {
            super(x, y, speed, 0, color, false);
            double maxRadius = Math.min(width, height) / 2.0;
            radiusX = maxRadius * starSize.getPercentage(0);
            radiusY = maxRadius * starSize.getPercentage(1);
            cx = width * starCenterParam.getRelativeX();
            cy = height * starCenterParam.getRelativeY();

            // For small angles the particle moves approximately
            // a distance of radius * angle pixels.
            angleIncrement = speed / Math.max(radiusX, radiusY);
        }

        @Override
        public void update(int width, int height) {
            if (radiusX == 0 && radiusY == 0) {
                x = cx;
                y = cy;
            } else {
                angle += angleIncrement;
                x = cx + radiusX * FastMath.cos(angle);
                y = cy + radiusY * FastMath.sin(angle);
            }
        }
    }

    private static class FrameParticle extends AbstractLights.Particle {
        private static final int STATE_TOP = 0;
        private static final int STATE_RIGHT = 1;
        private static final int STATE_BOTTOM = 2;
        private static final int STATE_LEFT = 3;
        private int state;

        public FrameParticle(int initialState, double speed, double angle, Color color, boolean bounce, int x, int y, int width, int height) {
            super(x, y, speed, angle, color, bounce);
            state = initialState;
            switch (state) {
                case STATE_TOP -> {
                    this.y = 0;
                    vx = speed;
                    vy = 0;
                }
                case STATE_RIGHT -> {
                    this.x = width;
                    vx = 0;
                    vy = speed;
                }
                case STATE_BOTTOM -> {
                    this.y = height;
                    vx = -speed;
                    vy = 0;
                }
                case STATE_LEFT -> {
                    this.x = 0;
                    vx = 0;
                    vy = -speed;
                }
                default -> throw new IllegalStateException("state = " + state);
            }
        }

        @Override
        public void update(int width, int height) {
            x += vx;
            y += vy;
            switch (state) {
                case STATE_TOP -> {
                    if (x >= width) {
                        state = STATE_RIGHT;
                        vx = 0;
                        vy = speed;
                    }
                }
                case STATE_RIGHT -> {
                    if (y >= height) {
                        state = STATE_BOTTOM;
                        vx = -speed;
                        vy = 0;
                    }
                }
                case STATE_BOTTOM -> {
                    if (x <= 0) {
                        state = STATE_LEFT;
                        vx = 0;
                        vy = -speed;
                    }
                }
                case STATE_LEFT -> {
                    if (y <= 0) {
                        state = STATE_TOP;
                        vx = speed;
                        vy = 0;
                    }
                }
                default -> throw new IllegalStateException("state = " + state);
            }
        }
    }
}
