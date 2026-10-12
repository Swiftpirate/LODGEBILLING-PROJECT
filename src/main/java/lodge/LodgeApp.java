package lodge;

import javafx.application.Application;
import javafx.stage.Stage;
import lodge.controller.AppController;
import lodge.model.AuthService;
import lodge.model.UserStore;

import java.io.IOException;
import java.time.Clock;

/**
 * Entry point. The code is split Model–View–Controller:
 * lodge.model (bills, accounts, rules, files), lodge.view (screens and dialogs) and lodge.controller (what happens
 * when the user acts).
 */
public class LodgeApp extends Application {

    @Override
    public void start(Stage stage) {
        new AppController(stage).start();
    }

    /**
     * Normal start opens the window. Emergency recovery, for a manager who has lost both password and
     * recovery key, run on the lodge PC:  gradlew run --args="--reset-password USERNAME"
     */
    public static void main(String[] args) {
        if (args.length >= 1 && args[0].equals("--reset-password")) {
            System.exit(emergencyReset(args.length >= 2 ? args[1] : ""));
        }
        launch(args);
    }

    private static int emergencyReset(String username) {
        if (username.isBlank()) {
            System.out.println("Usage: gradlew run --args=\"--reset-password USERNAME\"");
            return 2;
        }
        try {
            AuthService auth = new AuthService(new UserStore(UserStore.defaultFile()), Clock.systemDefaultZone());
            String temporary = auth.emergencyReset(username);
            System.out.println();
            System.out.println("  Password reset for \"" + AuthService.normalize(username) + "\".");
            System.out.println("  Temporary password:  " + temporary);
            System.out.println("  Sign in with it; you will then be asked to choose your own password.");
            System.out.println();
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            System.out.println("Could not reset the password: " + e.getMessage());
            return 1;
        }
    }
}
