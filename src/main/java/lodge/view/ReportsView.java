package lodge.view;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import lodge.model.Bill;
import lodge.model.Money;
import lodge.model.RoomType;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Charts and lists that update live as bills are saved, paid or deleted. */
final class ReportsView {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US);

    private final ObservableList<Bill> bills;
    private final Map<RoomType, XYChart.Data<String, Number>> billedByRoom = new EnumMap<>(RoomType.class);
    private final Map<RoomType, XYChart.Data<String, Number>> owingByRoom = new EnumMap<>(RoomType.class);
    private final XYChart.Series<String, Number> monthSeries = new XYChart.Series<>();
    private final ListView<Bill> owingList = new ListView<>();
    private final Label bookingsLabel = new Label();
    private final VBox root;

    ReportsView(ObservableList<Bill> bills) {
        this.bills = bills;

        CategoryAxis roomAxis = new CategoryAxis(FXCollections.observableArrayList(
                java.util.Arrays.stream(RoomType.values()).map(RoomType::toString).toList()));
        BarChart<String, Number> roomChart = new BarChart<>(roomAxis, kwachaAxis());
        roomChart.setTitle("Revenue by room type");
        XYChart.Series<String, Number> billed = new XYChart.Series<>();
        billed.setName("Billed");
        XYChart.Series<String, Number> owing = new XYChart.Series<>();
        owing.setName("Outstanding");
        for (RoomType room : RoomType.values()) {
            billedByRoom.put(room, new XYChart.Data<>(room.toString(), 0));
            owingByRoom.put(room, new XYChart.Data<>(room.toString(), 0));
            billed.getData().add(billedByRoom.get(room));
            owing.getData().add(owingByRoom.get(room));
        }
        roomChart.getData().add(billed);
        roomChart.getData().add(owing);
        roomChart.setCategoryGap(40);

        BarChart<String, Number> monthChart = new BarChart<>(new CategoryAxis(), kwachaAxis());
        monthChart.setTitle("Revenue by check-in month");
        monthChart.setAnimated(false); // changing categories while animating draws glitches
        monthChart.setLegendVisible(false);
        monthChart.setCategoryGap(60);
        monthChart.getData().add(monthSeries);

        Label owingTitle = new Label("Guests with an outstanding balance");
        owingTitle.getStyleClass().add("card-title");
        bookingsLabel.getStyleClass().add("hint");
        owingList.setPlaceholder(new Label("Every bill is settled ✓"));
        owingList.setCellFactory(list -> new OwingCell());

        VBox roomCard = card(roomChart);
        VBox monthCard = card(monthChart);
        HBox charts = new HBox(18, roomCard, monthCard);
        HBox.setHgrow(roomCard, Priority.ALWAYS);
        HBox.setHgrow(monthCard, Priority.ALWAYS);

        VBox owingCard = card(owingTitle, bookingsLabel, owingList);
        VBox.setVgrow(owingList, Priority.ALWAYS);

        root = new VBox(18, charts, owingCard);
        root.setPadding(new Insets(14, 0, 0, 0));
        VBox.setVgrow(owingCard, Priority.ALWAYS);

        bills.addListener((ListChangeListener<Bill>) change -> refresh());
        refresh();
    }

    Node node() {
        return root;
    }

    private void refresh() {
        Map<RoomType, BigDecimal> billed = new EnumMap<>(RoomType.class);
        Map<RoomType, BigDecimal> owing = new EnumMap<>(RoomType.class);
        Map<RoomType, Integer> count = new EnumMap<>(RoomType.class);
        TreeMap<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        for (Bill b : bills) {
            billed.merge(b.room(), b.total(), BigDecimal::add);
            if (!b.settled()) {
                owing.merge(b.room(), b.balance(), BigDecimal::add);
            }
            count.merge(b.room(), 1, Integer::sum);
            byMonth.merge(YearMonth.from(b.checkIn()), b.total(), BigDecimal::add);
        }
        for (RoomType room : RoomType.values()) {
            billedByRoom.get(room).setYValue(billed.getOrDefault(room, BigDecimal.ZERO).doubleValue());
            owingByRoom.get(room).setYValue(owing.getOrDefault(room, BigDecimal.ZERO).doubleValue());
        }
        monthSeries.getData().setAll(byMonth.entrySet().stream()
                .map(e -> new XYChart.Data<String, Number>(MONTH.format(e.getKey()), e.getValue().doubleValue()))
                .toList());

        bookingsLabel.setText("Bookings:  " + java.util.Arrays.stream(RoomType.values())
                .map(room -> room + " " + count.getOrDefault(room, 0))
                .collect(Collectors.joining("   ·   ")));

        List<Bill> owingBills = bills.stream()
                .filter(b -> !b.settled())
                .sorted(Comparator.comparing(Bill::balance).reversed())
                .toList();
        owingList.getItems().setAll(owingBills);
    }

    private static NumberAxis kwachaAxis() {
        NumberAxis axis = new NumberAxis();
        axis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(axis, "K ", null));
        axis.setMinorTickVisible(false);
        return axis;
    }

    private static VBox card(Node... children) {
        VBox card = new VBox(10, children);
        card.getStyleClass().add("card");
        return card;
    }

    private static final class OwingCell extends ListCell<Bill> {
        @Override
        protected void updateItem(Bill b, boolean empty) {
            super.updateItem(b, empty);
            setText(null);
            if (empty || b == null) {
                setGraphic(null);
                return;
            }
            Label who = new Label(b.id() + "  ·  " + b.guest());
            who.getStyleClass().add("owing-name");
            Label detail = new Label(b.room() + " room  ·  checked in " + DATE.format(b.checkIn())
                    + "  ·  paid " + Money.format(b.paid()) + " of " + Money.format(b.total()));
            detail.getStyleClass().add("owing-detail");
            Label amount = new Label(Money.format(b.balance()));
            amount.getStyleClass().add("owing-amount");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(10, new VBox(2, who, detail), spacer, amount);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
