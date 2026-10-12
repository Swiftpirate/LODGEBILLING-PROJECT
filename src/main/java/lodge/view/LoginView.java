package lodge.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import lodge.model.AuthService;
import lodge.model.PasswordHasher;
import lodge.model.User;

/**
 * Everything before the billing screen, as a series of cards:
 * first-run setup, sign in, forgot password (recovery key), choose a new password, save your recovery key.
 * It only displays; every button is handed to the Actions (the LoginController).
 */
public final class LoginView {

    /** What the user asked for on the current card. */
    public interface Actions {
        void signIn(String username, char[] password);

        void createManager(String fullName, String username, String password, String confirm);

        void forgotPassword(String username);

        void recover(String username, String recoveryKey, String password, String confirm);

        void backToSignIn(String username);

        void choosePassword(User user, String password, String confirm);

        void cancelNewPassword();

        void continueToApp(User user);
    }

    private final Actions actions;
    private final StackPane stack = new StackPane();
    private final ScrollPane root = new ScrollPane(stack);
    private final DropShadow glow = new DropShadow(12, Theme.current().glow(0.55));
    private final Timeline glowPulse;
    private boolean entered;

    // The card on screen and the parts of it the controller can update
    private VBox card;
    private Node firstField;
    private Label error;
    private Button primary;
    private String primaryText;
    private PasswordField password;

    public LoginView(Actions actions) {
        this.actions = actions;
        glowPulse = new Timeline(new KeyFrame(Duration.seconds(2.4),
                new KeyValue(glow.radiusProperty(), 32, Interpolator.EASE_BOTH)));
        glowPulse.setAutoReverse(true);
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.play();
        glow.colorProperty().bind(Bindings.createObjectBinding(
                () -> Theme.current().glow(0.55), Theme.currentProperty()));

        stack.setPadding(new Insets(24));
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.getStyleClass().add("page-scroller");
    }

    public Node node() {
        return root;
    }

    public void playEntrance() {
        entered = true;
        Effects.entrance(card, 80).play();
        Platform.runLater(firstField::requestFocus);
    }

    public void dispose() {
        glowPulse.stop();
    }

    // ===================================================================== Feedback on the current card

    /** Shows a problem under the form and shakes the card. */
    public void fail(String message) {
        error.setText(message);
        Effects.shake(card);
    }

    /** Wrong password (or sign-in failed): empties the password box and puts the cursor back in it. */
    public void passwordRejected(String message) {
        password.clear();
        error.setText(message);
        password.requestFocus();
        Effects.shake(card);
    }

    /** Disables the main button while work runs in the background. */
    public void busy(String text) {
        primary.setDisable(true);
        primary.setText(text);
        error.setText("");
    }

    public void idle() {
        primary.setDisable(false);
        primary.setText(primaryText);
    }

    /** Leaves with a little zoom-and-fade, then runs then (which hands over to the billing screen). */
    public void leave(Runnable then) {
        card.setDisable(true);
        FadeTransition fade = new FadeTransition(Duration.millis(280), card);
        fade.setToValue(0);
        ScaleTransition zoom = new ScaleTransition(Duration.millis(280), card);
        zoom.setToX(1.06);
        zoom.setToY(1.06);
        ParallelTransition out = new ParallelTransition(fade, zoom);
        out.setOnFinished(e -> {
            dispose();
            then.run();
        });
        out.play();
    }

    // ===================================================================== Sign in

    public void showSignIn(String presetUsername) {
        TextField username = new TextField(presetUsername);
        username.setPromptText("Your username (lower case, e.g. mary)");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        PasswordBox passwordBox = new PasswordBox(pass);

        Label caps = warning("Caps Lock is on");
        watchCapsLock(caps, pass, passwordBox.plain);
        Label err = Ui.errorLabel();

        Button signIn = primaryButton("Sign in");
        signIn.setOnAction(e -> actions.signIn(username.getText(), pass.getText().toCharArray()));

        Hyperlink forgot = new Hyperlink("Forgot your password?");
        forgot.setOnAction(e -> actions.forgotPassword(username.getText()));
        HBox links = new HBox(forgot);
        links.setAlignment(Pos.CENTER);

        show(new VBox(22, brand("Staff sign in", "Sign in to manage guest bills."),
                        new VBox(8, fieldLabel("Username"), username, fieldLabel("Password"), passwordBox.node,
                                passwordBox.toggle, caps, err),
                        new VBox(10, signIn, links)),
                presetUsername.isEmpty() ? username : pass, err, signIn, pass);
    }

    // ===================================================================== First run

