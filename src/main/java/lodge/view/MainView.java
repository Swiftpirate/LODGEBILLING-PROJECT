package lodge.view;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import lodge.model.Bill;
import lodge.model.BillBook;
import lodge.model.BillCalculator;
import lodge.model.Money;
import lodge.model.RoomType;
import lodge.model.User;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The signed-in billing screen: layout, form, live bill, stats and the bills table. It shows the bills it is
 * given and hands every button, menu and shortcut to the Actions (the MainController). dispose() it on sign-out.
 */
public final class MainView {

    /** What the user asked for. */
    public interface Actions {
        void newBill();

        void saveBill();

        void editSelected();

        void recordPayment();

        void deleteBill();

        void viewReceipt();

        void printReceipt();

        void exportCsv();

        void exit();

        void signOut();

        void changePassword();

        void newRecoveryKey();

        void manageStaff();

        void about();

        /** Something in the bill form changed: the live bill needs recalculating. */
        void formChanged();
    }

    /** What is typed into the bill form (guest and notes trimmed; empty money fields are zero). */
    public record BillForm(String guest, RoomType room, LocalDate checkIn, int nights,
                           BigDecimal extras, BigDecimal paid, String notes) {
    }

    private static final DateTimeFormatter TABLE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEE d MMM  ·  HH:mm:ss", Locale.US);

    /** Bills whose table row should play the slide-in animation the next time it is drawn. */
    private final Set<Bill> freshBills = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Animation> intro = new ArrayList<>();

    // ----- Form -----
    private final TextField guestField = new TextField();
    private final ComboBox<RoomType> roomBox = new ComboBox<>();
    private final DatePicker checkInPicker = new DatePicker(LocalDate.now());
    private final Spinner<Integer> nightsSpinner = new Spinner<>(1, 365, 1);
    private final Label checkOutLabel = new Label();
    private final TextField extrasField = Ui.moneyField("0.00");
    private final TextField paidField = Ui.moneyField("0.00");
    private final TextArea notesArea = new TextArea();
    private final Label formTitle = new Label();
    private final Label formError = new Label();
    private final Button saveBtn = new Button();
    private final Button cancelEditBtn = new Button("Cancel edit");
    private VBox formCard;

    // ----- Live bill -----
    private final Effects.CountUpLabel roomChargeValue = Effects.moneyCounter();
    private final Effects.CountUpLabel discountValue =
            new Effects.CountUpLabel(v -> v > 0.004 ? "− " + Money.format(v) : Money.format(0));
    private final Effects.CountUpLabel extrasValue = Effects.moneyCounter();
    private final Effects.CountUpLabel totalValue = Effects.moneyCounter().bumpOnChange();
    private final Effects.CountUpLabel paidValue = Effects.moneyCounter();
    private final Effects.CountUpLabel balanceValue = Effects.moneyCounter().bumpOnChange();
    private final Label balanceCaption = new Label("Balance");

    // ----- Saved bills -----
    private final TextField searchField = new TextField();
    private final TableView<Bill> table = new TableView<>();
    private final Effects.CountUpLabel billCountStat = new Effects.CountUpLabel(v -> String.valueOf(Math.round(v)));
    private final Effects.CountUpLabel revenueStat = Effects.moneyCounter();
    private final Effects.CountUpLabel collectedStat = Effects.moneyCounter();
    private final Effects.CountUpLabel outstandingStat = Effects.moneyCounter();

    private final Stage stage;
    private final User user;
    private final ObservableList<Bill> bills;
    private final Actions actions;
    private final List<Node> pages = new ArrayList<>();
    private final ToggleGroup nav = new ToggleGroup();
    /** Endless animations owned by this view, stopped on sign-out. */
    private final List<Animation> forever = new ArrayList<>();
    /** Sits above the whole screen for ripples and confetti; never catches the mouse. */
    private final Pane fxLayer = new Pane();
    private final StackPane root;
    private Node tableCard;
    private int currentPage = -1;
    private Animation pageSlide;
    private boolean disposed;

