package lodge.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small notifications that spring up from the bottom of the window, count down and fade away. */
public final class Toaster {

    public enum Kind {
        SUCCESS("toast-success", "✓  "),
        INFO("toast-info", "ℹ  "),
        ERROR("toast-error", "⚠  ");

        private final String styleClass;
        private final String icon;

        Kind(String styleClass, String icon) {
            this.styleClass = styleClass;
            this.icon = icon;
        }
    }

    private final VBox stack = new VBox(8);

    public Toaster(StackPane overlay) {
        stack.setAlignment(Pos.BOTTOM_CENTER);
        stack.setFillWidth(false);
        stack.setPadding(new Insets(0, 0, 30, 0));
        stack.setMouseTransparent(true);
        overlay.getChildren().add(stack);
    }

    public void show(String message, Kind kind) {
        Label text = new Label(kind.icon + message);
        text.getStyleClass().add("toast-text");
        // A thin bar along the bottom shrinks away while the toast is up
        Region timer = new Region();
        timer.getStyleClass().add("toast-timer");
        timer.setManaged(false);
        StackPane toast = new StackPane(text, timer);
        toast.getStyleClass().addAll("toast", kind.styleClass);
        toast.layoutBoundsProperty().addListener((obs, old, box) ->
                timer.resizeRelocate(14, box.getHeight() - 4, box.getWidth() - 28, 2.5));
        toast.setOpacity(0);
        toast.setTranslateY(36);
        toast.setScaleX(0.92);
        toast.setScaleY(0.92);
        stack.getChildren().add(toast);

        // Springy rise: the slight overshoot makes it settle like it has weight
        Interpolator spring = Effects.SPRING;
        FadeTransition fadeIn = new FadeTransition(Duration.millis(260), toast);
        fadeIn.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(480), toast);
        rise.setToY(0);
        rise.setInterpolator(spring);
        ScaleTransition grow = new ScaleTransition(Duration.millis(480), toast);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(spring);
        ScaleTransition countdown = new ScaleTransition(Duration.millis(kind == Kind.ERROR ? 4200 : 2800), timer);
        countdown.setFromX(1);
        countdown.setToX(0);
        countdown.setInterpolator(Interpolator.LINEAR);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(450), toast);
        fadeOut.setToValue(0);
        TranslateTransition sink = new TranslateTransition(Duration.millis(450), toast);
        sink.setToY(12);

        SequentialTransition life = new SequentialTransition(
                new ParallelTransition(fadeIn, rise, grow),
                countdown,
                new ParallelTransition(fadeOut, sink));
        life.setOnFinished(e -> stack.getChildren().remove(toast));
        life.play();
    }
}
