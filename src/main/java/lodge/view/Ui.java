package lodge.view;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import lodge.model.Money;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared bits of UI: the app name, the stylesheet, money inputs and themed dialogs. */
public final class Ui {

    public static final String LODGE_NAME = "Lodge Billing Lab";

    public static final String STYLESHEET = Objects.requireNonNull(
            Ui.class.getResource("styles.css"), "styles.css is missing").toExternalForm();

    /** Digits with at most two decimals; anything else is refused as it is typed. */
    private static final Pattern MONEY = Pattern.compile("\\d{0,9}(\\.\\d{0,2})?");

    private Ui() {
    }

    static TextField moneyField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setTextFormatter(new TextFormatter<String>(change ->
                MONEY.matcher(change.getControlNewText()).matches() ? change : null));
        return field;
    }

    static BigDecimal parseOrZero(TextField field) {
        String text = field.getText().trim();
        return text.isEmpty() || text.equals(".") ? Money.ZERO : Money.parse(text);
    }

    static <D extends Dialog<?>> D styled(D dialog, Window owner) {
        dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(Theme.current().stylesheets());
        pane.getStyleClass().add("lodge-dialog");
        return dialog;
    }

    static Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setMinHeight(Region.USE_PREF_SIZE);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        return error;
    }

    public static void warn(Window owner, String message) {
        Alert alert = styled(new Alert(Alert.AlertType.WARNING, message), owner);
        alert.setTitle(LODGE_NAME);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void info(Window owner, String title, String message) {
        Alert alert = styled(new Alert(Alert.AlertType.INFORMATION, message), owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** Yes/Cancel question where Cancel is the default, so a stray Enter never confirms. */
    public static boolean confirm(Window owner, String header, String message, String yesLabel) {
        ButtonType yes = new ButtonType(yesLabel, ButtonBar.ButtonData.OK_DONE);
        Alert alert = styled(new Alert(Alert.AlertType.CONFIRMATION, message, yes, ButtonType.CANCEL), owner);
        alert.setTitle("Please confirm");
        alert.setHeaderText(header);
        Button yesButton = (Button) alert.getDialogPane().lookupButton(yes);
        yesButton.setDefaultButton(false);
        yesButton.getStyleClass().add("primary");
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == yes;
    }
}