    public void showSetup() {
        TextField fullName = new TextField();
        fullName.setPromptText("e.g. Mary Banda");
        TextField username = new TextField();
        username.setPromptText("Short, lower case, no spaces — e.g. mary");
        Label usernamePreview = new Label(" ");
        usernamePreview.getStyleClass().add("hint");
        username.textProperty().addListener((obs, old, text) -> usernamePreview.setText(text.isBlank() ? " "
                : "You will sign in as:  " + AuthService.normalize(text)));
        PasswordField pass = new PasswordField();
        pass.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Type the password again");

        Label caps = warning("Caps Lock is on");
        watchCapsLock(caps, pass, confirm);
        Label err = Ui.errorLabel();

        Button create = primaryButton("Create manager account");
        create.setOnAction(e -> actions.createManager(fullName.getText(), username.getText(),
                pass.getText(), confirm.getText()));

        show(new VBox(18,
                        brand("Welcome! Let's set up the lodge",
                                "Create the manager account. Managers can add receptionists later from Account → Manage staff."),
                        new VBox(7,
                                fieldLabel("Your full name"), fullName,
                                fieldLabel("Choose a username (used to sign in)"), username, usernamePreview,
                                fieldLabel("Password"), pass, strengthMeter(pass),
                                fieldLabel("Confirm password"), confirm, caps, err),
                        create),
                fullName, err, create, pass);
    }

    // ===================================================================== Forgot password

    public void showRecover(String presetUsername) {
        TextField username = new TextField(presetUsername);
        username.setPromptText("Your username");
        TextField recoveryKey = new TextField();
        recoveryKey.setPromptText("XXXX-XXXX-XXXX-XXXX");
        recoveryKey.getStyleClass().add("recovery-input");
        PasswordField pass = new PasswordField();
        pass.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Type the new password again");
        Label caps = warning("Caps Lock is on");
        watchCapsLock(caps, pass, confirm);
        Label err = Ui.errorLabel();

        Button reset = primaryButton("Reset password");
        reset.setOnAction(e -> actions.recover(username.getText(), recoveryKey.getText(),
                pass.getText(), confirm.getText()));

        Hyperlink back = new Hyperlink("← Back to sign in");
        back.setOnAction(e -> actions.backToSignIn(username.getText()));
        HBox links = new HBox(back);
        links.setAlignment(Pos.CENTER);

        Label note = new Label("Receptionists don't have recovery keys: ask a manager to reset your password.");
        note.getStyleClass().add("hint");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);

