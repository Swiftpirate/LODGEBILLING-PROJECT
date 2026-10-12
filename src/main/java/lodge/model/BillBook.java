package lodge.model;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * All saved bills, kept in step with the bills file: every change to the list is written straight away.
 * Views observe bills(); only the controller changes it.
 */
public final class BillBook {

    /** Running totals over every bill, for the stats bar. */
    public record Totals(int count, BigDecimal billed, BigDecimal collected, BigDecimal outstanding) {
    }

    private final ObservableList<Bill> bills = FXCollections.observableArrayList();
    private final BillStore store;
    private int nextBill = 1;
    private boolean readOnly;
    private Consumer<IOException> onSaveFailed = e -> { };

    public BillBook(BillStore store) {
        this.store = store;
    }

    /**
     * Reads the bills file. Returns null if all went well, or a message explaining what went wrong: an unreadable
     * file is moved aside untouched, or, if that fails too, nothing is saved this session so it is never overwritten.
     */
    public String load() {
        String problem = null;
        try {
            bills.setAll(store.load());
        } catch (IOException | RuntimeException e) {
            Path backup = store.quarantine();
            if (backup != null) {
                problem = "The saved bills could not be read (" + e.getMessage() + ").\n\n"
                        + "The file was moved aside, untouched, to:\n" + backup
                        + "\n\nThe app will start with an empty list.";
            } else {
                readOnly = true;
                problem = "The saved bills could not be read (" + e.getMessage() + ").\n\n"
                        + "To protect the file, nothing will be saved this session:\n" + store.file();
            }
        }
        nextBill = BillStore.nextBillNumber(bills);
        bills.addListener((ListChangeListener<Bill>) change -> persist());
        return problem;
    }

    public void setOnSaveFailed(Consumer<IOException> handler) {
        onSaveFailed = handler;
    }

    public ObservableList<Bill> bills() {
        return bills;
    }

    public Path file() {
        return store.file();
    }

    /** The number the next new bill will get, e.g. B007. */
    public String nextId() {
        return String.format("B%03d", nextBill);
    }

    public void add(Bill bill) {
        nextBill++;
        bills.add(bill);
    }

    /** Replaces old with updated in place; returns false if old is no longer in the book. */
    public boolean replace(Bill old, Bill updated) {
        int index = bills.indexOf(old);
        if (index < 0) {
            return false;
        }
        bills.set(index, updated);
        return true;
    }

    public void remove(Bill bill) {
        bills.remove(bill);
    }

    public void exportTo(Path target) throws IOException {
        store.saveTo(bills, target);
    }

    public boolean isDuplicate(String guest, RoomType room, LocalDate checkIn) {
        return bills.stream().anyMatch(b ->
                b.guest().equalsIgnoreCase(guest) && b.room() == room && b.checkIn().equals(checkIn));
    }

    /** Search: query must already be trimmed and lower case. */
    public static boolean matches(Bill b, String query) {
        return b.id().toLowerCase(Locale.ROOT).contains(query)
                || b.guest().toLowerCase(Locale.ROOT).contains(query)
                || b.room().toString().toLowerCase(Locale.ROOT).contains(query)
                || b.notes().toLowerCase(Locale.ROOT).contains(query);
    }

    public Totals totals() {
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        for (Bill b : bills) {
            billed = billed.add(b.total());
            collected = collected.add(b.paid());
            if (!b.settled()) {
                outstanding = outstanding.add(b.balance());
            }
        }
        return new Totals(bills.size(), billed, collected, outstanding);
    }

    private void persist() {
        if (readOnly) {
            return;
        }
        try {
            store.save(bills);
        } catch (IOException e) {
            onSaveFailed.accept(e);
        }
    }
}
