package lodge.controller;

import javafx.animation.PauseTransition;
import javafx.stage.Stage;
import javafx.util.Duration;
import lodge.model.AuthService;
import lodge.model.User;
import lodge.model.UserStore;
import lodge.view.AppWindow;
import lodge.view.Toaster;

import java.io.IOException;
import java.time.Clock;

/** Sign-in first, then the billing screen; signs out again after inactivity. */
public final class AppController {

    private static final Duration IDLE_LIMIT = Duration.minutes(15);

    private final PauseTransition idleTimer = new PauseTransition(IDLE_LIMIT);
    private final AppWindow window;
    private AuthService auth;
    private LoginController login;
    private MainController main;

    public AppController(Stage stage) {
        window = new AppWindow(stage, () -> {
            if (main != null) {
                idleTimer.playFromStart();
            }
        });
        idleTimer.setOnFinished(e -> signOutForInactivity());
    }

    public void start() {
        try {
            auth = new AuthService(new UserStore(UserStore.defaultFile()), Clock.systemDefaultZone());
            showLogin(null);
        } catch (IOException e) {
            // Never fall back to "first run" here: that would let anyone create a new manager account
            window.showFatal("The staff accounts file could not be read (" + e.getMessage() + ").\n\n"
                    + "For security the app will not start until it is repaired or restored from a backup:\n"
                    + UserStore.defaultFile());
        }
        window.open();
    }

    private void showLogin(String notice) {
        idleTimer.stop();
        if (main != null) {
            main.dispose();
            main = null;
        }
        login = new LoginController(auth, this::showMain);
        window.showScreen(login.node(), "Sign in");
        login.playEntrance();
        if (notice != null) {
            window.toaster().show(notice, Toaster.Kind.INFO);
        }
    }

    private void showMain(User user) {
        if (login != null) {
            login.dispose();
            login = null;
        }
        main = new MainController(window.stage(), user, auth, window.toaster(),
                () -> showLogin("Signed out. See you next time!"));
        window.showScreen(main.node(), user.fullName() + " (" + user.role() + ")");
        main.onShown();
        idleTimer.playFromStart();
    }

    private void signOutForInactivity() {
        if (main == null) {
            return;
        }
        // Don't pull the screen away from under an open dialog (receipt, payment, print…); wait for it to close
        if (window.hasOpenDialog()) {
            idleTimer.playFromStart();
            return;
        }
        showLogin("You were signed out after " + (int) IDLE_LIMIT.toMinutes() + " minutes without activity.");
    }
}
