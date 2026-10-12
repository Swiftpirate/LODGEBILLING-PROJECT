package lodge.controller;

import javafx.application.Platform;
import javafx.scene.Node;
import lodge.model.AuthService;
import lodge.model.Role;
import lodge.model.User;
import lodge.view.LoginView;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Runs the sign-in screen: first-run setup, sign in, recovery, temporary passwords and recovery keys. */
public final class LoginController implements LoginView.Actions {

    private final AuthService auth;
    private final Consumer<User> onSignedIn;
    private final LoginView view;

    public LoginController(AuthService auth, Consumer<User> onSignedIn) {
        this.auth = auth;
        this.onSignedIn = onSignedIn;
        view = new LoginView(this);
        if (auth.needsSetup()) {
            view.showSetup();
        } else {
            view.showSignIn("");
        }
    }

    public Node node() {
        return view.node();
    }

    public void playEntrance() {
        view.playEntrance();
    }

    public void dispose() {
        view.dispose();
    }

    // ===================================================================== Sign in

    @Override
    public void signIn(String username, char[] password) {
        if (username.isBlank() || password.length == 0) {
            view.fail("Enter your username and password.");
            return;
        }
        view.busy("Signing in…");
        // Hashing deliberately takes ~0.2 s; do it off the UI thread so the animations keep running
        CompletableFuture.supplyAsync(() -> auth.login(username, password)).whenComplete((result, failure) -> {
            Arrays.fill(password, '\0');
            Platform.runLater(() -> {
                view.idle();
                if (failure != null) {
                    view.passwordRejected("Sign-in failed: " + failure.getMessage());
                } else if (result.ok()) {
                    proceed(result.user());
                } else {
                    view.passwordRejected(result.error()
                            + "\nUsernames are lower case with no spaces — not your full name.");
                }
            });
        });
    }

    /** After any successful sign-in: replace a temporary password, make sure managers have a recovery key. */
    private void proceed(User signedIn) {
        User user = auth.find(signedIn.username()).orElse(signedIn);
        if (user.mustChangePassword()) {
            view.showNewPassword(user);
            return;
        }
        if (user.isManager() && !user.hasRecoveryKey()) {
            try {
                String key = auth.issueRecoveryKey(user);
                view.showRecoveryKey(user, key, "Save your recovery key",
                        "If you ever forget your password, this key lets you set a new one from the sign-in "
                                + "screen (Forgot your password?). It is shown only this once.");
                return;
            } catch (IOException e) {
                // Not fatal: they can create one later from Account → New recovery key
            }
        }
        finish(user);
    }

    @Override
    public void continueToApp(User user) {
        finish(auth.find(user.username()).orElse(user));
    }

    private void finish(User user) {
        view.leave(() -> onSignedIn.accept(user));
    }

    // ===================================================================== First run

    @Override
    public void createManager(String fullName, String username, String password, String confirm) {
        if (!password.equals(confirm)) {
            view.fail("The two passwords do not match.");
            return;
        }
        char[] secret = password.toCharArray();
        try {
            proceed(auth.createUser(username, fullName, Role.MANAGER, secret));
        } catch (IllegalArgumentException e) {
            view.fail(e.getMessage());
        } catch (IOException e) {
            view.fail("Could not save the account: " + e.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    // ===================================================================== Forgot password

    @Override
    public void forgotPassword(String username) {
        view.showRecover(username);
    }

    @Override
    public void backToSignIn(String username) {
        view.showSignIn(username);
    }

    /** Outcome of a recovery attempt, worked out off the UI thread. */
    private record Recovery(AuthService.LoginResult result, String freshKey) {
    }

    @Override
    public void recover(String username, String recoveryKey, String password, String confirm) {
        if (username.isBlank() || recoveryKey.isBlank()) {
            view.fail("Enter your username and recovery key.");
            return;
        }
        if (!password.equals(confirm)) {
            view.fail("The two new passwords do not match.");
            return;
        }
        char[] secret = password.toCharArray();
        view.busy("Checking…");
        CompletableFuture.supplyAsync(() -> {
            try {
                AuthService.LoginResult result = auth.recover(username, recoveryKey, secret);
                // Hand out a fresh key straight away: the old one is now used up
                return new Recovery(result, result.ok() ? auth.issueRecoveryKey(result.user()) : null);
            } catch (IOException e) {
                throw new IllegalStateException("Could not save: " + e.getMessage(), e);
            }
        }).whenComplete((out, failure) -> {
            Arrays.fill(secret, '\0');
            Platform.runLater(() -> {
                view.idle();
                if (failure != null) {
                    Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                    view.fail(cause.getMessage());
                    return;
                }
                if (!out.result().ok()) {
                    view.fail(out.result().error());
                    return;
                }
                view.showRecoveryKey(out.result().user(), out.freshKey(), "Password reset ✓",
                        "Your old recovery key is now used up. Here is a new one — write it down "
                                + "and keep it somewhere safe.");
            });
        });
    }

    // ===================================================================== Temporary password → own password

    @Override
    public void choosePassword(User user, String password, String confirm) {
        if (!password.equals(confirm)) {
            view.fail("The two passwords do not match.");
            return;
        }
        char[] secret = password.toCharArray();
        try {
            proceed(auth.replaceTemporaryPassword(user, secret));
        } catch (IllegalArgumentException e) {
            view.fail(e.getMessage());
        } catch (IOException e) {
            view.fail("Could not save: " + e.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @Override
    public void cancelNewPassword() {
        view.showSignIn("");
    }
}