    public MainView(Stage stage, User user, ObservableList<Bill> bills, Actions actions) {
        this.stage = stage;
        this.user = user;
        this.bills = bills;
        this.actions = actions;

        ReportsView reports = new ReportsView(bills);
        pages.add(buildBillingPage());
        pages.add(reports.node());
        StackPane pageStack = new StackPane();
        pageStack.getChildren().setAll(pages);

        VBox main = new VBox(4, buildHeader(), pageStack);
        VBox.setVgrow(pageStack, Priority.ALWAYS);
        main.setPadding(new Insets(12, 22, 16, 22));
        showPage(0);

        BorderPane shell = new BorderPane(main);
        shell.setTop(buildMenuBar());
        fxLayer.setMouseTransparent(true);
        root = new StackPane(shell, fxLayer);
    }

    public Node node() {
        return root;
    }

    /** Call once the view is on screen. */
    public void playIntro() {
        intro.forEach(Animation::play);
    }

    public void dispose() {
        disposed = true;
        forever.forEach(Animation::stop);
    }

    // ===================================================================== What the controller can read and show

    public BillForm form() {
        return new BillForm(guestField.getText().trim(), roomBox.getValue(), checkInPicker.getValue(),
                nightsSpinner.getValue(), Ui.parseOrZero(extrasField), Ui.parseOrZero(paidField),
                notesArea.getText().trim());
    }

    /** True if anything has been typed into the form. */
    public boolean hasTypedInput() {
        return !guestField.getText().isBlank() || !notesArea.getText().isBlank()
                || !extrasField.getText().isBlank() || !paidField.getText().isBlank();
    }

    /** Empties the form for a new bill. */
    public void showNewForm() {
        formTitle.setText("New bill");
        saveBtn.setText("Save bill");
        cancelEditBtn.setVisible(false);
        guestField.clear();
        roomBox.setValue(RoomType.STANDARD);
        checkInPicker.setValue(LocalDate.now());
        nightsSpinner.getValueFactory().setValue(1);
        extrasField.clear();
        paidField.clear();
        notesArea.clear();
        clearInvalid();
        guestField.requestFocus();
    }

    /** Loads a saved bill into the form for editing. */
    public void showEditForm(Bill b) {
        showPage(0);
        formTitle.setText("Editing " + b.id());
        saveBtn.setText("Update bill");
        cancelEditBtn.setVisible(true);
        guestField.setText(b.guest());
        roomBox.setValue(b.room());
        checkInPicker.setValue(b.checkIn());
        nightsSpinner.getValueFactory().setValue(b.nights());
        extrasField.setText(plain(b.extras()));
        paidField.setText(plain(b.paid()));
        notesArea.setText(b.notes());
        formError.setText("");
        Effects.pulse(formCard);
        guestField.requestFocus();
    }

    /** The guest name is the only field that can be wrong (the others refuse bad input as it is typed). */
    public void showGuestError(String message) {
        showPage(0);
        formError.setText(message);
        if (!guestField.getStyleClass().contains("invalid")) {
            guestField.getStyleClass().add("invalid");
        }
        Effects.shake(guestField);
        guestField.requestFocus();
    }

    public void showLiveBill(LocalDate checkOut, BillCalculator.Breakdown b, BigDecimal paid) {
        checkOutLabel.setText(LONG_DATE.format(checkOut));
        BigDecimal balance = b.total().subtract(paid);
        roomChargeValue.animateTo(b.roomCharge());
        discountValue.animateTo(b.discount());
        extrasValue.animateTo(b.extras());
        totalValue.animateTo(b.total());
        paidValue.animateTo(paid);

        balanceValue.getStyleClass().removeAll("balance-owing", "balance-settled", "balance-change");
        if (balance.signum() < 0) {
            balanceCaption.setText("Change due");
            balanceValue.animateTo(balance.negate());
            balanceValue.getStyleClass().add("balance-change");
        } else {
            balanceCaption.setText("Balance");
            balanceValue.animateTo(balance);
            balanceValue.getStyleClass().add(balance.signum() > 0 ? "balance-owing" : "balance-settled");
        }
    }

