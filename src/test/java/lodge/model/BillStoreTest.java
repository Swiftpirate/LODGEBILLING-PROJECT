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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillStoreTest {

    @TempDir
    Path dir;

    private static Bill bill(String id, String guest, String notes) {
        var b = BillCalculator.calculate(RoomType.DELUXE, 5, Money.of("80"));
        return new Bill(id, guest, RoomType.DELUXE, LocalDate.of(2026, 10, 11), 5, b.roomCharge(), b.discount(),
                b.extras(), b.total(), Money.of("1000"), notes, LocalDateTime.of(2026, 10, 11, 9, 30), "mary");
    }

    @Test
    void missingFileMeansNoBills() throws IOException {
        assertTrue(new BillStore(dir.resolve("none.csv")).load().isEmpty());
    }

    @Test
    void roundTripKeepsAwkwardText() throws IOException {
        BillStore store = new BillStore(dir.resolve("sub/bills.csv"));
        List<Bill> bills = List.of(
                bill("B001", "Mwansa, Chanda", "Asked for \"quiet\" room,\nlate check-out"),
                bill("B002", "Banda", ""));
        store.save(bills);
        assertEquals(bills, store.load());
    }

    @Test
    void readsFilesSavedBeforeLoginsExisted() throws IOException {
        Path file = dir.resolve("old.csv");
        Files.writeString(file, "id,guest,room,checkIn,nights,roomCharge,discount,extras,total,paid,notes,createdAt\n"
                + "B001,Banda,STANDARD,2026-09-28,2,700.00,0.00,0.00,700.00,300.00,,2026-09-28T14:30:00\n");
        List<Bill> bills = new BillStore(file).load();
        assertEquals(1, bills.size());
        assertEquals("", bills.get(0).createdBy());
        assertEquals(Money.of("400"), bills.get(0).balance());
    }

    @Test
    void nextBillNumberContinuesAfterTheHighest() {
        assertEquals(1, BillStore.nextBillNumber(List.of()));
        assertEquals(13, BillStore.nextBillNumber(List.of(bill("B004", "A", ""), bill("B012", "B", ""))));
    }

    @Test
    void brokenFileIsReportedAndCanBeMovedAside() throws IOException {
        Path file = dir.resolve("bills.csv");
        Files.writeString(file, "id,guest\nB001,only two columns\n");
        BillStore store = new BillStore(file);
        assertThrows(IOException.class, store::load);
        Path backup = store.quarantine();
        assertNotNull(backup);
        assertTrue(Files.exists(backup));
        assertTrue(Files.notExists(file));
    }
}
