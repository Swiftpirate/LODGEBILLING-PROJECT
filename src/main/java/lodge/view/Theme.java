package lodge.view;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/** The look of the whole app: its colours (a stylesheet layered over styles.css) and its moving background. */
public enum Theme {

    MIDNIGHT("Midnight Gold", null, Effects::midnightBackground, Color.web("#d4af37"),
            Color.rgb(255, 246, 214, 0.45),
            "#f1d67a", "#d4af37", "#2ecc9a", "#fff6dc", "#ff8a9b"),
    AURORA("Aurora Borealis", "aurora.css", Effects::auroraBackground, Color.web("#5eead4"),
            Color.rgb(204, 251, 241, 0.45),
            "#6ee7b7", "#22d3ee", "#a78bfa", "#ecfeff", "#99f6e4");

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final String KEY = "theme";
    private static final ObjectProperty<Theme> CURRENT = new SimpleObjectProperty<>(saved());

    private final String displayName;
    private final List<String> stylesheets;
    private final Supplier<Pane> background;
    private final Color accent;
    private final Color ripple;
    private final Color[] confetti;

    Theme(String displayName, String extraStylesheet, Supplier<Pane> background, Color accent, Color ripple,
          String... confetti) {
        this.displayName = displayName;
        this.stylesheets = extraStylesheet == null ? List.of(Ui.STYLESHEET) : List.of(Ui.STYLESHEET,
                Objects.requireNonNull(Theme.class.getResource(extraStylesheet), extraStylesheet + " is missing")
                        .toExternalForm());
        this.background = background;
        this.accent = accent;
        this.ripple = ripple;
        this.confetti = java.util.Arrays.stream(confetti).map(Color::web).toArray(Color[]::new);
    }

    public static Theme current() {
        return CURRENT.get();
    }

    public static ReadOnlyObjectProperty<Theme> currentProperty() {
        return CURRENT;
    }

    /** Switches the whole app to this theme and remembers it for next time. */
    public static void use(Theme theme) {
        CURRENT.set(theme);
        PREFS.put(KEY, theme.name());
    }

    private static Theme saved() {
        try {
            return valueOf(PREFS.get(KEY, AURORA.name()));
        } catch (IllegalArgumentException e) {
            return AURORA;
        }
    }

    public String displayName() {
        return displayName;
    }

    /** styles.css first, then this theme's colours on top. */
    List<String> stylesheets() {
        return stylesheets;
    }

    Pane newBackground() {
        return background.get();
    }

    /** Glow colour for titles. */
    Color glow(double opacity) {
        return accent.deriveColor(0, 1, 1, opacity);
    }

    Color ripple() {
        return ripple;
    }

    Color[] confetti() {
        return confetti.clone();
    }
}
