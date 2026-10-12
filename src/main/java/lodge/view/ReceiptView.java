package lodge.view;

import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

import lodge.model.Bill;
import lodge.model.Money;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A paper-style receipt that can be viewed and printed (choose "Microsoft Print to PDF" for a PDF). */
public final class ReceiptView {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US);

    private ReceiptView() {
    }

    public static void show(Window owner, Bill bill) {
        VBox receipt = build(bill);
        Dialog<Void> dialog = Ui.styled(new Dialog<>(), owner);
        dialog.setTitle("Receipt " + bill.id());
        ButtonType print = new ButtonType("Print…", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(print, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(receipt);

        Button printButton = (Button) dialog.getDialogPane().lookupButton(print);
        printButton.getStyleClass().add("primary");
        printButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume(); // keep the dialog open
            if (print(receipt.getScene().getWindow(), receipt)) {
                printButton.setText("Printed ✓");
            }
        });
        dialog.setOnShown(e -> Effects.popIn(receipt));
        dialog.showAndWait();
    }

    /** Prints without opening the receipt dialog first. */
    public static boolean printDirect(Window owner, Bill bill) {
        VBox receipt = build(bill);
        Scene offscreen = new Scene(receipt);
        offscreen.getStylesheets().setAll(Theme.current().stylesheets());
        receipt.applyCss();
        receipt.layout();
        return print(owner, receipt);
    }

    private static boolean print(Window owner, Node node) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Ui.warn(owner, "No printer is set up on this computer.");
            return false;
        }
        if (!job.showPrintDialog(owner)) {
            return false;
        }
        PageLayout page = job.getJobSettings().getPageLayout();
        double scale = Math.min(1, Math.min(page.getPrintableWidth() / node.getBoundsInLocal().getWidth(),
                page.getPrintableHeight() / node.getBoundsInLocal().getHeight()));
        Scale fit = new Scale(scale, scale);
        node.getTransforms().add(fit);
        boolean printed = job.printPage(node);
        node.getTransforms().remove(fit);
        return printed && job.endJob();
    }

    static VBox build(Bill b) {
        Label lodge = styled(new Label(Ui.LODGE_NAME), "receipt-title");
        Label kind = styled(new Label("OFFICIAL RECEIPT"), "receipt-muted");
        Label number = styled(new Label("Receipt " + b.id()), "receipt-strong");
        Label issued = styled(new Label("Issued " + STAMP.format(LocalDateTime.now())), "receipt-muted");

        GridPane stay = grid();
        row(stay, 0, "Guest", b.guest());
        row(stay, 1, "Room", b.room() + "  ·  " + Money.format(b.room().rate()) + " / night");
        row(stay, 2, "Check-in", DATE.format(b.checkIn()));
        row(stay, 3, "Check-out", DATE.format(b.checkOut()));
        row(stay, 4, "Nights", String.valueOf(b.nights()));
        if (!b.createdBy().isEmpty()) {
            row(stay, 5, "Served by", b.createdBy());
        }

        GridPane charges = grid();
        int r = 0;
        row(charges, r++, "Room charge", Money.format(b.roomCharge()));
        if (b.discount().signum() > 0) {
            row(charges, r++, "Long-stay discount (5%)", "− " + Money.format(b.discount()));
        }
        row(charges, r++, "Extras", Money.format(b.extras()));
        row(charges, r++, "TOTAL", Money.format(b.total()), "receipt-strong");
        row(charges, r++, "Paid", Money.format(b.paid()));
        if (b.settled()) {
            row(charges, r, "Balance", "Settled in full ✓", "receipt-settled");
        } else {
            row(charges, r, "Balance due", Money.format(b.balance()), "receipt-owing");
        }

        VBox receipt = new VBox(10, lodge, kind, number, issued, separator(), stay, separator(), charges);
        if (!b.notes().isEmpty()) {
            Label notes = styled(new Label("Notes: " + b.notes()), "receipt-muted");
            notes.setWrapText(true);
            receipt.getChildren().addAll(separator(), notes);
        }
        receipt.getChildren().addAll(separator(), styled(new Label("Thank you for staying with us!"), "receipt-thanks"));
        receipt.getStyleClass().add("receipt");
        receipt.setPrefWidth(400);
        return receipt;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(5);
        ColumnConstraints label = new ColumnConstraints();
        label.setHgrow(Priority.ALWAYS);
        ColumnConstraints value = new ColumnConstraints();
        value.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().addAll(label, value);
        return grid;
    }

    private static void row(GridPane grid, int row, String label, String value, String... styleClasses) {
        Label l = new Label(label);
        Label v = new Label(value);
        l.getStyleClass().addAll(styleClasses);
        v.getStyleClass().addAll(styleClasses);
        grid.addRow(row, l, v);
    }

    private static Region separator() {
        Region line = new Region();
        line.getStyleClass().add("receipt-separator");
        return line;
    }

    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        return label;
    }
}
