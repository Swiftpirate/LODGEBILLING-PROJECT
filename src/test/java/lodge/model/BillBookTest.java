package lodge.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillBookTest {

    @TempDir
    Path dir;

    private static Bill bill(String id, String guest, String paid) {
        var b = BillCalculator.calculate(RoomType.STANDARD, 2, Money.ZERO);
        return new Bill(id, guest, RoomType.STANDARD, LocalDate.of(2026, 10, 11), 2, b.roomCharge(), b.discount(),
                b.extras(), b.total(), Money.of(paid), "", LocalDateTime.of(2026, 10, 11, 9, 30), "mary");
    }

    @Test
    void everyChangeIsSavedAndNumberingContinues() throws IOException {
        Path file = dir.resolve("bills.csv");
        BillBook book = new BillBook(new BillStore(file));
        assertNull(book.load());
        assertEquals("B001", book.nextId());

        Bill first = bill(book.nextId(), "Banda", "0");
        book.add(first);
        assertEquals("B002", book.nextId());
        Bill paid = first.withPayment(Money.of("200"));
        assertTrue(book.replace(first, paid));
        assertFalse(book.replace(first, paid)); // first is no longer in the book

        assertEquals(List.of(paid), new BillStore(file).load());
    }

    @Test
    void totalsCountOnlyUnsettledBalancesAsOutstanding() {
        BillBook book = new BillBook(new BillStore(dir.resolve("bills.csv")));
        book.load();
        book.add(bill("B001", "Banda", "700"));  // settled
        book.add(bill("B002", "Phiri", "200"));  // owes 500
        BillBook.Totals totals = book.totals();
        assertEquals(2, totals.count());
        assertEquals(0, Money.of("1400").compareTo(totals.billed()));
        assertEquals(0, Money.of("900").compareTo(totals.collected()));
        assertEquals(0, Money.of("500").compareTo(totals.outstanding()));
    }

    @Test
    void duplicatesAndSearchIgnoreCase() {
        BillBook book = new BillBook(new BillStore(dir.resolve("bills.csv")));
        book.load();
        Bill b = bill("B001", "Mwansa Chanda", "0");
        book.add(b);
        assertTrue(book.isDuplicate("mwansa chanda", RoomType.STANDARD, LocalDate.of(2026, 10, 11)));
        assertFalse(book.isDuplicate("mwansa chanda", RoomType.DELUXE, LocalDate.of(2026, 10, 11)));
        assertTrue(BillBook.matches(b, "chanda"));
        assertTrue(BillBook.matches(b, "b001"));
        assertFalse(BillBook.matches(b, "phiri"));
    }

    @Test
    void unreadableFileIsMovedAsideAndReported() throws IOException {
        Path file = dir.resolve("bills.csv");
        Files.writeString(file, "id,guest\nB001,only two columns\n");
        BillBook book = new BillBook(new BillStore(file));
        String problem = book.load();
        assertNotNull(problem);
        assertTrue(problem.contains("moved aside"));
        assertTrue(book.bills().isEmpty());
        assertTrue(Files.notExists(file));
    }
}
