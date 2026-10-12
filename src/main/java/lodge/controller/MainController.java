package lodge.controller;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.stage.Stage;
import lodge.model.AuthService;
import lodge.model.Bill;
import lodge.model.BillBook;
import lodge.model.BillCalculator;
import lodge.model.BillStore;
import lodge.model.Money;
import lodge.model.User;
import lodge.view.MainView;
import lodge.view.ReceiptView;
import lodge.view.Toaster;
import lodge.view.Ui;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Runs the signed-in billing screen. One instance per sign-in; dispose() it on sign-out. */
public final class MainController implements MainView.Actions {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);

    private final Stage stage;
    private final User user;
    private final AuthService auth;
    private final Toaster toaster;
    private final Runnable onSignOut;
    private final BillBook book = new BillBook(new BillStore(BillStore.defaultFile()));
    private final String loadProblem;
    private final MainView view;
    private Bill editing; // the bill loaded into the form for editing, or null for a new bill

    public MainController(Stage stage, User user, AuthService auth, Toaster toaster, Runnable onSignOut) {
        this.stage = stage;
        this.user = user;
        this.auth = auth;
        this.toaster = toaster;
        this.onSignOut = onSignOut;
        loadProblem = book.load();
        book.setOnSaveFailed(e -> toaster.show("Could not save bills: " + e.getMessage(), Toaster.Kind.ERROR));
        view = new MainView(stage, user, book.bills(), this);
        book.bills().addListener((ListChangeListener<Bill>) change -> refreshStats());
    }

    public Node node() {
        return view.node();
    }

    /** Call once the view is on screen: starts the entrance animations and greets the user. */
    public void onShown() {
        newBill();
        refreshStats();
        view.playIntro();
        Platform.runLater(() -> {
            if (loadProblem != null) {
                Ui.warn(stage, loadProblem);
            }
            String firstName = user.fullName().split("\\s+")[0];
            toaster.show("Welcome, " + firstName + "  ·  signed in as " + user.role(), Toaster.Kind.SUCCESS);
        });
    }

    public void dispose() {
        view.dispose();
    }

    // ===================================================================== Bills

    @Override
    public void newBill() {
        editing = null;
        view.showNewForm();
        formChanged();
    }

    @Override
    public void saveBill() {
        MainView.BillForm form = view.form();
        String guest = form.guest();
        if (guest.isEmpty()) {
            view.showGuestError("Please enter the guest name.");
            return;
        }
        BillCalculator.Breakdown b = BillCalculator.calculate(form.room(), form.nights(), form.extras());
        BigDecimal paid = form.paid();

        if (paid.compareTo(b.total()) > 0) {
            BigDecimal change = paid.subtract(b.total());
            if (!Ui.confirm(stage, "Amount paid is more than the bill",
                    "The guest paid " + Money.format(paid) + " but the bill is " + Money.format(b.total())
                            + ".\n\nRecord " + Money.format(b.total()) + " as paid and give "
                            + Money.format(change) + " change?",
                    "Record and give change")) {
                return;
            }
            paid = b.total();
        }
        if (editing == null && book.isDuplicate(guest, form.room(), form.checkIn())) {
            if (!Ui.confirm(stage, "This looks like a duplicate",
                    "A " + form.room() + " bill for " + guest + " checking in on " + LONG_DATE.format(form.checkIn())
                            + " already exists.\n\nSave another one anyway?",
                    "Save anyway")) {
                return;
            }
        }

        String id = editing != null ? editing.id() : book.nextId();
        LocalDateTime created = editing != null ? editing.createdAt() : LocalDateTime.now();
        String createdBy = editing != null ? editing.createdBy() : user.username();
        Bill bill = new Bill(id, guest, form.room(), form.checkIn(), form.nights(), b.roomCharge(), b.discount(),
                b.extras(), b.total(), paid, form.notes(), created, createdBy);
        view.markFresh(bill);
        boolean justSettled = bill.settled() && (editing == null || !editing.settled());
        if (editing != null && book.replace(editing, bill)) {
            toaster.show("Updated " + id + " for " + guest, Toaster.Kind.SUCCESS);
        } else {
            book.add(bill);
            toaster.show("Saved " + id + " for " + guest + "  ·  balance " + Money.format(bill.balance()),
                    Toaster.Kind.SUCCESS);
        }
        newBill();
        view.select(bill, true);
        if (justSettled) {
            view.celebrate();
        }
    }

    @Override
    public void editSelected() {
        if (!requireManager("edit saved bills")) {
            return;
        }
        Bill b = selectedOrWarn("Select a bill to edit first.");
        if (b == null) {
            return;
        }
        editing = b;
        view.showEditForm(b);
    }

    @Override
    public void recordPayment() {
        Bill b = selectedOrWarn("Select a bill to record a payment against.");
        if (b == null) {
            return;
        }
        if (b.settled()) {
            toaster.show(b.id() + " is already settled", Toaster.Kind.INFO);
            return;
        }
        Optional<BigDecimal> result = view.askPayment(b);
        if (result.isEmpty()) {
            return;
        }
        BigDecimal amount = result.get();
        BigDecimal change = amount.subtract(b.balance());
        if (change.signum() > 0) {
            amount = b.balance();
        }
        Bill updated = b.withPayment(amount);
        view.markFresh(updated);
        book.replace(b, updated);
        view.select(updated, false);
        if (updated.settled()) {
            view.celebrate();
        }
        toaster.show("Recorded " + Money.format(amount) + " for " + b.id()
                        + (change.signum() > 0 ? "  ·  give " + Money.format(change) + " change" : "")
                        + (updated.settled() ? "  ·  fully settled" : ""),
                Toaster.Kind.SUCCESS);
    }

    @Override
    public void deleteBill() {
        if (!requireManager("delete bills")) {
            return;
        }
        Bill b = selectedOrWarn("Select a bill to delete first.");
        if (b == null) {
            return;
        }
        if (!Ui.confirm(stage, "Delete bill " + b.id() + "?",
                "This permanently removes the bill for " + b.guest() + " (" + Money.format(b.total()) + ").",
                "Delete")) {
            return;
        }
        book.remove(b);
        if (b == editing) {
            newBill();
        }
        toaster.show("Deleted " + b.id(), Toaster.Kind.INFO);
    }

    @Override
    public void viewReceipt() {
        Bill b = selectedOrWarn("Select a bill to view its receipt.");
        if (b != null) {
            ReceiptView.show(stage, b);
        }
    }

    @Override
    public void printReceipt() {
        Bill b = selectedOrWarn("Select a bill to print its receipt.");
        if (b != null && ReceiptView.printDirect(stage, b)) {
            toaster.show("Receipt " + b.id() + " sent to the printer", Toaster.Kind.SUCCESS);
        }
    }

    @Override
    public void exportCsv() {
        File file = view.chooseExportFile();
        if (file == null) {
            return;
        }
        try {
            book.exportTo(file.toPath());
            toaster.show("Exported " + book.bills().size() + " bill(s) to " + file.getName(), Toaster.Kind.SUCCESS);
        } catch (IOException e) {
            Ui.warn(stage, "Could not export the bills:\n" + e.getMessage());
        }
    }

    /** Recalculates the live bill from what is typed in the form. */
    @Override
    public void formChanged() {
        if (view == null) {
            return; // still being built
        }
        MainView.BillForm form = view.form();
        if (form.room() == null || form.checkIn() == null) {
            return;
        }
        view.showLiveBill(form.checkIn().plusDays(form.nights()),
                BillCalculator.calculate(form.room(), form.nights(), form.extras()), form.paid());
    }

    private void refreshStats() {
        view.showTotals(book.totals());
    }

    // ===================================================================== Session and account

    @Override
    public void signOut() {
        if (hasUnsavedForm() && !Ui.confirm(stage, "Sign out?",
                "The bill you are typing has not been saved and will be lost.", "Sign out anyway")) {
            return;
        }
        onSignOut.run();
    }

    @Override
    public void exit() {
        if (!hasUnsavedForm() || Ui.confirm(stage, "Exit?",
                "The bill you are typing has not been saved.", "Exit anyway")) {
            stage.close();
        }
    }

    @Override
    public void changePassword() {
        if (account().changePassword()) {
            toaster.show("Your password has been changed", Toaster.Kind.SUCCESS);
        }
    }

    @Override
    public void newRecoveryKey() {
        if (requireManager("create recovery keys") && account().newRecoveryKey()) {
            toaster.show("New recovery key created — the old one no longer works", Toaster.Kind.SUCCESS);
        }
    }

    @Override
    public void manageStaff() {
        if (requireManager("manage staff")) {
            account().manageStaff();
        }
    }

    @Override
    public void about() {
        Ui.info(stage, "About", Ui.LODGE_NAME + "\nICT261 Lecture 3\n\nSigned in as " + user.username()
                + " (" + user.role() + ")\n\nBills are saved automatically to:\n" + book.file());
    }

    // ===================================================================== Helpers

    private AccountController account() {
        return new AccountController(stage, auth, user);
    }

    /** True if the user has started typing a bill that has not been saved. */
    private boolean hasUnsavedForm() {
        return editing != null || view.hasTypedInput();
    }

    /** Guards manager-only actions (the buttons are hidden too, but shortcuts and menus must not bypass it). */
    private boolean requireManager(String what) {
        if (user.isManager()) {
            return true;
        }
        toaster.show("Only a manager can " + what + ".", Toaster.Kind.ERROR);
        return false;
    }

    private Bill selectedOrWarn(String message) {
        Bill b = view.selectedBill();
        if (b == null) {
            toaster.show(message, Toaster.Kind.ERROR);
        }
        return b;
    }
}
