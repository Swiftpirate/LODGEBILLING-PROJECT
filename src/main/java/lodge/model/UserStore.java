package lodge.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Staff accounts in users.csv, next to the bills file. */
public final class UserStore {

    private static final String HEADER = "username,fullName,role,iterations,salt,passwordHash,createdAt,"
            + "mustChangePassword,recoveryIterations,recoverySalt,recoveryHash\r\n";
    private static final int COLUMNS = 11;
    private static final int OLD_COLUMNS = 7; // files written before recovery keys existed

    private final Path file;

    public UserStore(Path file) {
        this.file = file;
    }

    public static Path defaultFile() {
        return BillStore.defaultFile().resolveSibling("users.csv");
    }

    public Path file() {
        return file;
    }

    public List<User> load() throws IOException {
        List<User> users = new ArrayList<>();
        if (!Files.exists(file)) {
            return users;
        }
        List<List<String>> rows = Csv.readRows(file);
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() != COLUMNS && r.size() != OLD_COLUMNS) {
                throw new IOException("account " + (i + 1) + " has " + r.size() + " columns, expected " + COLUMNS);
            }
            boolean full = r.size() == COLUMNS;
            try {
                users.add(new User(r.get(0), r.get(1), Role.valueOf(r.get(2)), Integer.parseInt(r.get(3)),
                        r.get(4), r.get(5), LocalDateTime.parse(r.get(6)),
                        full && Boolean.parseBoolean(r.get(7)),
                        full ? Integer.parseInt(r.get(8)) : 0,
                        full ? r.get(9) : "",
                        full ? r.get(10) : ""));
            } catch (RuntimeException e) {
                throw new IOException("account " + (i + 1) + " is invalid: " + e.getMessage(), e);
            }
        }
        return users;
    }

    public void save(List<User> users) throws IOException {
        StringBuilder csv = new StringBuilder(HEADER);
        for (User u : users) {
            csv.append(Csv.row(u.username(), u.fullName(), u.role().name(), String.valueOf(u.iterations()),
                    u.salt(), u.passwordHash(), u.createdAt().toString(), String.valueOf(u.mustChangePassword()),
                    String.valueOf(u.recoveryIterations()), u.recoverySalt(), u.recoveryHash()));
        }
        Csv.writeAtomically(file, csv);
    }
}