    public void showTotals(BillBook.Totals totals) {
        billCountStat.animateTo(totals.count());
        revenueStat.animateTo(totals.billed());
        collectedStat.animateTo(totals.collected());
        outstandingStat.animateTo(totals.outstanding());
    }

    public Bill selectedBill() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void select(Bill bill, boolean scrollTo) {
        table.getSelectionModel().select(bill);
        if (scrollTo) {
            table.scrollTo(bill);
        }
    }

    /** A bill has just been paid in full: confetti bursts up from the bills table. */
    public void celebrate() {
        Platform.runLater(() -> {
            Bounds box = fxLayer.sceneToLocal(tableCard.localToScene(tableCard.getBoundsInLocal()));
            Effects.confetti(fxLayer, box.getCenterX(), box.getMinY() + box.getHeight() * 0.35);
        });
    }

    /** The row for this bill flashes the next time it is drawn. */
    public void markFresh(Bill bill) {
        freshBills.add(bill);
    }

    /** Asks how much was received against a bill; empty if cancelled. */
    public Optional<BigDecimal> askPayment(Bill b) {
        TextField amountField = Ui.moneyField("Amount in K");
        amountField.setText(b.balance().toPlainString());
        Dialog<BigDecimal> dialog = Ui.styled(new Dialog<>(), stage);
        dialog.setTitle("Record payment");
        dialog.setHeaderText(b.id() + "  ·  " + b.guest() + "\nOutstanding: " + Money.format(b.balance()));
        dialog.getDialogPane().setContent(new VBox(8, formLabel("Amount received (K)"), amountField));
        ButtonType record = new ButtonType("Record payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(record, ButtonType.CANCEL);
        Button recordButton = (Button) dialog.getDialogPane().lookupButton(record);
        recordButton.getStyleClass().add("primary");
        recordButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> Ui.parseOrZero(amountField).signum() <= 0, amountField.textProperty()));
        dialog.setResultConverter(button -> button == record ? Ui.parseOrZero(amountField) : null);
        Platform.runLater(() -> {
            amountField.requestFocus();
            amountField.selectAll();
        });
        return dialog.showAndWait();
    }

    /** Asks where to export the bills; null if cancelled. */
    public File chooseExportFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export bills");
        chooser.setInitialFileName("lodge-bills-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (Excel)", "*.csv"));
        return chooser.showSaveDialog(stage);
    }

    // ===================================================================== Layout

    private Node buildHeader() {
        Label title = new Label(Ui.LODGE_NAME);
        title.getStyleClass().add("app-title");
        DropShadow glow = new DropShadow(12, Theme.current().glow(0.5));
        glow.colorProperty().bind(Bindings.createObjectBinding(
                () -> Theme.current().glow(0.5), Theme.currentProperty()));
        title.setEffect(glow);
        Timeline breathe = new Timeline(new KeyFrame(Duration.seconds(2.6),
                new KeyValue(glow.radiusProperty(), 30, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true);
        breathe.setCycleCount(Animation.INDEFINITE);
        breathe.play();
        forever.add(breathe);

        Label subtitle = new Label("ICT261 Lecture 3   ·   Mr E Nyirenda   ·   Mulungushi University");
        subtitle.getStyleClass().add("app-subtitle");

        Label clock = new Label();
        clock.getStyleClass().add("clock");
        Timeline tick = new Timeline(
                new KeyFrame(Duration.ZERO, e -> clock.setText(CLOCK.format(LocalDateTime.now()))),
                new KeyFrame(Duration.seconds(1)));
        tick.setCycleCount(Animation.INDEFINITE);
        tick.play();
        forever.add(tick);

        ToggleButton billingNav = navButton("Billing", 0);
        ToggleButton reportsNav = navButton("Reports", 1);
        // Keep one page selected: clicking the active pill again does nothing
        nav.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                nav.selectToggle(old);
            }
        });
        Node navBar = buildNavBar(billingNav, reportsNav);

        Label who = new Label(user.fullName());
        who.getStyleClass().add("user-name");
        Label role = new Label(user.role().toString().toUpperCase(Locale.ROOT));
        role.getStyleClass().addAll("role-badge", user.isManager() ? "role-manager" : "role-receptionist");
        Button signOutBtn = new Button("Sign out");
        signOutBtn.getStyleClass().add("sign-out");
        signOutBtn.setTooltip(new Tooltip("Sign out  (Ctrl+L)"));
        signOutBtn.setOnAction(e -> actions.signOut());
        Effects.hoverPop(signOutBtn);
        Effects.ripple(fxLayer, signOutBtn);
        HBox userChip = new HBox(12, new VBox(2, who, role), signOutBtn);
        userChip.getStyleClass().add("user-chip");
        userChip.setAlignment(Pos.CENTER_LEFT);

        HBox right = new HBox(12, clock, userChip);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(new VBox(2, title, subtitle), spacer(), navBar, spacer(), right);
        header.setAlignment(Pos.CENTER_LEFT);
        intro.add(Effects.entrance(header, 0));
        return header;
    }

    /** The nav buttons sit on a gold pill that glides to whichever page is selected. */
    private Node buildNavBar(ToggleButton... buttons) {
        HBox row = new HBox(6, buttons);
        row.setAlignment(Pos.CENTER);
        Region pill = new Region();
        pill.getStyleClass().add("nav-pill");
        pill.setManaged(false);
        StackPane bar = new StackPane(pill, row);
        bar.getStyleClass().add("nav-bar");
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        SimpleDoubleProperty x = new SimpleDoubleProperty();
        SimpleDoubleProperty width = new SimpleDoubleProperty();
        Timeline glide = new Timeline();
        Runnable place = () -> {
            ToggleButton on = (ToggleButton) nav.getSelectedToggle();
            if (on != null) {
                pill.resizeRelocate(x.get(), row.getLayoutY() + on.getLayoutY(), width.get(), on.getHeight());
            }
        };
        x.addListener(obs -> place.run());
        width.addListener(obs -> place.run());
        // Layout changes (first show, resizing) snap the pill into place; changing page makes it glide
        Runnable snap = () -> {
            ToggleButton on = (ToggleButton) nav.getSelectedToggle();
            if (on != null && glide.getStatus() != Animation.Status.RUNNING) {
                x.set(row.getLayoutX() + on.getLayoutX());
                width.set(on.getWidth());
                place.run();
            }
        };
        row.layoutXProperty().addListener(obs -> snap.run());
        row.layoutYProperty().addListener(obs -> snap.run());
        for (ToggleButton button : buttons) {
            button.layoutXProperty().addListener(obs -> snap.run());
            button.widthProperty().addListener(obs -> snap.run());
            button.heightProperty().addListener(obs -> snap.run());
        }
        nav.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now instanceof ToggleButton on && on.getWidth() > 0) {
                Effects.slidePill(glide, x, width, row.getLayoutX() + on.getLayoutX(), on.getWidth());
            }
        });
        return bar;
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File", null,
                menuItem("New guest", "Shortcut+N", actions::newBill),
                menuItem("Save bill", "Shortcut+S", actions::saveBill),
                new SeparatorMenuItem(),
                menuItem("Print receipt…", "Shortcut+P", actions::printReceipt),
                menuItem("Export bills to CSV…", null, actions::exportCsv),
                new SeparatorMenuItem(),
                menuItem("Exit", null, actions::exit));
        Menu bill = new Menu("Bill", null,
                menuItem("View receipt", "Shortcut+R", actions::viewReceipt),
                menuItem("Record payment…", "Shortcut+M", actions::recordPayment));
        if (user.isManager()) {
            bill.getItems().addAll(
                    menuItem("Edit selected bill", "Shortcut+E", actions::editSelected),
                    new SeparatorMenuItem(),
                    menuItem("Delete selected bill", null, actions::deleteBill));
        }
        Menu account = new Menu("Account", null,
                menuItem("Change my password…", null, actions::changePassword));
        if (user.isManager()) {
            account.getItems().addAll(
                    menuItem("New recovery key…", null, actions::newRecoveryKey),
                    menuItem("Manage staff…", null, actions::manageStaff));
        }
        account.getItems().addAll(new SeparatorMenuItem(), menuItem("Sign out", "Shortcut+L", actions::signOut));
        Menu view = new Menu("View", null,
                menuItem("Billing", "Shortcut+1", () -> showPage(0)),
                menuItem("Reports", "Shortcut+2", () -> showPage(1)),
                menuItem("Search bills", "Shortcut+F", () -> {
                    showPage(0);
                    searchField.requestFocus();
                }));
        view.getItems().addAll(new SeparatorMenuItem(), themeMenu());
        Menu help = new Menu("Help", null,
                menuItem("Keyboard shortcuts", "F1", this::showShortcuts),
                menuItem("About", null, actions::about));
        return new MenuBar(file, bill, account, view, help);
    }

    private Node buildBillingPage() {
        formCard = buildFormCard();
        Node summaryCard = buildSummaryCard();
        HBox top = new HBox(18, formCard, summaryCard);
        HBox.setHgrow(formCard, Priority.ALWAYS);

        Node stats = buildStatsBar();
        tableCard = buildTableCard();

        VBox page = new VBox(12, top, stats, tableCard);
        VBox.setVgrow(tableCard, Priority.ALWAYS);
        page.setPadding(new Insets(8, 4, 4, 0));

        intro.add(Effects.entrance(formCard, 120));
        intro.add(Effects.entrance(summaryCard, 240));
        intro.add(Effects.entrance(stats, 360));
        intro.add(Effects.entrance(tableCard, 480));

        ScrollPane scroller = new ScrollPane(page);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        scroller.getStyleClass().add("page-scroller");
        return scroller;
    }

    private VBox buildFormCard() {
        formTitle.getStyleClass().add("card-title");
        guestField.setPromptText("Full name of the guest");
        roomBox.getItems().setAll(RoomType.values());
        roomBox.setMaxWidth(Double.MAX_VALUE);
        roomBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RoomType room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? null : room + "   ·   " + Money.format(room.rate()) + " / night");
            }
        });
        checkInPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : TABLE_DATE.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                return text == null || text.isBlank() ? null : LocalDate.parse(text, TABLE_DATE);
            }
        });
        checkInPicker.setEditable(false);
        checkInPicker.setMaxWidth(Double.MAX_VALUE);
        configureNightsSpinner();
        checkOutLabel.getStyleClass().add("checkout-value");
        notesArea.setPromptText("Special requests, what the extras were for…");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);
        formError.getStyleClass().add("form-error");
        formError.visibleProperty().bind(formError.textProperty().isNotEmpty());
        formError.managedProperty().bind(formError.visibleProperty());

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(9);
        grid.getColumnConstraints().addAll(labelColumn(), fieldColumn(), labelColumn(), fieldColumn());
        grid.add(formLabel("Guest name"), 0, 0);
        grid.add(guestField, 1, 0, 3, 1);
        grid.addRow(1, formLabel("Room type"), roomBox, formLabel("Check-in"), checkInPicker);
        grid.addRow(2, formLabel("Nights"), nightsSpinner, formLabel("Check-out"), checkOutLabel);
        grid.addRow(3, formLabel("Extras (K)"), extrasField, formLabel("Amount paid (K)"), paidField);
        grid.add(formLabel("Notes"), 0, 4);
        grid.add(notesArea, 1, 4, 3, 1);

        saveBtn.getStyleClass().add("primary");
        saveBtn.setOnAction(e -> actions.saveBill());
        saveBtn.setTooltip(new Tooltip("Save this bill  (Ctrl+S)"));
        Button newBtn = new Button("New guest");
        newBtn.setOnAction(e -> actions.newBill());
        newBtn.setTooltip(new Tooltip("Clear the form  (Ctrl+N)"));
        cancelEditBtn.setOnAction(e -> actions.newBill());
        cancelEditBtn.managedProperty().bind(cancelEditBtn.visibleProperty());
        Effects.hoverPop(saveBtn, cancelEditBtn, newBtn);
        Effects.ripple(fxLayer, saveBtn, cancelEditBtn, newBtn);
        HBox buttons = new HBox(10, saveBtn, cancelEditBtn, newBtn);

        guestField.textProperty().addListener((obs, old, text) -> clearInvalid());
        roomBox.valueProperty().addListener(obs -> actions.formChanged());
        checkInPicker.valueProperty().addListener(obs -> actions.formChanged());
        nightsSpinner.valueProperty().addListener(obs -> actions.formChanged());
        extrasField.textProperty().addListener(obs -> actions.formChanged());
        paidField.textProperty().addListener(obs -> actions.formChanged());

        VBox card = new VBox(12, formTitle, grid, formError, buttons);
        card.getStyleClass().add("card");
        return card;
    }

    /** Nights: arrows or typing; only 1–365 is accepted. */
    private void configureNightsSpinner() {
        nightsSpinner.setEditable(true);
        nightsSpinner.setMaxWidth(Double.MAX_VALUE);
        TextField editor = nightsSpinner.getEditor();
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,3}") ? change : null));
        editor.textProperty().addListener((obs, old, text) -> {
            if (!text.isEmpty()) {
                int nights = Integer.parseInt(text);
                if (nights >= 1 && nights <= 365) {
                    nightsSpinner.getValueFactory().setValue(nights);
                }
            }
        });
        editor.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                editor.setText(String.valueOf(nightsSpinner.getValue()));
            }
        });
    }

    private Node buildSummaryCard() {
        Label caption = caption("LIVE BILL");

        GridPane rows = new GridPane();
        rows.setHgap(20);
        rows.setVgap(6);
        ColumnConstraints left = new ColumnConstraints();
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setHalignment(HPos.RIGHT);
        rows.getColumnConstraints().addAll(left, right);

        for (Label value : List.of(roomChargeValue, discountValue, extrasValue, paidValue)) {
            value.getStyleClass().add("summary-value");
        }
        Label totalCaption = summaryLabel("Total");
        totalCaption.getStyleClass().add("summary-total-caption");
        totalValue.getStyleClass().add("summary-total");
        balanceCaption.getStyleClass().add("summary-label");
        balanceValue.getStyleClass().add("summary-balance");

        rows.addRow(0, summaryLabel("Room charge"), roomChargeValue);
        rows.addRow(1, summaryLabel("Long-stay discount"), discountValue);
        rows.addRow(2, summaryLabel("Extras"), extrasValue);
        rows.add(new Separator(), 0, 3, 2, 1);
        rows.addRow(4, totalCaption, totalValue);
        rows.addRow(5, summaryLabel("Paid"), paidValue);
        rows.addRow(6, balanceCaption, balanceValue);

        String percent = BillCalculator.DISCOUNT_RATE.movePointRight(2).stripTrailingZeros().toPlainString();
        Label rule = new Label(BillCalculator.DISCOUNT_MIN_NIGHTS + "+ nights: " + percent
                + "% off the room charge. Extras are not discounted. No tax.");
        rule.setWrapText(true);
        rule.setMinHeight(Region.USE_PREF_SIZE);
        rule.getStyleClass().add("hint");

        VBox card = new VBox(10, caption, rows, new Separator(), rule);
        card.getStyleClass().addAll("card", "summary-card");
        card.setPrefWidth(340);
        card.setMinWidth(310);
        return card;
    }

    private Node buildStatsBar() {
        HBox bar = new HBox(12,
                statTile("BILLS", billCountStat, "stat-plain"),
                statTile("TOTAL BILLED", revenueStat, "stat-gold"),
                statTile("COLLECTED", collectedStat, "stat-emerald"),
                statTile("OUTSTANDING", outstandingStat, "stat-rose"));
        bar.getChildren().forEach(tile -> HBox.setHgrow(tile, Priority.ALWAYS));
        return bar;
    }

    private Node statTile(String caption, Label value, String accent) {
        Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");
        VBox tile = new VBox(4, label, value);
        tile.getStyleClass().addAll("stat-tile", accent);
        tile.setMaxWidth(Double.MAX_VALUE);
        Effects.hoverLift(tile);
        return tile;
    }

    private Node buildTableCard() {
        Label title = new Label("Saved bills");
        title.getStyleClass().add("card-title");
        searchField.setPromptText("Search guest, bill no., room or notes   (Ctrl+F)");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("search-field");

        Button receiptBtn = toolButton("Receipt", "View receipt  (Enter or double-click)", actions::viewReceipt);
        Button editBtn = toolButton("Edit", "Load into the form  (Ctrl+E)", actions::editSelected);
        Button payBtn = toolButton("Record payment", "Add a payment  (Ctrl+M)", actions::recordPayment);
        Button deleteBtn = toolButton("Delete", "Delete bill  (Delete key)", actions::deleteBill);
        deleteBtn.getStyleClass().add("danger");
        Effects.ripple(fxLayer, receiptBtn, editBtn, payBtn, deleteBtn);

        ReadOnlyObjectProperty<Bill> selected = table.getSelectionModel().selectedItemProperty();
        BooleanBinding nothingSelected = selected.isNull();
        receiptBtn.disableProperty().bind(nothingSelected);
        editBtn.disableProperty().bind(nothingSelected);
        deleteBtn.disableProperty().bind(nothingSelected);
        payBtn.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().settled(), selected));

        HBox header = new HBox(10, title, spacer(), searchField, receiptBtn, payBtn);
        if (user.isManager()) {
            header.getChildren().addAll(editBtn, deleteBtn);
        }
        header.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox card = new VBox(12, header, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        card.getStyleClass().add("card");
        card.setMinHeight(200);
        return card;
    }

    private void configureTable() {
        FilteredList<Bill> filtered = new FilteredList<>(bills);
        searchField.textProperty().addListener((obs, old, text) -> {
            String query = text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(query.isEmpty() ? null : b -> BillBook.matches(b, query));
        });
        SortedList<Bill> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        TableColumn<Bill, BigDecimal> balanceCol = column("Balance", Bill::balance, Money::format, 130, "numeric");
        balanceCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal balance, boolean empty) {
                super.updateItem(balance, empty);
                getStyleClass().removeAll("owing", "settled");
                if (empty || balance == null) {
                    setText(null);
                    return;
                }
                boolean owing = balance.signum() > 0;
                setText(owing ? Money.format(balance) : "✓ Settled");
                getStyleClass().add(owing ? "owing" : "settled");
            }
        });
        // Number columns hold numbers (not text), so sorting K 1,050 vs K 350 is correct
        List<TableColumn<Bill, ?>> columns = List.of(
                column("Bill", Bill::id, id -> id, 70),
                column("Guest", Bill::guest, guest -> guest, 190),
                column("Room", Bill::room, RoomType::toString, 95),
                column("Check-in", Bill::checkIn, TABLE_DATE::format, 115),
                column("Nights", Bill::nights, n -> Integer.toString(n), 70, "numeric"),
                column("Total", Bill::total, Money::format, 120, "numeric"),
                column("Paid", Bill::paid, Money::format, 120, "numeric"),
                balanceCol,
                column("By", Bill::createdBy, by -> by.isEmpty() ? "—" : by, 90));
        table.getColumns().setAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Label placeholder = new Label();
        placeholder.textProperty().bind(Bindings.when(searchField.textProperty().isEmpty())
                .then("No bills yet — fill in the form above and press Save bill.")
                .otherwise("No bills match your search."));
        table.setPlaceholder(placeholder);

        ContextMenu rowMenu = new ContextMenu(
                menuItem("View receipt", null, actions::viewReceipt),
                menuItem("Record payment…", null, actions::recordPayment));
        if (user.isManager()) {
            rowMenu.getItems().addAll(
                    menuItem("Edit", null, actions::editSelected),
                    new SeparatorMenuItem(),
                    menuItem("Delete", null, actions::deleteBill));
        }
        table.setRowFactory(tv -> {
            TableRow<Bill> row = new TableRow<>() {
                @Override
                protected void updateItem(Bill bill, boolean empty) {
                    super.updateItem(bill, empty);
                    if (!empty && bill != null && freshBills.remove(bill)) {
                        Effects.rowFlash(this);
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    actions.viewReceipt();
                }
            });
            row.contextMenuProperty().bind(Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null).otherwise(rowMenu));
            return row;
        });
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                actions.deleteBill();
            } else if (e.getCode() == KeyCode.ENTER) {
                actions.viewReceipt();
            }
        });
    }

    // ===================================================================== Navigation

    public void showPage(int index) {
        nav.selectToggle(nav.getToggles().get(index));
        if (index == currentPage) {
            return;
        }
        if (pageSlide != null) {
            pageSlide.stop();
        }
        // Anything left half-way by an interrupted slide is put straight
        for (int i = 0; i < pages.size(); i++) {
            if (i != index && i != currentPage) {
                Node page = pages.get(i);
                page.setVisible(false);
                page.setOpacity(1);
                page.setTranslateX(0);
            }
        }
        if (currentPage < 0) {
            pages.get(index).setVisible(true);
        } else {
            pageSlide = Effects.slidePages(pages.get(currentPage), pages.get(index), index > currentPage ? 1 : -1);
        }
        currentPage = index;
    }

    /** View > Theme: switches colours and background straight away; remembered for next time. */
    private Menu themeMenu() {
        Menu menu = new Menu("Theme");
        ToggleGroup choice = new ToggleGroup();
        for (Theme theme : Theme.values()) {
            RadioMenuItem item = new RadioMenuItem(theme.displayName());
            item.setToggleGroup(choice);
            item.setSelected(theme == Theme.current());
            item.setOnAction(e -> {
                if (!disposed && theme != Theme.current()) {
                    Theme.use(theme);
                }
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private void showShortcuts() {
        Ui.info(stage, "Keyboard shortcuts", """
                Ctrl+N      New guest (clear the form)
                Ctrl+S      Save / update bill
                Ctrl+E      Edit selected bill
                Ctrl+M      Record a payment
                Ctrl+R      View receipt   (or Enter / double-click)
                Ctrl+P      Print receipt
                Delete      Delete selected bill
                Ctrl+F      Search bills
                Ctrl+1 / 2  Billing / Reports
                Ctrl+L      Sign out

                Edit and Delete are for managers only.""");
    }

    // ===================================================================== Helpers

    private void clearInvalid() {
        guestField.getStyleClass().remove("invalid");
        formError.setText("");
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "" : value.toPlainString();
    }

    private <T> TableColumn<Bill, T> column(String title, Function<Bill, T> getter,
                                            Function<T, String> format, double width, String... styleClasses) {
        TableColumn<Bill, T> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(getter.apply(d.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
        column.setPrefWidth(width);
        column.getStyleClass().addAll(styleClasses);
        return column;
    }

    private MenuItem menuItem(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(e -> {
            if (!disposed) {
                action.run();
            }
        });
        return item;
    }

    private ToggleButton navButton(String text, int page) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(nav);
        button.setOnAction(e -> showPage(page));
        Effects.hoverPop(button);
        return button;
    }

    private static Button toolButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        Effects.hoverPop(button);
        return button;
    }

    private static Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label summaryLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("summary-label");
        return label;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-caption");
        return label;
    }

    private static ColumnConstraints labelColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(Region.USE_PREF_SIZE);
        return column;
    }

    private static ColumnConstraints fieldColumn() {
        ColumnConstraints column = new ColumnConstraints(120, 200, Double.MAX_VALUE);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
