package lodge.model;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Keeps bills in a CSV file (opens in Excel) so they survive closing the app. */
public final class BillStore {

    private static final String HEADER =
            "id,guest,room,checkIn,nights,roomCharge,discount,extras,total,paid,notes,createdAt,createdBy\r\n";
    private static final int COLUMNS = 13;
    private static final int OLD_COLUMNS = 12; // files written before logins existed have no createdBy

    private final Path file;

    public BillStore(Path file) {
        this.file = file;
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".lodge-billing", "bills.csv");
    }

    public Path file() {
        return file;
    }

    public List<Bill> load() throws IOException {
        List<Bill> bills = new ArrayList<>();
        if (!Files.exists(file)) {
            return bills;
        }
        List<List<String>> rows = Csv.readRows(file);
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() != COLUMNS && r.size() != OLD_COLUMNS) {
                throw new IOException("bill " + (i + 1) + " has " + r.size() + " columns, expected " + COLUMNS);
            }
            try {
                bills.add(new Bill(r.get(0), r.get(1), RoomType.valueOf(r.get(2)), LocalDate.parse(r.get(3)),
                        Integer.parseInt(r.get(4)), new BigDecimal(r.get(5)), new BigDecimal(r.get(6)),
                        new BigDecimal(r.get(7)), new BigDecimal(r.get(8)), new BigDecimal(r.get(9)),
                        r.get(10), LocalDateTime.parse(r.get(11)), r.size() == COLUMNS ? r.get(12) : ""));
            } catch (RuntimeException e) {
                throw new IOException("bill " + (i + 1) + " is invalid: " + e.getMessage(), e);
            }
        }
        return bills;
    }

    public void save(List<Bill> bills) throws IOException {
        Csv.writeAtomically(file, toCsv(bills));
    }

    public void saveTo(List<Bill> bills, Path target) throws IOException {
        Csv.writeAtomically(target, toCsv(bills));
    }

    private static String toCsv(List<Bill> bills) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (Bill b : bills) {
            csv.append(Csv.row(b.id(), b.guest(), b.room().name(), b.checkIn().toString(),
                    String.valueOf(b.nights()), b.roomCharge().toPlainString(), b.discount().toPlainString(),
                    b.extras().toPlainString(), b.total().toPlainString(), b.paid().toPlainString(),
                    b.notes(), b.createdAt().toString(), b.createdBy()));
        }
        return csv.toString();
    }

    /** Moves an unreadable file aside so it is never overwritten. Returns the new path, or null on failure. */
    public Path quarantine() {
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file, backup);
            return backup;
        } catch (IOException e) {
            return null;
        }
    }

    public static int nextBillNumber(List<Bill> bills) {
        int max = 0;
        for (Bill b : bills) {
            if (b.id().matches("B\\d+")) {
                max = Math.max(max, Integer.parseInt(b.id().substring(1)));
            }
        }
        return max + 1;
    }
}
