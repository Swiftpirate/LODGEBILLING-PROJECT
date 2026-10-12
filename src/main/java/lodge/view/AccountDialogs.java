package lodge.view;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lodge.model.Role;
import lodge.model.User;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Change-password, recovery-key and staff-management dialogs. They only collect input and show results;
 * the handlers passed in (from the AccountController) do the work and return an error message, or null on success.
 */
public final class AccountDialogs {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);

    public interface ChangePasswordHandler {
        String submit(String current, String next, String confirm) throws IOException;
    }

    public interface NewStaffHandler {
        String submit(String fullName, String username, Role role, String password, String confirm) throws IOException;
    }

    public interface ResetPasswordHandler {
        String submit(String password, String confirm) throws IOException;
    }

    /** The buttons of the staff dialog. Each returns true if the staff list changed. */
    public interface StaffActions {
        List<User> users();

        boolean add(Window owner);

        boolean resetPassword(Window owner, User target);

        boolean toggleRole(Window owner, User target);

        boolean remove(Window owner, User target);
    }

    /** Performs the action behind a form; returns null on success or an error message to show. */
    private interface Action {
        String run() throws IOException;
    }

    private AccountDialogs() {
    }

    public static boolean changePassword(Window owner, User user, ChangePasswordHandler handler) {
        PasswordField current = new PasswordField();
        PasswordField next = new PasswordField();
        next.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        GridPane form = form(List.of("Current password", "New password", "Confirm new password"),
                List.of(current, next, confirm));
        return showForm(owner, "Change password", "Change the password for " + user.username(), form,
                "Change password", () -> handler.submit(current.getText(), next.getText(), confirm.getText()));
    }

    /** Shows a freshly made recovery key, once. */
    public static void showRecoveryKey(Window owner, User user, String key) {
        Label who = new Label("Username:  " + user.username());
        who.getStyleClass().add("username-reminder");
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("recovery-key");
        keyLabel.setMaxWidth(Double.MAX_VALUE);
        keyLabel.setAlignment(Pos.CENTER);
        Button copy = new Button("Copy key");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(key);
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText("Copied ✓");
        });
        Label tip = new Label("Write it down and keep it away from the computer. If you forget your password, "
                + "use \"Forgot your password?\" on the sign-in screen.");
        tip.getStyleClass().add("hint");
        tip.setWrapText(true);
        tip.setMaxWidth(420);
        tip.setMinHeight(Region.USE_PREF_SIZE);

        Dialog<ButtonType> dialog = Ui.styled(new Dialog<>(), owner);
        dialog.setTitle("Recovery key");
        dialog.setHeaderText("Your new recovery key");
        dialog.getDialogPane().setContent(new VBox(12, who, keyLabel, copy, tip));
        ButtonType done = new ButtonType("I have written it down", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(done);
        ((Button) dialog.getDialogPane().lookupButton(done)).getStyleClass().add("primary");
        dialog.showAndWait();
    }

    public static void manageStaff(Window owner, StaffActions actions) {
        TableView<User> table = new TableView<>();
        table.getColumns().setAll(List.of(
                column("Username", User::username, 120),
                column("Full name", User::fullName, 190),
                column("Role", u -> u.role().toString(), 110),
                column("Since", u -> DATE.format(u.createdAt()), 110)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefSize(620, 300);
        Runnable refresh = () -> table.getItems().setAll(actions.users());
        refresh.run();

        ReadOnlyObjectProperty<User> selected = table.getSelectionModel().selectedItemProperty();
        Button add = new Button("Add staff…");
        add.getStyleClass().add("primary");
        Button reset = new Button("Reset password…");
        Button role = new Button();
        role.textProperty().bind(Bindings.createStringBinding(() -> selected.get() == null ? "Change role"
                : selected.get().isManager() ? "Make receptionist" : "Make manager", selected));
        Button remove = new Button("Remove");
        remove.getStyleClass().add("danger");
        reset.disableProperty().bind(selected.isNull());
        role.disableProperty().bind(selected.isNull());
        remove.disableProperty().bind(selected.isNull());
        Effects.hoverPop(add, reset, role, remove);

        Label hint = new Label("Managers can edit and delete bills and manage staff. "
                + "Receptionists can create bills, take payments, print receipts and view reports.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        hint.setMaxWidth(620);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(12, hint, table, new HBox(10, add, reset, role, remove));

        Dialog<Void> dialog = Ui.styled(new Dialog<>(), owner);
        dialog.setTitle("Manage staff");
        dialog.setHeaderText("Staff accounts");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Supplier<Window> here = () -> dialog.getDialogPane().getScene().getWindow();
        add.setOnAction(e -> {
            if (actions.add(here.get())) {
                refresh.run();
            }
        });
        reset.setOnAction(e -> {
            if (actions.resetPassword(here.get(), selected.get())) {
                refresh.run();
            }
        });
        role.setOnAction(e -> {
            if (actions.toggleRole(here.get(), selected.get())) {
                refresh.run();
            }
        });
        remove.setOnAction(e -> {
            if (actions.remove(here.get(), selected.get())) {
                refresh.run();
            }
        });
        dialog.showAndWait();
    }

    public static boolean addStaff(Window owner, NewStaffHandler handler) {
        TextField fullName = new TextField();
        TextField username = new TextField();
        username.setPromptText("3–20 letters/numbers, e.g. john");
        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setValue(Role.RECEPTIONIST);
        role.setMaxWidth(Double.MAX_VALUE);
        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        GridPane form = form(List.of("Full name", "Username", "Role", "Temporary password", "Confirm password"),
                List.of(fullName, username, role, password, confirm));
        Label note = new Label("Give them this temporary password. They will be asked to choose their own "
                + "the first time they sign in.");
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.getStyleClass().add("hint");
        form.add(note, 0, 5, 2, 1);
        return showForm(owner, "Add staff", "Create a staff account", form, "Create account", () ->
                handler.submit(fullName.getText(), username.getText(), role.getValue(),
                        password.getText(), confirm.getText()));
    }

    public static boolean resetPassword(Window owner, User target, ResetPasswordHandler handler) {
        PasswordField password = new PasswordField();
        password.setPromptText("At least 8 characters, letters and numbers");
        PasswordField confirm = new PasswordField();
        GridPane form = form(List.of("New password", "Confirm password"), List.of(password, confirm));
        return showForm(owner, "Reset password",
                "Set a new password for " + target.fullName() + " (" + target.username() + ")", form,
                "Reset password", () -> handler.submit(password.getText(), confirm.getText()));
    }

    /** A dialog that stays open, showing the error, until the action succeeds or the user cancels. */
    private static boolean showForm(Window owner, String title, String header, Node form, String okLabel,
                                    Action action) {
        Label error = Ui.errorLabel();
        error.setMaxWidth(420);
        Dialog<ButtonType> dialog = Ui.styled(new Dialog<>(), owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType ok = new ButtonType(okLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        VBox content = new VBox(12, form, error);
        dialog.getDialogPane().setContent(content);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ok);
        okButton.getStyleClass().add("primary");
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            String problem;
            try {
                problem = action.run();
            } catch (IllegalArgumentException ex) {
                problem = ex.getMessage();
            } catch (IOException ex) {
                problem = "Could not save: " + ex.getMessage();
            }
            if (problem != null) {
                error.setText(problem);
                Effects.shake(content);
                e.consume(); // keep the dialog open
            }
        });
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ok;
    }

    private static GridPane form(List<String> labels, List<? extends Node> fields) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        for (int i = 0; i < labels.size(); i++) {
            Label label = new Label(labels.get(i));
            label.getStyleClass().add("form-label");
            Node field = fields.get(i);
            if (field instanceof Control control) {
                control.setPrefWidth(280);
            }
            grid.addRow(i, label, field);
        }
        return grid;
    }

    private static TableColumn<User, String> column(String title, Function<User, String> getter, double width) {
        TableColumn<User, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new ReadOnlyStringWrapper(getter.apply(d.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