        show(new VBox(18,
                        brand("Forgot your password?", "Use the recovery key you saved when the account was set up."),
                        new VBox(7,
                                fieldLabel("Username"), username,
                                fieldLabel("Recovery key"), recoveryKey,
                                fieldLabel("New password"), pass, strengthMeter(pass),
                                fieldLabel("Confirm new password"), confirm, caps, err),
                        new VBox(10, reset, note, links)),
                presetUsername.isBlank() ? username : recoveryKey, err, reset, pass);
    }

    // ===================================================================== Temporary password → own password

    public void showNewPassword(User user) {
        PasswordField pass = new PasswordField();
        pass.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Type the new password again");
        Label caps = warning("Caps Lock is on");
        watchCapsLock(caps, pass, confirm);
        Label err = Ui.errorLabel();

        Button save = primaryButton("Save and continue");
        save.setOnAction(e -> actions.choosePassword(user, pass.getText(), confirm.getText()));
        Hyperlink cancel = new Hyperlink("Cancel and sign out");
        cancel.setOnAction(e -> actions.cancelNewPassword());
        HBox links = new HBox(cancel);
        links.setAlignment(Pos.CENTER);

        show(new VBox(18,
                        brand("Choose your own password",
                                "Hello " + user.fullName() + " (" + user.username() + "). You signed in with a temporary "
                                        + "password — pick a new one that only you know."),
                        new VBox(7, fieldLabel("New password"), pass, strengthMeter(pass),
                                fieldLabel("Confirm new password"), confirm, caps, err),
                        new VBox(10, save, links)),
                pass, err, save, pass);
    }

    // ===================================================================== Show a recovery key once

    public void showRecoveryKey(User user, String recoveryKey, String heading, String explanation) {
        Label who = new Label("Your username:  " + user.username());
        who.getStyleClass().add("username-reminder");

        Label keyLabel = new Label(recoveryKey);
        keyLabel.getStyleClass().add("recovery-key");
        keyLabel.setMaxWidth(Double.MAX_VALUE);
        keyLabel.setAlignment(Pos.CENTER);

        Button copy = new Button("Copy key");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(recoveryKey);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText("Copied ✓");
        });
        Effects.hoverPop(copy);
        HBox copyRow = new HBox(copy);
        copyRow.setAlignment(Pos.CENTER);

        CheckBox written = new CheckBox("I have written down my username and recovery key");
        written.setWrapText(true);
        Button proceed = primaryButton("Continue to the app");
        proceed.disableProperty().bind(written.selectedProperty().not());
        proceed.setOnAction(e -> actions.continueToApp(user));

        Label tip = new Label("Keep it on paper, away from the computer. Anyone with this key can reset your password.");
        tip.getStyleClass().add("hint");
        tip.setWrapText(true);
        tip.setMinHeight(Region.USE_PREF_SIZE);

        show(new VBox(18, brand(heading, explanation),
                        new VBox(12, who, keyLabel, copyRow, tip, written),
                        proceed),
                written, Ui.errorLabel(), proceed, null);
    }

    // ===================================================================== Shared pieces

    private void show(VBox next, Node first, Label err, Button button, PasswordField pass) {
        next.getStyleClass().addAll("card", "login-card");
        next.setPrefWidth(440);
        next.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        next.setAlignment(Pos.TOP_CENTER);
        card = next;
        firstField = first;
        error = err;
        primary = button;
        primaryText = button.getText();
        password = pass;
        stack.getChildren().setAll(next);
        if (entered) {
            Effects.popIn(next);
            Platform.runLater(firstField::requestFocus);
        }
    }

    private VBox brand(String heading, String subheading) {
        Label crest = new Label("✦");
        crest.getStyleClass().add("login-crest");
        Label title = new Label(Ui.LODGE_NAME);
        title.getStyleClass().add("login-title");
        title.setEffect(glow);
        Label head = new Label(heading);
        head.getStyleClass().add("login-heading");
        head.setWrapText(true);
        head.setTextAlignment(TextAlignment.CENTER);
        Label sub = new Label(subheading);
        sub.getStyleClass().add("hint");
        sub.setWrapText(true);
        sub.setTextAlignment(TextAlignment.CENTER);
        sub.setMinHeight(Region.USE_PREF_SIZE);
        VBox box = new VBox(4, crest, title, head, sub);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("primary", "login-button");
        button.setDefaultButton(true); // Enter submits the card
        button.setMaxWidth(Double.MAX_VALUE);
        Effects.hoverPop(button);
        return button;
    }

    private static VBox strengthMeter(PasswordField password) {
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("strength-bar");
        Label label = new Label(" ");
        label.getStyleClass().add("hint");
        password.textProperty().addListener((obs, old, text) -> {
            int strength = PasswordHasher.strength(text.toCharArray());
            bar.setProgress(strength / 3.0);
            bar.getStyleClass().removeAll("strength-1", "strength-2", "strength-3");
            if (strength > 0) {
                bar.getStyleClass().add("strength-" + strength);
            }
            label.setText(switch (strength) {
                case 0 -> " ";
                case 1 -> "Too weak — " + PasswordHasher.weakness(text.toCharArray());
                case 2 -> "Good. Longer, with capitals or symbols, is even stronger.";
                default -> "Strong password ✓";
            });
        });
        return new VBox(4, bar, label);
    }

    /** Password field with a "Show password" checkbox that swaps in a plain text field. */
    private static final class PasswordBox {
        final TextField plain = new TextField();
        final CheckBox toggle = new CheckBox("Show password");
        final StackPane node;

        PasswordBox(PasswordField password) {
            plain.setPromptText(password.getPromptText());
            plain.textProperty().bindBidirectional(password.textProperty());
            plain.setVisible(false);
            toggle.selectedProperty().addListener((obs, was, on) -> {
                plain.setVisible(on);
                password.setVisible(!on);
                (on ? plain : password).requestFocus();
                (on ? plain : password).end();
            });
            node = new StackPane(password, plain);
        }
    }

    private static void watchCapsLock(Label warning, Node... fields) {
        for (Node field : fields) {
            field.addEventHandler(KeyEvent.KEY_RELEASED, e -> updateCaps(warning));
            field.focusedProperty().addListener((obs, was, focused) -> updateCaps(warning));
        }
    }

    private static void updateCaps(Label warning) {
        warning.setVisible(Platform.isKeyLocked(KeyCode.CAPS).orElse(false));
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label warning(String text) {
        Label label = new Label("⚠  " + text);
        label.getStyleClass().add("caps-warning");
        label.setVisible(false);
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }
}
