package lodge.view;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** The main window: animated background, one screen at a time on top of it, and the toasts above that. */
public final class AppWindow {

    /** Holds either the login screen or the billing screen, above the shared animated background. */
    private final StackPane content = new StackPane();
    /** The moving theme background; on a theme change the new one fades in under the old. */
    private final StackPane backdrop = new StackPane();
    private final Stage stage;
    private final Toaster toaster;

    /** onActivity runs on every key press, click or mouse move, for the inactivity timer. */
    public AppWindow(Stage stage, Runnable onActivity) {
        this.stage = stage;
        backdrop.setMouseTransparent(true);
        backdrop.getChildren().add(Theme.current().newBackground());
        StackPane root = new StackPane(backdrop, content);
        toaster = new Toaster(root);

        // Never open taller or wider than the screen (laptops with 125% scaling are only ~820px high)
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(1240, screen.getWidth()), Math.min(900, screen.getHeight()));
        scene.getStylesheets().setAll(Theme.current().stylesheets());
        Theme.currentProperty().addListener((obs, old, theme) -> {
            scene.getStylesheets().setAll(theme.stylesheets());
            crossFadeBackground(theme);
        });
        scene.addEventFilter(InputEvent.ANY, e -> onActivity.run());

        stage.setScene(scene);
        stage.setMinWidth(Math.min(1020, screen.getWidth()));
        stage.setMinHeight(Math.min(640, screen.getHeight()));
        stage.setMaximized(screen.getHeight() < 900 || screen.getWidth() < 1240);
    }

    private void crossFadeBackground(Theme theme) {
        // A background still fading out from a quick earlier switch is simply dropped
        backdrop.getChildren().remove(1, backdrop.getChildren().size());
        Node old = backdrop.getChildren().get(0);
        backdrop.getChildren().add(0, theme.newBackground());
        FadeTransition fade = new FadeTransition(Duration.millis(1400), old);
        fade.setToValue(0);
        fade.setOnFinished(e -> backdrop.getChildren().remove(old));
        fade.play();
    }

    public Stage stage() {
        return stage;
    }

    public Toaster toaster() {
        return toaster;
    }

    public void open() {
        stage.show();
    }

    /** Shows a screen; the window title becomes "Lodge Billing Lab  —  subtitle". */
    public void showScreen(Node screen, String subtitle) {
        content.getChildren().setAll(screen);
        stage.setTitle(Ui.LODGE_NAME + "  —  " + subtitle);
    }

    /** True while a receipt, payment, print or other dialog is open over the main window. */
    public boolean hasOpenDialog() {
        return Window.getWindows().stream().anyMatch(w -> w != stage && w.isShowing() && w instanceof Stage);
    }

    public void showFatal(String message) {
        Label title = new Label("Cannot start");
        title.getStyleClass().add("login-heading");
        Label text = new Label(message);
        text.setWrapText(true);
        Button exit = new Button("Exit");
        exit.getStyleClass().add("primary");
        exit.setOnAction(e -> stage.close());
        VBox card = new VBox(16, title, text, exit);
        card.getStyleClass().addAll("card", "login-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(520);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        content.getChildren().setAll(card);
        stage.setTitle(Ui.LODGE_NAME);
    }
}
