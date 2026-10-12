package lodge.view;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import lodge.model.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleFunction;

/** Animations and the animated theme backgrounds. */
final class Effects {

    private static final Interpolator SMOOTH = Interpolator.SPLINE(0.2, 0.9, 0.3, 1);
    /** Eases out past the target and settles back, as if on a spring. */
    static final Interpolator SPRING = new Interpolator() {
        @Override
        protected double curve(double t) {
            double u = t - 1;
            return 1 + 2.7 * u * u * u + 1.7 * u * u;
        }
    };
    private static final PseudoClass FRESH = PseudoClass.getPseudoClass("fresh");

    private Effects() {
    }

    // ----- Backgrounds -----

    /** Midnight Gold: glowing orbs that drift and breathe, twinkling stars and the odd shooting star. */
    static Pane midnightBackground() {
        Pane bg = backgroundPane();
        List<Animation> running = new ArrayList<>();
        stopWhenRemoved(bg, () -> running.forEach(Animation::stop));
        Random random = new Random(42);

        Color[] colors = {Color.web("#d4af37"), Color.web("#1fbf8f"), Color.web("#5b4bdb"),
                Color.web("#1e6fd9"), Color.web("#b8327a")};
        double[][] spots = {{0.12, 0.18}, {0.88, 0.22}, {0.72, 0.88}, {0.2, 0.82}, {0.5, 0.45}};
        for (int i = 0; i < colors.length; i++) {
            Circle orb = new Circle(220 + random.nextDouble() * 160);
            orb.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, colors[i].deriveColor(0, 1, 1, 0.30)),
                    new Stop(0.55, colors[i].deriveColor(0, 1, 1, 0.09)),
                    new Stop(1, Color.TRANSPARENT)));
            placeAt(orb, bg, spots[i][0], spots[i][1]);

            TranslateTransition drift = new TranslateTransition(Duration.seconds(16 + random.nextDouble() * 12), orb);
            drift.setByX((random.nextDouble() - 0.5) * 440);
            drift.setByY((random.nextDouble() - 0.5) * 320);
            running.add(loop(drift));

            ScaleTransition breathe = new ScaleTransition(Duration.seconds(9 + random.nextDouble() * 6), orb);
            breathe.setToX(1.25);
            breathe.setToY(1.25);
            running.add(loop(breathe));
            bg.getChildren().add(orb);
        }

        addStars(bg, running, random, 70, Color.rgb(255, 248, 225, 0.9), 1.0);

        Rectangle streak = new Rectangle(140, 1.6, new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT), new Stop(1, Color.rgb(255, 240, 200, 0.95))));
        streak.setManaged(false);
        streak.setRotate(18);
        streak.setOpacity(0);
        Timeline shoot = new Timeline(
                new KeyFrame(Duration.ZERO, e -> {
                    streak.setLayoutX(bg.getWidth() * (0.05 + random.nextDouble() * 0.55));
                    streak.setLayoutY(bg.getHeight() * random.nextDouble() * 0.35);
                },
                        new KeyValue(streak.translateXProperty(), 0),
                        new KeyValue(streak.translateYProperty(), 0),
                        new KeyValue(streak.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(150), new KeyValue(streak.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(streak.translateXProperty(), 420, Interpolator.EASE_IN),
                        new KeyValue(streak.translateYProperty(), 136, Interpolator.EASE_IN),
                        new KeyValue(streak.opacityProperty(), 0)),
                new KeyFrame(Duration.seconds(7)));
        shoot.setCycleCount(Animation.INDEFINITE);
        shoot.setDelay(Duration.seconds(3));
        shoot.play();
        running.add(shoot);
        bg.getChildren().add(streak);
        return bg;
    }

    /**
     * Aurora Borealis: curtains of green, teal and violet light that wave, ripple and shimmer above a dark
     * mountain range, with faint stars and slow falling snow sparkles.
     */
    static Pane auroraBackground() {
        Pane bg = backgroundPane();
        List<Animation> running = new ArrayList<>();
        Random random = new Random(7);
        addStars(bg, running, random, 45, Color.rgb(225, 250, 255, 0.85), 0.7);

        // Each curtain: bottom colour to top colour along its length, where its lower edge sits (fraction of
        // the height), how tall it is, how bright, and a phase so no two move in step
        Curtain[] curtains = {
                new Curtain(bg, 150, "#22d3ee", "#a78bfa", 0.30, 0.26, 0.50, 1.7),
                new Curtain(bg, 170, "#3dffb0", "#22d3ee", 0.44, 0.34, 0.70, 0.0),
                new Curtain(bg, 130, "#a78bfa", "#f0abfc", 0.58, 0.20, 0.38, 3.4),
        };

        Polygon farHills = new Polygon();
        farHills.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0d2a38", 0.85)), new Stop(1, Color.web("#04101a", 0.95))));
        Polygon nearHills = new Polygon();
        nearHills.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#06141d")), new Stop(1, Color.web("#02070c"))));
        double[] far = ridge(random, 14, 0.80, 0.09);
        double[] near = ridge(random, 9, 0.87, 0.07);
        Runnable shapeHills = () -> {
            drawRidge(farHills, far, bg.getWidth(), bg.getHeight());
            drawRidge(nearHills, near, bg.getWidth(), bg.getHeight());
        };
        bg.widthProperty().addListener(obs -> shapeHills.run());
        bg.heightProperty().addListener(obs -> shapeHills.run());
        bg.getChildren().addAll(farHills, nearHills);

        int flakes = 55;
        Circle[] snow = new Circle[flakes];
        double[][] fall = new double[flakes][]; // x fraction, y in px (-1 = not placed yet), speed px/s, sway phase
        for (int i = 0; i < flakes; i++) {
            snow[i] = new Circle(0.6 + random.nextDouble() * 1.2, Color.rgb(236, 254, 255, 0.85));
            snow[i].setManaged(false);
            fall[i] = new double[]{random.nextDouble(), -1, 10 + random.nextDouble() * 22, random.nextDouble() * 6.3};
            bg.getChildren().add(snow[i]);
        }

        AnimationTimer sky = new AnimationTimer() {
            private long start = -1;
            private long last;

            @Override
            public void handle(long now) {
                if (start < 0) {
                    start = now;
                    last = now;
                }
                double t = (now - start) / 1e9;
                double dt = Math.min(0.1, (now - last) / 1e9);
                last = now;
                double w = bg.getWidth();
                double h = bg.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                for (Curtain curtain : curtains) {
                    curtain.update(t, w, h);
                }
                for (int i = 0; i < flakes; i++) {
                    double[] f = fall[i];
                    if (f[1] < 0 || f[1] > h) {
                        f[1] = f[1] < 0 ? random.nextDouble() * h : 0; // scatter at first, then fall from the top
                        f[0] = random.nextDouble();
                    }
                    f[1] += f[2] * dt;
                    snow[i].setCenterX(f[0] * w + Math.sin(t * 0.6 + f[3]) * 14);
                    snow[i].setCenterY(f[1]);
                    snow[i].setOpacity(0.35 + 0.35 * Math.sin(t * 1.4 + f[3] * 3));
                }
            }
        };
        sky.start();
        stopWhenRemoved(bg, () -> {
            sky.stop();
            running.forEach(Animation::stop);
        });
        return bg;
    }

    /** One curtain of aurora: many thin vertical rays whose lower edge follows a slowly travelling wave. */
    private static final class Curtain {
        private final Group group = new Group();
        private final Rectangle[] rays;
        private final double base;
        private final double height;
        private final double phase;

        Curtain(Pane bg, int count, String bottomColor, String topColor, double base, double height,
                double brightness, double phase) {
            this.base = base;
            this.height = height;
            this.phase = phase;
            rays = new Rectangle[count];
            Color from = Color.web(bottomColor);
            Color to = Color.web(topColor);
            for (int i = 0; i < count; i++) {
                Color c = from.interpolate(to, i / (double) (count - 1));
                // Faint at the top, a bright fringe near the bottom, then a soft cut-off, like the real thing
                rays[i] = new Rectangle();
                rays[i].setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.TRANSPARENT),
                        new Stop(0.55, c.deriveColor(0, 1, 1, brightness * 0.45)),
                        new Stop(0.88, c.deriveColor(0, 1, 1, brightness)),
                        new Stop(1, Color.TRANSPARENT)));
            }
            group.getChildren().addAll(rays);
            group.setManaged(false);
            group.setEffect(new GaussianBlur(22));
            bg.getChildren().add(group);
        }

        void update(double t, double w, double h) {
            int n = rays.length;
            double rayWidth = w * 1.3 / n;
            for (int i = 0; i < n; i++) {
                double u = i / (double) (n - 1);
                double bottom = h * (base
                        + 0.07 * Math.sin(u * 4.1 + t * 0.23 + phase)
                        + 0.025 * Math.sin(u * 13.0 - t * 0.55 + phase * 2));
                double tall = h * height * (0.7 + 0.3 * Math.sin(u * 19.0 + t * 0.8 + phase));
                Rectangle ray = rays[i];
                ray.setX(-0.15 * w + u * w * 1.3);
                ray.setWidth(rayWidth + 2);
                ray.setY(bottom - tall);
                ray.setHeight(tall);
                // Broad bright patches drift along the curtain, with a finer flicker of rays inside them
                double glow = Math.sin(u * 7.0 - t * 0.5 + phase);
                double flicker = Math.sin(u * 61.0 + t * 1.7 + phase);
                ray.setOpacity(Math.min(1, 0.35 + 0.55 * glow * glow + 0.12 * flicker));
            }
            group.setOpacity(0.75 + 0.25 * Math.sin(t * 0.17 + phase));
        }
    }

    /** A mountain ridge as (x, y) pairs in fractions of the window: alternating peaks and dips. */
    private static double[] ridge(Random random, int peaks, double line, double rise) {
        double[] points = new double[(peaks + 1) * 2];
        for (int i = 0; i <= peaks; i++) {
            points[i * 2] = i / (double) peaks;
            points[i * 2 + 1] = line - (i % 2 == 0 ? 0.3 : 1) * rise * (0.4 + random.nextDouble() * 0.6);
        }
        return points;
    }

    private static void drawRidge(Polygon hills, double[] ridge, double w, double h) {
        hills.getPoints().clear();
        hills.getPoints().addAll(0.0, h);
        for (int i = 0; i < ridge.length; i += 2) {
            hills.getPoints().addAll(ridge[i] * w, ridge[i + 1] * h);
        }
        hills.getPoints().addAll(w, h);
    }

    private static Pane backgroundPane() {
        Pane bg = new Pane();
        bg.getStyleClass().add("app-background");
        bg.setMouseTransparent(true);
        return bg;
    }

    private static void addStars(Pane bg, List<Animation> running, Random random, int count, Color color,
                                 double brightness) {
        for (int i = 0; i < count; i++) {
            Circle star = new Circle(0.5 + random.nextDouble() * 1.3, color);
            placeAt(star, bg, random.nextDouble(), random.nextDouble());
            star.setOpacity(0.15);
            FadeTransition twinkle = new FadeTransition(Duration.seconds(1.5 + random.nextDouble() * 3.5), star);
            twinkle.setFromValue(0.1);
            twinkle.setToValue((0.4 + random.nextDouble() * 0.6) * brightness);
            twinkle.setDelay(Duration.seconds(random.nextDouble() * 4));
            running.add(loop(twinkle));
            bg.getChildren().add(star);
        }
    }

    /** Endless background animations stop once the background is taken off screen (on a theme change). */
    private static void stopWhenRemoved(Pane bg, Runnable stopAll) {
        bg.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null && scene == null) {
                stopAll.run();
            }
        });
    }

    private static void placeAt(Circle shape, Pane bg, double fractionX, double fractionY) {
        shape.setManaged(false);
        shape.centerXProperty().bind(bg.widthProperty().multiply(fractionX));
        shape.centerYProperty().bind(bg.heightProperty().multiply(fractionY));
    }

    private static Animation loop(Animation animation) {
        animation.setAutoReverse(true);
        animation.setCycleCount(Animation.INDEFINITE);
        if (animation instanceof javafx.animation.Transition t) {
            t.setInterpolator(Interpolator.EASE_BOTH);
        }
        animation.play();
        return animation;
    }

    // ----- One-off animations -----

    /** Prepares a node to fade and slide up into place; call play() on the result once the window shows. */
    static Animation entrance(Node node, double delayMillis) {
        node.setOpacity(0);
        node.setTranslateY(30);
        FadeTransition fade = new FadeTransition(Duration.millis(700), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(700), node);
        slide.setToY(0);
        slide.setInterpolator(SMOOTH);
        ParallelTransition both = new ParallelTransition(fade, slide);
        both.setDelay(Duration.millis(delayMillis));
        return both;
    }

    static void popIn(Node node) {
        node.setScaleX(0.9);
        node.setScaleY(0.9);
        ScaleTransition scale = new ScaleTransition(Duration.millis(320), node);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(SMOOTH);
        FadeTransition fade = new FadeTransition(Duration.millis(320), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        new ParallelTransition(scale, fade).play();
    }

    /** "No" head-shake for a field with a mistake. */
    static void shake(Node node) {
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(60), new KeyValue(node.translateXProperty(), -9)),
                new KeyFrame(Duration.millis(120), new KeyValue(node.translateXProperty(), 9)),
                new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(240), new KeyValue(node.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), -2)),
                new KeyFrame(Duration.millis(360), new KeyValue(node.translateXProperty(), 0)));
        shake.play();
    }

    static void pulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(160), node);
        pulse.setToX(1.015);
        pulse.setToY(1.015);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();
    }

    /** New or changed table row: slides in from the left with a short gold highlight. */
    static void rowFlash(Node row) {
        row.setOpacity(0);
        row.setTranslateX(-24);
        FadeTransition fade = new FadeTransition(Duration.millis(450), row);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(450), row);
        slide.setToX(0);
        slide.setInterpolator(SMOOTH);
        new ParallelTransition(fade, slide).play();
        row.pseudoClassStateChanged(FRESH, true);
        PauseTransition hold = new PauseTransition(Duration.millis(1600));
        hold.setOnFinished(e -> row.pseudoClassStateChanged(FRESH, false));
        hold.play();
    }

    // ----- Hover -----

    static void hoverPop(Node... nodes) {
        for (Node node : nodes) {
            ScaleTransition scale = new ScaleTransition(Duration.millis(130), node);
            node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> scaleTo(scale, 1.05));
            node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> scaleTo(scale, 1.0));
            node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> scaleTo(scale, 0.96));
            node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> scaleTo(scale, node.isHover() ? 1.05 : 1.0));
        }
    }

    static void hoverLift(Node node) {
        TranslateTransition lift = new TranslateTransition(Duration.millis(180), node);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            lift.stop();
            lift.setToY(-4);
            lift.play();
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            lift.stop();
            lift.setToY(0);
            lift.play();
        });
    }

    private static void scaleTo(ScaleTransition scale, double value) {
        scale.stop();
        scale.setToX(value);
        scale.setToY(value);
        scale.play();
    }

    // ----- Celebration -----

    /** Paid in full: a burst of confetti in the theme colours that arcs up, tumbles and falls. */
    static void confetti(Pane layer, double x, double y) {
        Color[] colors = Theme.current().confetti();
        Random random = new Random();
        List<double[]> motion = new ArrayList<>(); // vx, vy, spin per piece
        List<Rectangle> pieces = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            Rectangle piece = new Rectangle(5 + random.nextDouble() * 5, 8 + random.nextDouble() * 6,
                    colors[random.nextInt(colors.length)]);
            piece.setArcWidth(3);
            piece.setArcHeight(3);
            piece.setManaged(false);
            piece.setLayoutX(x);
            piece.setLayoutY(y);
            double angle = Math.toRadians(-90 + (random.nextDouble() - 0.5) * 140);
            double speed = 380 + random.nextDouble() * 520;
            motion.add(new double[]{Math.cos(angle) * speed, Math.sin(angle) * speed,
                    (random.nextDouble() - 0.5) * 900});
            pieces.add(piece);
        }
        layer.getChildren().addAll(pieces);

        double lifetime = 2.2;
        new AnimationTimer() {
            private long start = -1;
            private long last;

            @Override
            public void handle(long now) {
                if (start < 0) {
                    start = now;
                    last = now;
                    return;
                }
                double dt = (now - last) / 1e9;
                double age = (now - start) / 1e9;
                last = now;
                for (int i = 0; i < pieces.size(); i++) {
                    Rectangle piece = pieces.get(i);
                    double[] m = motion.get(i);
                    m[0] *= 1 - 1.6 * dt; // air drag
                    m[1] += 980 * dt;     // gravity
                    m[1] *= 1 - 0.9 * dt;
                    piece.setTranslateX(piece.getTranslateX() + m[0] * dt);
                    piece.setTranslateY(piece.getTranslateY() + m[1] * dt);
                    piece.setRotate(piece.getRotate() + m[2] * dt);
                    piece.setScaleX(Math.cos(age * 9 + i)); // flutter, like paper turning over
                    piece.setOpacity(Math.max(0, 1 - Math.pow(age / lifetime, 3)));
                }
                if (age >= lifetime) {
                    stop();
                    layer.getChildren().removeAll(pieces);
                }
            }
        }.start();
    }

    // ----- Click ripple -----

    /** A soft circle of light spreads out from where the button was clicked, kept inside the button. */
    static void ripple(Pane layer, Node... buttons) {
        for (Node button : buttons) {
            button.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if (button.isDisabled() || layer.getScene() == null) {
                    return;
                }
                Bounds box = layer.sceneToLocal(button.localToScene(button.getBoundsInLocal()));
                Point2D at = layer.sceneToLocal(e.getSceneX(), e.getSceneY());
                double reach = Math.hypot(Math.max(at.getX() - box.getMinX(), box.getMaxX() - at.getX()),
                        Math.max(at.getY() - box.getMinY(), box.getMaxY() - at.getY()));
                Circle wave = new Circle(at.getX(), at.getY(), 1, Theme.current().ripple());
                wave.setManaged(false);
                Rectangle clip = new Rectangle(box.getMinX(), box.getMinY(), box.getWidth(), box.getHeight());
                clip.setArcWidth(20);
                clip.setArcHeight(20);
                wave.setClip(clip);
                layer.getChildren().add(wave);
                Timeline spread = new Timeline(new KeyFrame(Duration.millis(520),
                        new KeyValue(wave.radiusProperty(), reach, SMOOTH),
                        new KeyValue(wave.opacityProperty(), 0, Interpolator.EASE_IN)));
                spread.setOnFinished(done -> layer.getChildren().remove(wave));
                spread.play();
            });
        }
    }

    // ----- Page switching -----

    /** Slides the old page out and the new one in; direction 1 moves forward (to the left), -1 back. */
    static Animation slidePages(Node out, Node in, int direction) {
        double distance = 70 * direction;
        if (!in.isVisible()) {
            in.setOpacity(0);
            in.setTranslateX(distance);
            in.setVisible(true);
        }
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(420),
                new KeyValue(in.opacityProperty(), 1, SMOOTH),
                new KeyValue(in.translateXProperty(), 0, SMOOTH),
                new KeyValue(out.opacityProperty(), 0, SMOOTH),
                new KeyValue(out.translateXProperty(), -distance, SMOOTH)));
        slide.setOnFinished(e -> {
            out.setVisible(false);
            out.setOpacity(1);
            out.setTranslateX(0);
        });
        slide.play();
        return slide;
    }

    /**
     * Moves the nav pill like a drop of liquid gold: the leading edge stretches out first,
     * then the trailing edge catches up.
     */
    static void slidePill(Timeline timeline, DoubleProperty x, DoubleProperty width, double toX, double toWidth) {
        double fromX = x.get();
        double stretchX = Math.min(fromX, toX);
        double stretchWidth = Math.max(fromX + width.get(), toX + toWidth) - stretchX;
        Interpolator lead = Interpolator.SPLINE(0.4, 0, 0.2, 1);
        timeline.stop();
        timeline.getKeyFrames().setAll(
                new KeyFrame(Duration.millis(190),
                        new KeyValue(x, stretchX, lead),
                        new KeyValue(width, stretchWidth, lead)),
                new KeyFrame(Duration.millis(440),
                        new KeyValue(x, toX, SMOOTH),
                        new KeyValue(width, toWidth, SMOOTH)));
        timeline.playFromStart();
    }

    // ----- Count-up numbers -----

    static CountUpLabel moneyCounter() {
        return new CountUpLabel(Money::format);
    }

    /** A label whose number rolls smoothly to each new value. */
    static final class CountUpLabel extends Label {
        private final DoubleProperty value = new SimpleDoubleProperty();
        private final Timeline timeline = new Timeline();
        private final ScaleTransition bump = new ScaleTransition(Duration.millis(170), this);
        private double target;
        private boolean bumps;

        CountUpLabel(DoubleFunction<String> format) {
            value.addListener((obs, old, v) -> setText(format.apply(v.doubleValue())));
            setText(format.apply(0));
        }

        /** The number gives a little pop whenever it changes, so the eye catches it. */
        CountUpLabel bumpOnChange() {
            bumps = true;
            bump.setToX(1.12);
            bump.setToY(1.12);
            bump.setAutoReverse(true);
            bump.setCycleCount(2);
            bump.setInterpolator(Interpolator.EASE_OUT);
            return this;
        }

        void animateTo(double target) {
            if (bumps && Math.abs(target - this.target) > 0.004) {
                bump.stop();
                setScaleX(1);
                setScaleY(1);
                bump.playFromStart();
            }
            this.target = target;
            timeline.stop();
            timeline.getKeyFrames().setAll(
                    new KeyFrame(Duration.millis(650), new KeyValue(value, target, Interpolator.EASE_OUT)));
            timeline.playFromStart();
        }

        void animateTo(BigDecimal target) {
            animateTo(target.doubleValue());
        }
    }
}
